package dev.bloodmoonplus;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class LightningEffectManager implements Listener {
   public static final UUID ALL_PLAYERS = new UUID(0L, 0L);
   public static final double MIN_STORM_RATE = 0.05;
   public static final double MAX_STORM_RATE = 50.0;
   public static final double MIN_SMITE_INTERVAL = 0.1;

   public static class SmiteTask {
      public final UUID playerId;
      public final double damage;
      public volatile double intervalSeconds;
      public final long endMs;
      public long lastStrikeMs;

      public SmiteTask(UUID playerId, double damage, double intervalSeconds, long durationSeconds) {
         this.playerId = playerId;
         this.damage = damage;
         this.intervalSeconds = Math.max(MIN_SMITE_INTERVAL, intervalSeconds);
         this.endMs = durationSeconds > 0 ? System.currentTimeMillis() + durationSeconds * 1000L : 0L;
         this.lastStrikeMs = 0L;
      }
   }

   public static class AmbientStorm {
      public final UUID playerId;
      public volatile double strikesPerSec;
      public final double radius;
      public final long endMs;

      public AmbientStorm(UUID playerId, double strikesPerSec, double radius, long durationSeconds) {
         this.playerId = playerId;
         this.strikesPerSec = Math.max(MIN_STORM_RATE, strikesPerSec);
         this.radius = Math.max(1.0, radius);
         this.endMs = durationSeconds > 0 ? System.currentTimeMillis() + durationSeconds * 1000L : 0L;
      }
   }

   private final BloodMoonPlus plugin;
   private BukkitTask mainTask;
   private final Map<UUID, SmiteTask> smites = new ConcurrentHashMap<>();
   private final Map<UUID, AmbientStorm> storms = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastSmiteDamageMs = new ConcurrentHashMap<>();

   public LightningEffectManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.startTask();
   }

   public void startTask() {
      if (this.mainTask != null) {
         this.mainTask.cancel();
      }
      this.mainTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 2L, 2L);
   }

   public void shutdown() {
      if (this.mainTask != null) {
         this.mainTask.cancel();
         this.mainTask = null;
      }
      this.smites.clear();
      this.storms.clear();
      this.lastSmiteDamageMs.clear();
   }

   public void startStorm(UUID target, double strikesPerSec, double radius, long durationSeconds) {
      this.storms.put(target, new AmbientStorm(target, strikesPerSec, radius, durationSeconds));
   }

   public boolean stopStorm(UUID target) {
      return this.storms.remove(target) != null;
   }

   public int setStormRate(UUID target, double strikesPerSec) {
      double v = Math.max(MIN_STORM_RATE, Math.min(MAX_STORM_RATE, strikesPerSec));
      int n = 0;
      for (AmbientStorm st : this.storms.values()) {
         if (target == null || st.playerId.equals(target)) {
            st.strikesPerSec = v;
            n++;
         }
      }
      return n;
   }

   public int setSmiteInterval(UUID target, double intervalSeconds) {
      double v = Math.max(MIN_SMITE_INTERVAL, intervalSeconds);
      int n = 0;
      for (SmiteTask t : this.smites.values()) {
         if (target == null || t.playerId.equals(target)) {
            t.intervalSeconds = v;
            n++;
         }
      }
      return n;
   }

   public void startSmite(UUID target, double damage, double intervalSeconds, long durationSeconds) {
      this.smites.put(target, new SmiteTask(target, damage, intervalSeconds, durationSeconds));
   }

   public boolean stopSmite(UUID target) {
      return this.smites.remove(target) != null;
   }

   public void stopAll(UUID target) {
      this.stopStorm(target);
      this.stopSmite(target);
   }

   public void stopAllGlobal() {
      this.storms.clear();
      this.smites.clear();
   }

   public Map<UUID, AmbientStorm> getStorms() {
      return this.storms;
   }

   public Map<UUID, SmiteTask> getSmites() {
      return this.smites;
   }

   public void singleStrike(Player p, double damage) {
      if (p == null || !p.isOnline()) return;
      Location loc = p.getLocation();
      loc.getWorld().strikeLightningEffect(loc);
      loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8F, 1.0F);
      loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9F, 1.2F);
      loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.08);

      if (damage > 0.0) {
         this.lastSmiteDamageMs.put(p.getUniqueId(), System.currentTimeMillis());
         p.damage(damage);
         p.sendActionBar(Util.mini(this.plugin.locales().get("text.lightning.m001", "damage", String.valueOf((damage / 2.0)))));
      }
   }

   private void tick() {
      long now = System.currentTimeMillis();

      // 1. Process Ambient Storms
      for (Map.Entry<UUID, AmbientStorm> entry : this.storms.entrySet()) {
         AmbientStorm storm = entry.getValue();
         if (storm.endMs > 0 && now >= storm.endMs) {
            this.storms.remove(entry.getKey());
            continue;
         }

         double expected = storm.strikesPerSec * 0.1;
         int count = (int) expected;
         if (ThreadLocalRandom.current().nextDouble() < (expected - count)) {
            count++;
         }
         if (count <= 0) continue;

         Collection<? extends Player> targets;
         if (storm.playerId.equals(ALL_PLAYERS)) {
            targets = Bukkit.getOnlinePlayers();
         } else {
            Player p = Bukkit.getPlayer(storm.playerId);
            targets = (p != null && p.isOnline()) ? java.util.List.of(p) : java.util.List.of();
         }

         for (Player p : targets) {
            World w = p.getWorld();
            Location pLoc = p.getLocation();

            for (int i = 0; i < count; i++) {
               double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
               double dist = ThreadLocalRandom.current().nextDouble(2.0, storm.radius);
               Location sLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
               int highY = w.getHighestBlockYAt(sLoc);
               sLoc.setY(highY > 0 ? highY : pLoc.getY());

               w.strikeLightningEffect(sLoc);
               float pitch = 0.7F + ThreadLocalRandom.current().nextFloat() * 0.4F;
               w.playSound(sLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, pitch);
            }
         }
      }

      // 2. Process Smite Tasks
      for (Map.Entry<UUID, SmiteTask> entry : this.smites.entrySet()) {
         SmiteTask smite = entry.getValue();
         if (smite.endMs > 0 && now >= smite.endMs) {
            this.smites.remove(entry.getKey());
            continue;
         }

         if (now - smite.lastStrikeMs >= (long) (smite.intervalSeconds * 1000.0)) {
            smite.lastStrikeMs = now;
            Player p = Bukkit.getPlayer(smite.playerId);
            if (p != null && p.isOnline() && !p.isDead()) {
               Location loc = p.getLocation();
               loc.getWorld().strikeLightningEffect(loc);
               p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.0F);
               p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8F, 1.2F);
               loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.05);

               if (smite.damage > 0.0) {
                  this.lastSmiteDamageMs.put(p.getUniqueId(), now);
                  p.damage(smite.damage);
                  p.sendActionBar(Util.mini(this.plugin.locales().get("text.lightning.m002", "damage", String.valueOf((smite.damage / 2.0)))));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPlayerDeath(PlayerDeathEvent e) {
      Player p = e.getPlayer();
      Long t = this.lastSmiteDamageMs.get(p.getUniqueId());
      if (t != null && (System.currentTimeMillis() - t) <= 2500L) {
         e.deathMessage(Util.mini(this.plugin.locales().get("text.lightning.m003", "player", String.valueOf(p.getName()))));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      UUID id = e.getPlayer().getUniqueId();
      this.smites.remove(id);
      this.storms.remove(id);
      this.lastSmiteDamageMs.remove(id);
   }
}
