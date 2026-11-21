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

public final class ElytraWrathManager implements Listener {
   private final BloodMoonPlus plugin;
   private BukkitTask task;

   private boolean enabled = true;
   private double minAltitude = 90.0;
   private int flightTimeSeconds = 3;
   private int strikeIntervalSeconds = 2;
   private double damageAmount = 4.0;
   private boolean disruptFlight = true;
   private int broadcastIntervalSeconds = 30;
   private boolean exemptCreative = true;
   private String bypassPermission = "bloodmoon.bypass.elytrawrath";
   private boolean broadcastChat = false;
   private double damageMultiplier = 1.5;
   private double maxDamage = 40.0;
   private int streakResetSeconds = 60;
   private double warnDamage = 8.0;

   private String broadcastMessage;
   private String actionbarMessage;
   private String deathMessage;
   private String warnMessage;

   private final Map<UUID, Long> glidingStartMs = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastStrikeSec = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastBroadcastSec = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastDamageMs = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> strikeStreak = new ConcurrentHashMap<>();
   private final Map<UUID, Boolean> warned = new ConcurrentHashMap<>();

   public ElytraWrathManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
      this.startTask();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("elytra-wrath.enabled", true);
      this.minAltitude = cfg.getDouble("elytra-wrath.min-altitude", 90.0);
      this.flightTimeSeconds = Math.max(0, cfg.getInt("elytra-wrath.flight-time-seconds", 3));
      this.strikeIntervalSeconds = Math.max(1, cfg.getInt("elytra-wrath.strike-interval-seconds", 2));
      this.damageAmount = Math.max(0.1, cfg.getDouble("elytra-wrath.damage", 4.0));
      this.disruptFlight = cfg.getBoolean("elytra-wrath.disrupt-flight", true);
      this.broadcastIntervalSeconds = Math.max(1, cfg.getInt("elytra-wrath.broadcast-interval-seconds", 30));
      this.exemptCreative = cfg.getBoolean("elytra-wrath.exempt-creative", true);
      this.bypassPermission = cfg.getString("elytra-wrath.bypass-permission", "bloodmoon.bypass.elytrawrath");
      this.broadcastChat = cfg.getBoolean("elytra-wrath.broadcast-chat", false);
      this.damageMultiplier = Math.max(1.0, cfg.getDouble("elytra-wrath.damage-multiplier", 1.5));
      this.maxDamage = Math.max(this.damageAmount, cfg.getDouble("elytra-wrath.max-damage", 40.0));
      this.streakResetSeconds = Math.max(1, cfg.getInt("elytra-wrath.streak-reset-seconds", 60));
      this.warnDamage = cfg.getDouble("elytra-wrath.warn-damage", 8.0);
      this.warnMessage = cfg.getString(
         "elytra-wrath.warn-message",
         "<dark_red><bold>⚠ The sky rejects you!</bold></dark_red> <red>Every strike hits harder than the last: the next one takes <yellow>{hearts} ❤</yellow>. Land now!</red>"
      );

      this.broadcastMessage = cfg.getString(
         "elytra-wrath.broadcast-message",
         "<gradient:#00e5ff:#0055ff><bold>⚡ WRATH OF THE SKY:</bold></gradient> <red>There is no escape from the Blood Moon in the sky! <yellow>{player}</yellow> was struck by lightning for flying on elytra!</red>"
      );
      this.actionbarMessage = cfg.getString(
         "elytra-wrath.actionbar-message",
         "<gradient:#00e5ff:#0088ff><bold>⚡ The Blood Moon's lightning knocks you out of the sky! (-{hearts} ❤)</bold></gradient>"
      );
      this.deathMessage = cfg.getString(
         "elytra-wrath.death-message",
         "<gradient:#00e5ff:#0055ff><bold>☠ <yellow>{player}</yellow> tried to escape the Blood Moon on elytra and was burned by lightning!</gradient>"
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
      this.glidingStartMs.clear();
      this.lastStrikeSec.clear();
      this.lastBroadcastSec.clear();
      this.lastDamageMs.clear();
      this.strikeStreak.clear();
      this.warned.clear();
   }

