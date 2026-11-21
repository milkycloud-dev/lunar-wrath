package dev.bloodmoonplus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class DimensionWrathManager implements Listener {
   private final BloodMoonPlus plugin;
   private BukkitTask task;

   private boolean enabled = true;
   private boolean applyToNether = true;
   private boolean applyToEnd = true;
   private int strikeIntervalSeconds = 2;
   private double damageAmount = 4.0;
   private int broadcastIntervalSeconds = 30;
   private boolean exemptCreative = true;
   private String bypassPermission = "bloodmoon.bypass.dimensionwrath";
   private boolean broadcastChat = false;

   private String netherBroadcast;
   private String netherActionbar;
   private String netherDeath;

   private String endBroadcast;
   private String endActionbar;
   private String endDeath;

   private final Map<UUID, Long> lastStrikeSec = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastBroadcastSec = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastDamageMs = new ConcurrentHashMap<>();
   private final Map<UUID, World.Environment> lastDamageEnv = new ConcurrentHashMap<>();

   public DimensionWrathManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
      this.startTask();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("dimension-wrath.enabled", cfg.getBoolean("nether-wrath.enabled", true));
      this.applyToNether = cfg.getBoolean("dimension-wrath.apply-to-nether", true);
      this.applyToEnd = cfg.getBoolean("dimension-wrath.apply-to-end", true);
      this.strikeIntervalSeconds = Math.max(1, cfg.getInt("dimension-wrath.strike-interval-seconds", cfg.getInt("nether-wrath.strike-interval-seconds", 2)));
      this.damageAmount = Math.max(0.1, cfg.getDouble("dimension-wrath.damage", cfg.getDouble("nether-wrath.damage", 4.0)));
      this.broadcastIntervalSeconds = Math.max(1, cfg.getInt("dimension-wrath.broadcast-interval-seconds", cfg.getInt("nether-wrath.broadcast-interval-seconds", 30)));
      this.exemptCreative = cfg.getBoolean("dimension-wrath.exempt-creative", true);
      this.bypassPermission = cfg.getString("dimension-wrath.bypass-permission", "bloodmoon.bypass.dimensionwrath");
      this.broadcastChat = cfg.getBoolean("dimension-wrath.broadcast-chat", cfg.getBoolean("nether-wrath.broadcast-chat", false));

      this.netherBroadcast = cfg.getString(
         "dimension-wrath.nether.broadcast-message",
         cfg.getString("nether-wrath.broadcast-message",
         "<gradient:#ff4500:#990000><bold>⚡ NETHER WRATH:</bold></gradient> <red>There is no escape from the Blood Moon in the Nether! <yellow>{player}</yellow> was struck by hellish lightning for hiding there!</red>")
      );
      this.netherActionbar = cfg.getString(
         "dimension-wrath.nether.actionbar-message",
         cfg.getString("nether-wrath.actionbar-message",
         "<gradient:#ff5500:#aa0000><bold>⚡ Nether lightning strikes you for fleeing! (-{hearts} ❤)</bold></gradient>")
      );
      this.netherDeath = cfg.getString(
         "dimension-wrath.nether.death-message",
         cfg.getString("nether-wrath.death-message",
         "<gradient:#ff4500:#990000><bold>☠ <yellow>{player}</yellow> tried to hide from the Blood Moon in the Nether and was burned to ash!</gradient>")
      );

      this.endBroadcast = cfg.getString(
         "dimension-wrath.end.broadcast-message",
         "<gradient:#b000ff:#440077><bold>⚡ END WRATH:</bold></gradient> <red>There is no escape from the Blood Moon in the void! <yellow>{player}</yellow> was struck by astral lightning for hiding in the End!</red>"
      );
      this.endActionbar = cfg.getString(
         "dimension-wrath.end.actionbar-message",
         "<gradient:#c033ff:#660099><bold>⚡ Void lightning strikes you for fleeing to the End! (-{hearts} ❤)</bold></gradient>"
      );
      this.endDeath = cfg.getString(
         "dimension-wrath.end.death-message",
         "<gradient:#b000ff:#440077><bold>☠ <yellow>{player}</yellow> tried to hide from the Blood Moon in the End and was consumed by astral lightning!</gradient>"
      );
   }

   public void startTask() {
      if (this.task != null) {
         this.task.cancel();
      }
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 20L);
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
      this.lastStrikeSec.clear();
      this.lastBroadcastSec.clear();
      this.lastDamageMs.clear();
      this.lastDamageEnv.clear();
   }

   private boolean isTargetDimension(World w) {
      if (w == null) return false;
      World.Environment env = w.getEnvironment();
      String name = w.getName().toLowerCase();
      if (this.applyToNether && (env == World.Environment.NETHER || name.contains("nether"))) {
         return true;
      }
      if (this.applyToEnd && (env == World.Environment.THE_END || name.contains("end"))) {
         return true;
      }
      return false;
   }

   private boolean isExempt(Player p) {
      if (p == null) return true;
      if (p.isOp()) return true;
      if (this.exemptCreative && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
         return true;
      }
      return this.bypassPermission != null && !this.bypassPermission.isEmpty() && p.hasPermission(this.bypassPermission);
   }

   private void tick() {
      if (!this.enabled) {
         return;
      }

      boolean bmActive = this.plugin.events() != null && this.plugin.events().hasGlobalSession();
      if (!bmActive) {
         return;
      }

      long nowSec = System.currentTimeMillis() / 1000L;
      double hearts = this.damageAmount / 2.0;
      String heartsStr = (hearts == (long)hearts) ? String.format("%.0f", hearts) : String.format("%.1f", hearts);

      for (Player p : Bukkit.getOnlinePlayers()) {
         World w = p.getWorld();
         if (!this.isTargetDimension(w)) {
            continue;
         }
         if (this.isExempt(p)) {
            continue;
         }

         UUID id = p.getUniqueId();
         long lastStrike = this.lastStrikeSec.getOrDefault(id, 0L);
         long lastBc = this.lastBroadcastSec.getOrDefault(id, 0L);

         boolean isEnd = w.getEnvironment() == World.Environment.THE_END || w.getName().toLowerCase().contains("end");

         if (nowSec - lastStrike >= (long)this.strikeIntervalSeconds) {
            this.lastStrikeSec.put(id, nowSec);
            this.lastDamageMs.put(id, System.currentTimeMillis());
            this.lastDamageEnv.put(id, w.getEnvironment());

            Location loc = p.getLocation();
            w.strikeLightningEffect(loc);
            p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, isEnd ? 0.8F : 1.1F);
            p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8F, isEnd ? 1.4F : 1.2F);

            if (isEnd) {
               w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.08, 1.0F);
               w.spawnParticle(Particle.PORTAL, loc.clone().add(0.0, 1.0, 0.0), 25, 0.5, 0.5, 0.5, 0.1);
            } else {
               w.spawnParticle(Particle.FLAME, loc.clone().add(0.0, 1.0, 0.0), 15, 0.4, 0.4, 0.4, 0.06);
            }

            String barMsg = isEnd ? this.endActionbar : this.netherActionbar;
            if (barMsg != null && !barMsg.isEmpty()) {
               p.sendActionBar(Util.mini(barMsg.replace("{hearts}", heartsStr)));
            }

            p.damage(this.damageAmount);
         }

         if (nowSec - lastBc >= (long)this.broadcastIntervalSeconds) {
            this.lastBroadcastSec.put(id, nowSec);

            String bcMsg = isEnd ? this.endBroadcast : this.netherBroadcast;
            if (bcMsg != null && !bcMsg.isEmpty()) {
               String msg = bcMsg.replace("{player}", p.getName()).replace("{hearts}", heartsStr);
               if (this.broadcastChat) {
                  Bukkit.broadcast(Util.mini(msg));
               } else {
                  p.sendMessage(Util.mini(msg));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onDeath(PlayerDeathEvent e) {
      if (!this.enabled) {
         return;
      }
      Player p = e.getPlayer();
      Long t = this.lastDamageMs.get(p.getUniqueId());
      if (t != null && (System.currentTimeMillis() - t) <= 2500L) {
         World.Environment env = this.lastDamageEnv.get(p.getUniqueId());
         boolean isEnd = env == World.Environment.THE_END;
         String msg = isEnd ? this.endDeath : this.netherDeath;
         if (msg != null && !msg.isEmpty()) {
            e.deathMessage(Util.mini(msg.replace("{player}", p.getName())));
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      UUID id = e.getPlayer().getUniqueId();
      this.lastStrikeSec.remove(id);
      this.lastBroadcastSec.remove(id);
      this.lastDamageMs.remove(id);
      this.lastDamageEnv.remove(id);
   }
}
