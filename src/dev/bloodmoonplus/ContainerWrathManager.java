package dev.bloodmoonplus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

public final class ContainerWrathManager implements Listener {
   private final BloodMoonPlus plugin;

   private boolean enabled = true;
   private final Set<Material> blocked = EnumSet.noneOf(Material.class);
   private boolean blockMinecarts = true;
   private int warningsBeforePunish = 1;
   private int cooldownSeconds = 180;
   private String bossId = "wither_sombra";
   private boolean bossEnabled = true;
   private boolean explosionEnabled = true;
   private double explosionDamage = 6.0;
   private boolean exemptCreative = true;
   private String bypassPermission = "bloodmoon.bypass.containerwrath";
   private String warnMessage;
   private String punishMessage;

   private final Map<UUID, Attempts> attempts = new ConcurrentHashMap<>();

   private static final class Attempts {
      long sessionStart;
      int count;
      long lastPunishMs;
      long lastMessageMs;
   }

   public ContainerWrathManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("container-wrath.enabled", true);
      this.blocked.clear();
      if (cfg.isList("container-wrath.blocks")) {
         for (String name : cfg.getStringList("container-wrath.blocks")) {
            Material m = Material.matchMaterial(name.trim());
            if (m != null) {
               this.blocked.add(m);
            } else {
               this.plugin.getLogger().warning("container-wrath.blocks: unknown material " + name);
            }
         }
      } else {
         for (Material m : Material.values()) {
            if (!m.isLegacy() && (m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
               || m == Material.ENDER_CHEST || m.name().endsWith("SHULKER_BOX"))) {
               this.blocked.add(m);
            }
         }
      }
      this.blockMinecarts = cfg.getBoolean("container-wrath.block-minecarts", true);
      this.warningsBeforePunish = Math.max(0, cfg.getInt("container-wrath.warnings-before-punish", 1));
      this.cooldownSeconds = Math.max(0, cfg.getInt("container-wrath.punish-cooldown-seconds", 180));
      this.bossId = cfg.getString("container-wrath.boss", "wither_sombra");
      this.bossEnabled = cfg.getBoolean("container-wrath.boss-enabled", true);
      this.explosionEnabled = cfg.getBoolean("container-wrath.explosion.enabled", true);
      this.explosionDamage = Math.max(0.0, cfg.getDouble("container-wrath.explosion.damage", 6.0));
      this.exemptCreative = cfg.getBoolean("container-wrath.exempt-creative", true);
      this.bypassPermission = cfg.getString("container-wrath.bypass-permission", "bloodmoon.bypass.containerwrath");
      this.warnMessage = cfg.getString(
         "container-wrath.warn-message",
         "<dark_red><bold>⚠ Do not open chests during the Blood Moon!</bold></dark_red> <red>Storage is sealed and the seal explodes when touched. Try again and you wake what sleeps inside.</red>"
      );
      this.punishMessage = cfg.getString(
         "container-wrath.punish-message",
         "<dark_red><bold>☠ You disturbed a sealed container!</bold></dark_red> <red>A guardian of the Blood Moon breaks free.</red>"
      );
      this.attempts.clear();
   }

   private boolean isExempt(Player p) {
      if (p.isOp()) return true;
      if (this.exemptCreative && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
         return true;
      }
      return this.bypassPermission != null && !this.bypassPermission.isEmpty() && p.hasPermission(this.bypassPermission);
   }

   private EventManager.Session activeSession(Player p) {
      if (!this.enabled || this.plugin.events() == null || this.isExempt(p)) {
         return null;
      }
      return this.plugin.events().session(p.getWorld());
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent e) {
      if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
         return;
      }
      Block b = e.getClickedBlock();
      if (b == null || !this.blocked.contains(b.getType()) || e.useInteractedBlock() == Event.Result.DENY) {
         return;
      }
      Player p = e.getPlayer();
      EventManager.Session s = this.activeSession(p);
      if (s == null) {
         return;
      }
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
      this.handle(p, s, b.getLocation().add(0.5, 1.0, 0.5));
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteractEntity(PlayerInteractEntityEvent e) {
      if (!this.blockMinecarts || e.getHand() != EquipmentSlot.HAND) {
         return;
      }
      Entity t = e.getRightClicked();
      if (!(t instanceof StorageMinecart) && !(t instanceof HopperMinecart)) {
         return;
      }
      Player p = e.getPlayer();
      EventManager.Session s = this.activeSession(p);
      if (s == null) {
         return;
      }
      e.setCancelled(true);
      this.handle(p, s, t.getLocation().add(0.0, 1.0, 0.0));
   }

   private void handle(Player p, EventManager.Session s, Location at) {
      long now = System.currentTimeMillis();
      Attempts a = this.attempts.computeIfAbsent(p.getUniqueId(), k -> new Attempts());
      if (a.sessionStart != s.startMs) {
         a.sessionStart = s.startMs;
         a.count = 0;
         a.lastPunishMs = 0L;
      }
      if (now - a.lastMessageMs < 1000L) {
         return;
      }
      a.lastMessageMs = now;
      a.count++;

      boolean punish = this.bossEnabled
         && a.count > this.warningsBeforePunish
         && now - a.lastPunishMs >= (long)this.cooldownSeconds * 1000L
         && this.bossId != null && !this.bossId.isEmpty()
         && this.plugin.bosses() != null;

      if (punish) {
         World w = at.getWorld();
         Location spawn = this.plugin.events().findSpawn(at, 3.0, 8.0);
         if (spawn == null) {
            spawn = at;
         }
         w.strikeLightningEffect(at);
         w.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 40, 0.5, 0.5, 0.5, 0.05);
         if (this.plugin.bosses().spawnAt(spawn, this.bossId, s.difficulty, p)) {
            a.lastPunishMs = now;
            if (this.punishMessage != null && !this.punishMessage.isEmpty()) {
               p.sendMessage(Util.mini(this.punishMessage.replace("{player}", p.getName())));
            }
            p.playSound(at, Sound.ENTITY_WITHER_SPAWN, 1.0F, 0.8F);
            return;
         }
         this.plugin.getLogger().warning("container-wrath: boss " + this.bossId + " failed to spawn for " + p.getName());
      }

      if (this.explosionEnabled) {
         World w = at.getWorld();
         w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
         w.spawnParticle(Particle.LARGE_SMOKE, at, 20, 0.4, 0.4, 0.4, 0.02);
         w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.9F);
         if (this.explosionDamage > 0.0) {
            p.damage(this.explosionDamage);
         }
         Vector push = p.getLocation().toVector().subtract(at.toVector()).setY(0);
         if (push.lengthSquared() < 0.01) {
            push = p.getLocation().getDirection().multiply(-1).setY(0);
         }
         if (push.lengthSquared() > 0.0001) {
            p.setVelocity(push.normalize().multiply(0.6).setY(0.4));
         }
      }
      if (this.warnMessage != null && !this.warnMessage.isEmpty()) {
         p.sendMessage(Util.mini(this.warnMessage.replace("{player}", p.getName())));
      }
      p.playSound(at, Sound.BLOCK_CHEST_LOCKED, 1.0F, 0.6F);
   }
}