   private static String hearts(double damage) {
      double h = damage / 2.0;
      return h == (long)h ? String.format("%.0f", h) : String.format("%.1f", h);
   }

   private double strikeDamage(int streak) {
      return Math.min(this.maxDamage, this.damageAmount * Math.pow(this.damageMultiplier, streak));
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
         this.strikeStreak.clear();
         this.warned.clear();
         return;
      }

      long nowMs = System.currentTimeMillis();
      long nowSec = nowMs / 1000L;
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (this.isExempt(p)) {
            continue;
         }

         UUID id = p.getUniqueId();
         Long lastHit = this.lastDamageMs.get(id);
         if (lastHit != null && nowMs - lastHit >= (long)this.streakResetSeconds * 1000L) {
            this.strikeStreak.remove(id);
            this.warned.remove(id);
         }
         int streak = this.strikeStreak.getOrDefault(id, 0);
         double damage = this.strikeDamage(streak);
         String heartsStr = hearts(damage);
         boolean isGliding = p.isGliding() || (p.isFlying() && p.getGameMode() == GameMode.SURVIVAL);
         boolean isHigh = p.getLocation().getY() >= this.minAltitude;

         if (isGliding && isHigh) {
            this.glidingStartMs.putIfAbsent(id, nowMs);
            long start = this.glidingStartMs.get(id);

            if (nowMs - start >= (long)this.flightTimeSeconds * 1000L) {
               long lastStrike = this.lastStrikeSec.getOrDefault(id, 0L);
               long lastBc = this.lastBroadcastSec.getOrDefault(id, 0L);

               if (nowSec - lastStrike >= (long)this.strikeIntervalSeconds) {
                  this.lastStrikeSec.put(id, nowSec);
                  this.lastDamageMs.put(id, nowMs);

                  Location loc = p.getLocation();
                  World w = p.getWorld();
                  w.strikeLightningEffect(loc);
                  p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.2F);
                  p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9F, 1.4F);
                  w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 25, 0.6, 0.6, 0.6, 0.1);

                  if (this.actionbarMessage != null && !this.actionbarMessage.isEmpty()) {
                     p.sendActionBar(Util.mini(this.actionbarMessage.replace("{hearts}", heartsStr)));
                  }

                  if (this.disruptFlight) {
                     p.setGliding(false);
                  }

                  p.damage(damage);
                  this.strikeStreak.put(id, streak + 1);

                  double next = this.strikeDamage(streak + 1);
                  if (this.warnDamage > 0 && next >= this.warnDamage && !this.warned.containsKey(id)
                     && this.warnMessage != null && !this.warnMessage.isEmpty()) {
                     this.warned.put(id, true);
                     p.sendMessage(Util.mini(this.warnMessage.replace("{player}", p.getName()).replace("{hearts}", hearts(next))));
                     p.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
                  }
               }

               if (nowSec - lastBc >= (long)this.broadcastIntervalSeconds) {
                  this.lastBroadcastSec.put(id, nowSec);
                  if (this.broadcastMessage != null && !this.broadcastMessage.isEmpty()) {
                     String msg = this.broadcastMessage.replace("{player}", p.getName()).replace("{hearts}", heartsStr);
                     if (this.broadcastChat) {
                        Bukkit.broadcast(Util.mini(msg));
                     } else {
                        p.sendMessage(Util.mini(msg));
                     }
                  }
               }
            }
         } else {
            this.glidingStartMs.remove(id);
            this.lastStrikeSec.remove(id);
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
      this.strikeStreak.remove(p.getUniqueId());
      this.warned.remove(p.getUniqueId());
      if (t != null && (System.currentTimeMillis() - t) <= 2500L) {
         if (this.deathMessage != null && !this.deathMessage.isEmpty()) {
            e.deathMessage(Util.mini(this.deathMessage.replace("{player}", p.getName())));
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      UUID id = e.getPlayer().getUniqueId();
      this.glidingStartMs.remove(id);
      this.lastStrikeSec.remove(id);
      this.lastBroadcastSec.remove(id);
   }
}
