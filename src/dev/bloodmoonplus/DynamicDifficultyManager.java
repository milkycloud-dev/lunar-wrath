package dev.bloodmoonplus;

import java.util.Locale;
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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class DynamicDifficultyManager implements Listener {
   private final BloodMoonPlus plugin;
   private BukkitTask task;

   // Configuration options
   private boolean enabled = true;
   private int inactivitySeconds = 45;
   private double campingRadius = 4.0;
   private int escalationIntervalSeconds = 25;
   private double difficultyStep = 0.25;
   private double maxDifficulty = 0.0;
   private boolean spawnPunishmentHorde = true;
   private int punishmentHordeCount = 5;
   private boolean decayOnCombat = true;
   private int killsForDecay = 10;
   private double decayStep = 0.1;
   private boolean exemptCreative = true;
   private String bypassPermission = "bloodmoon.bypass.dynamicdifficulty";
   private boolean broadcastChat = false;

   // Atmospheric messages
   private String broadcastEscalate;
   private String camperWarning;
   private String broadcastDecay;

   // Tracking states per player
   private final Map<UUID, Long> lastCombatMs = new ConcurrentHashMap<>();
   private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
   private final Map<UUID, Long> locationSinceMs = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastEscalateSec = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastCamperWarnSec = new ConcurrentHashMap<>();

   // Combat kill counters & base difficulty per world
   private final Map<String, Integer> killCounterPerWorld = new ConcurrentHashMap<>();
   private final Map<String, Map<String, Integer>> killersPerWorld = new ConcurrentHashMap<>();
   private final Map<String, Double> baseDifficultyPerWorld = new ConcurrentHashMap<>();

   public DynamicDifficultyManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
      this.startTask();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("dynamic-difficulty.enabled", true);
      this.inactivitySeconds = Math.max(10, cfg.getInt("dynamic-difficulty.inactivity-seconds", 45));
      this.campingRadius = Math.max(1.0, cfg.getDouble("dynamic-difficulty.camping-radius", 4.0));
      this.escalationIntervalSeconds = Math.max(5, cfg.getInt("dynamic-difficulty.escalation-interval-seconds", 25));
      this.difficultyStep = Math.max(0.01, cfg.getDouble("dynamic-difficulty.difficulty-step", 0.25));
      double cap = cfg.getDouble("dynamic-difficulty.max-difficulty", 0.0);
      this.maxDifficulty = cap <= 0.0 ? 0.0 : Math.max(1.0, cap);
      this.spawnPunishmentHorde = cfg.getBoolean("dynamic-difficulty.spawn-punishment-horde", true);
      this.punishmentHordeCount = Math.max(1, cfg.getInt("dynamic-difficulty.punishment-horde-count", 5));
      this.decayOnCombat = cfg.getBoolean("dynamic-difficulty.decay-on-combat", true);
      this.killsForDecay = Math.max(1, cfg.getInt("dynamic-difficulty.kills-for-decay", 10));
      this.decayStep = Math.max(0.01, cfg.getDouble("dynamic-difficulty.decay-step", 0.1));
      this.exemptCreative = cfg.getBoolean("dynamic-difficulty.exempt-creative", true);
      this.bypassPermission = cfg.getString("dynamic-difficulty.bypass-permission", "bloodmoon.bypass.dynamicdifficulty");
      this.broadcastChat = cfg.getBoolean("dynamic-difficulty.broadcast-chat", true);

      this.broadcastEscalate = cfg.getString(
         "dynamic-difficulty.announcement.escalate",
         "<gradient:#ff2200:#ff8800><bold>⚡ ЯРОСТЬ КРОВАВОЙ ЛУНЫ:</bold></gradient> <red>Игрок <yellow>{player}</yellow> отсиживается вместо боя, и Луна распаляется! Сложность возросла до <yellow>x{difficulty}</yellow>!</red>"
      );
      this.camperWarning = cfg.getString(
         "dynamic-difficulty.announcement.camper-warning",
         "<gradient:#ff0000:#aa0000><bold>☠ Кровавая Луна чует ваш страх! Вы прячетесь от боя! Сражайтесь или навлечёте беду!</bold></gradient>"
      );
      this.broadcastDecay = cfg.getString(
         "dynamic-difficulty.announcement.decay",
         "<gradient:#00ff88:#00aa55><bold>✔ ВОЛЯ ВЫЖИВШИХ:</bold></gradient> <green><yellow>{players}</yellow> истребляют порождения луны, и её ярость стихает! Сложность снизилась до <yellow>x{difficulty}</yellow>.</green>"
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
      this.lastCombatMs.clear();
      this.lastLocation.clear();
      this.locationSinceMs.clear();
      this.lastEscalateSec.clear();
      this.lastCamperWarnSec.clear();
      this.killCounterPerWorld.clear();
      this.killersPerWorld.clear();
      this.baseDifficultyPerWorld.clear();
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public double getBaseDifficulty(String worldName) {
      return this.baseDifficultyPerWorld.getOrDefault(worldName, 1.0);
   }

   public void resetDifficulty(String worldName) {
      if (this.plugin.events() == null) return;
      EventManager.Session s = this.plugin.events().session(Bukkit.getWorld(worldName));
      if (s != null) {
         double base = this.baseDifficultyPerWorld.getOrDefault(worldName, 1.0);
         s.difficulty = base;
         this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);
      }
   }

   public void recordCombat(Player p) {
      if (p == null) return;
      this.lastCombatMs.put(p.getUniqueId(), System.currentTimeMillis());
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
      if (!this.enabled || this.plugin.events() == null || !this.plugin.events().hasGlobalSession()) {
         return;
      }

      long nowMs = System.currentTimeMillis();
      long nowSec = nowMs / 1000L;

      for (EventManager.Session s : this.plugin.events().sessions()) {
         World w = s.world;
         if (w == null) continue;
         String wn = w.getName();

         // Save base difficulty once when session begins
         this.baseDifficultyPerWorld.putIfAbsent(wn, s.difficulty);

         for (Player p : w.getPlayers()) {
            if (this.isExempt(p)) {
               continue;
            }

            UUID id = p.getUniqueId();
            Location curLoc = p.getLocation();

            // Track movement
            Location prevLoc = this.lastLocation.get(id);
            if (prevLoc == null || !prevLoc.getWorld().equals(curLoc.getWorld()) || prevLoc.distance(curLoc) > this.campingRadius) {
               this.lastLocation.put(id, curLoc.clone());
               this.locationSinceMs.put(id, nowMs);
            }

            long lastCombat = this.lastCombatMs.computeIfAbsent(id, k -> nowMs);
            long idleSec = (nowMs - lastCombat) / 1000L;
            long campingSec = (nowMs - this.locationSinceMs.getOrDefault(id, nowMs)) / 1000L;

            boolean isCamping = (idleSec >= this.inactivitySeconds) || (campingSec >= this.inactivitySeconds);

            if (isCamping) {
               // Actionbar warning to the camper every 10s
               long lastWarn = this.lastCamperWarnSec.getOrDefault(id, 0L);
               if (nowSec - lastWarn >= 10L) {
                  this.lastCamperWarnSec.put(id, nowSec);
                  if (this.camperWarning != null && !this.camperWarning.isEmpty()) {
                     p.sendActionBar(Util.mini(this.camperWarning));
                  }
               }

               // Check escalation interval
               long lastEsc = this.lastEscalateSec.getOrDefault(id, 0L);
               if (nowSec - lastEsc >= (long)this.escalationIntervalSeconds) {
                  this.lastEscalateSec.put(id, nowSec);

                  double curDiff = s.difficulty;
                  boolean capped = this.maxDifficulty > 0.0;
                  if (!capped || curDiff < this.maxDifficulty) {
                     double raised = Math.round((curDiff + this.difficultyStep) * 100.0) / 100.0;
                     double nextDiff = capped ? Math.min(this.maxDifficulty, raised) : raised;
                     s.difficulty = nextDiff;
                     this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);

                     if (this.broadcastChat && this.broadcastEscalate != null && !this.broadcastEscalate.isEmpty()) {
                        String msg = this.broadcastEscalate
                           .replace("{player}", p.getName())
                           .replace("{difficulty}", String.format(Locale.ROOT, "%.2f", nextDiff));
                        Bukkit.broadcast(Util.mini(msg));
                     } else {
                        p.sendMessage(Util.mini("<red>⚡ Пассивность распаляет зло! Сложность Луны возросла до <yellow>x" + String.format(Locale.ROOT, "%.2f", nextDiff) + "</yellow>!</red>"));
                     }

                     w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.2F);
                     w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().clone().add(0, 1, 0), 30, 0.8, 0.8, 0.8, 0.05);
                  }

                  // Punitive horde
                  if (this.spawnPunishmentHorde) {
                     this.plugin.events().spawnPunishmentHorde(p, this.punishmentHordeCount, s.difficulty);
                  }
               }
            }
         }
      }
   }

   private void onMonsterKilled(Player killer) {
      if (!this.decayOnCombat || this.plugin.events() == null) {
         return;
      }
      World w = killer.getWorld();
      EventManager.Session s = this.plugin.events().session(w);
      if (s == null) {
         return;
      }

      int kills = this.killCounterPerWorld.merge(w.getName(), 1, Integer::sum);
      Map<String, Integer> killers = this.killersPerWorld.computeIfAbsent(w.getName(), k -> new ConcurrentHashMap<>());
      killers.merge(killer.getName(), 1, Integer::sum);
      if (kills >= this.killsForDecay) {
         this.killCounterPerWorld.put(w.getName(), 0);
         String players = killers.entrySet().stream()
            .sorted((x, y) -> Integer.compare(y.getValue(), x.getValue()))
            .limit(3)
            .map(en -> en.getKey() + " (" + en.getValue() + ")")
            .collect(java.util.stream.Collectors.joining(", "));
         killers.clear();
         double base = this.baseDifficultyPerWorld.getOrDefault(w.getName(), 1.0);
         if (s.difficulty > base) {
            double nextDiff = Math.max(base, Math.round((s.difficulty - this.decayStep) * 100.0) / 100.0);
            if (nextDiff < s.difficulty) {
               s.difficulty = nextDiff;
               this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);

               if (this.broadcastChat && this.broadcastDecay != null && !this.broadcastDecay.isEmpty()) {
                  String msg = this.broadcastDecay
                     .replace("{players}", players)
                     .replace("{player}", killer.getName())
                     .replace("{difficulty}", String.format(Locale.ROOT, "%.2f", nextDiff));
                  Bukkit.broadcast(Util.mini(msg));
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamage(EntityDamageByEntityEvent e) {
      Player p = null;
      if (e.getDamager() instanceof Player pl) {
         p = pl;
      } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl) {
         p = pl;
      }

      if (p != null && e.getEntity() instanceof Monster) {
         this.recordCombat(p);
      } else if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Monster) {
         this.recordCombat(victim);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onKill(EntityDeathEvent e) {
      Player killer = e.getEntity().getKiller();
      if (killer != null && e.getEntity() instanceof Monster) {
         this.recordCombat(killer);
         this.onMonsterKilled(killer);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      UUID id = e.getPlayer().getUniqueId();
      this.lastCombatMs.remove(id);
      this.lastLocation.remove(id);
      this.locationSinceMs.remove(id);
      this.lastEscalateSec.remove(id);
      this.lastCamperWarnSec.remove(id);
   }

   public Map<UUID, Long> getLastCombatMs() {
      return this.lastCombatMs;
   }
}
