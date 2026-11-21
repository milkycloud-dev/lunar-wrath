package dev.bloodmoonplus.stats;

import dev.bloodmoonplus.BloodMoonPlus;
import dev.bloodmoonplus.placeholder.HistoricalStatistics;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StatisticsStore {
   public static final String VERSION = "2";
   private final BloodMoonPlus plugin;
   private final File file;
   private final ReentrantLock lock = new ReentrantLock();
   private HistoricalStatistics data = HistoricalStatistics.empty();

   public StatisticsStore(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "statistics.yml");
   }

   public void load() {
      if (!this.file.exists()) {
         this.plugin.getLogger().info("statistics.yml not found, creating it with defaults.");
         this.saveNow();
      } else {
         this.lock.lock();

         try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
            HistoricalStatistics loaded = new HistoricalStatistics(
               y.getLong("total_events", 0L),
               y.getLong("total_death_events", 0L),
               y.getLong("total_unique_deaths", 0L),
               y.getLong("total_bosses_spawned", 0L),
               y.getLong("total_bosses_defeated", 0L),
               this.parseLastEvent(y.getConfigurationSection("last_event"))
            );
            this.data = loaded;
         } catch (Exception var10) {
            String backupName = "statistics.corrupt-" + System.currentTimeMillis() + ".yml";
            File backup = new File(this.plugin.getDataFolder(), backupName);

            try {
               Files.move(this.file.toPath(), backup.toPath());
               this.plugin.getLogger().warning("statistics.yml corrupto, archivado como " + backupName);
            } catch (IOException var9) {
            }

            this.data = HistoricalStatistics.empty();
         } finally {
            this.lock.unlock();
         }
      }
   }

   private HistoricalStatistics.LastEvent parseLastEvent(ConfigurationSection s) {
      return s == null
         ? HistoricalStatistics.empty().lastEvent()
         : new HistoricalStatistics.LastEvent(
            s.getString("world", ""),
            s.getLong("duration_seconds", 0L),
            s.getLong("death_count", 0L),
            s.getLong("unique_deaths", 0L),
            s.getLong("participants", 0L),
            s.getLong("survivors", 0L),
            s.getString("boss_name", ""),
            s.getString("boss_type", "NONE"),
            this.parseInstant(s.getString("ended_at"))
         );
   }

   private Instant parseInstant(String s) {
      if (s != null && !s.isEmpty()) {
         try {
            return Instant.parse(s);
         } catch (Exception var3) {
            return Instant.EPOCH;
         }
      } else {
         return Instant.EPOCH;
      }
   }

   public void saveNow() {
      this.lock.lock();

      try {
         YamlConfiguration y = new YamlConfiguration();
         HistoricalStatistics d = this.data;
         y.set("_version", "2");
         y.set("total_events", d.totalEvents());
         y.set("total_death_events", d.totalDeathEvents());
         y.set("total_unique_deaths", d.totalUniqueDeaths());
         y.set("total_bosses_spawned", d.totalBossesSpawned());
         y.set("total_bosses_defeated", d.totalBossesDefeated());
         HistoricalStatistics.LastEvent last = d.lastEvent();
         y.set("last_event.world", last.world());
         y.set("last_event.duration_seconds", last.durationSeconds());
         y.set("last_event.death_count", last.deathCount());
         y.set("last_event.unique_deaths", last.uniqueDeaths());
         y.set("last_event.participants", last.participants());
         y.set("last_event.survivors", last.survivors());
         y.set("last_event.boss_name", last.bossName());
         y.set("last_event.boss_type", last.bossType());
         y.set("last_event.ended_at", last.endedAt() != null && !last.endedAt().equals(Instant.EPOCH) ? last.endedAt().toString() : null);
         this.writeAtomic(y);
      } finally {
         this.lock.unlock();
      }
   }

   private void writeAtomic(YamlConfiguration y) {
      File tmp = new File(this.plugin.getDataFolder(), "statistics.yml.tmp");

      try {
         y.save(tmp);

         try {
            Files.move(tmp.toPath(), this.file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException var4) {
            Files.move(tmp.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException var5) {
         this.plugin.getLogger().warning("No se pudo escribir statistics.yml: " + var5.getMessage());
      }
   }

   public void recordEventCompleted(
      String world, long durationSec, long deaths, long uniqueDeaths, long participants, long survivors, String bossName, String bossType
   ) {
      this.lock.lock();

      try {
         this.data = new HistoricalStatistics(
            this.data.totalEvents() + 1L,
            this.data.totalDeathEvents() + deaths,
            this.data.totalUniqueDeaths() + uniqueDeaths,
            this.data.totalBossesSpawned(),
            this.data.totalBossesDefeated(),
            new HistoricalStatistics.LastEvent(
               world == null ? "" : world, durationSec, deaths, uniqueDeaths, participants, survivors, bossName, bossType, Instant.now()
            )
         );
      } finally {
         this.lock.unlock();
      }

      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::saveNow);
   }

   public void recordBossSpawned() {
      this.lock.lock();

      try {
         this.data = new HistoricalStatistics(
            this.data.totalEvents(),
            this.data.totalDeathEvents(),
            this.data.totalUniqueDeaths(),
            this.data.totalBossesSpawned() + 1L,
            this.data.totalBossesDefeated(),
            this.data.lastEvent()
         );
      } finally {
         this.lock.unlock();
      }

      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::saveNow);
   }

   public void recordBossDefeated() {
      this.lock.lock();

      try {
         this.data = new HistoricalStatistics(
            this.data.totalEvents(),
            this.data.totalDeathEvents(),
            this.data.totalUniqueDeaths(),
            this.data.totalBossesSpawned(),
            this.data.totalBossesDefeated() + 1L,
            this.data.lastEvent()
         );
      } finally {
         this.lock.unlock();
      }

      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::saveNow);
   }

   public HistoricalStatistics get() {
      this.lock.lock();

      HistoricalStatistics var1;
      try {
         var1 = this.data;
      } finally {
         this.lock.unlock();
      }

      return var1;
   }

   private static class FileUtils {
      static void copyDirectory(File src, File dest) throws IOException {
         if (src != null && src.exists()) {
            if (!dest.exists()) {
               dest.mkdirs();
            }

            for (File child : src.listFiles()) {
               if (child.isDirectory()) {
                  copyDirectory(child, new File(dest, child.getName()));
               } else {
                  Files.copy(child.toPath(), new File(dest, child.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
               }
            }
         }
      }
   }
}
