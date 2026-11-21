package dev.bloodmoonplus.config;

import dev.bloodmoonplus.BloodMoonPlus;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

public final class WorldConfigRegistry {
   private final BloodMoonPlus plugin;
   private final File worldsDir;
   private final Map<String, WorldConfig> cache = new LinkedHashMap<>();

   public WorldConfigRegistry(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.worldsDir = new File(plugin.getDataFolder(), "worlds");
   }

   public void init() {
      this.worldsDir.mkdirs();
      this.installIfMissing("README.txt", "distribution/README.txt");
      File examples = new File(this.plugin.getDataFolder(), "EXAMPLES");
      if (!examples.exists()) {
         examples.mkdirs();
         this.installIfMissing("EXAMPLES/BossRewards-en.yml", "distribution/EXAMPLES/BossRewards-en.yml");
         this.installIfMissing("EXAMPLES/BossRewards-es.yml", "distribution/EXAMPLES/BossRewards-es.yml");
         this.installIfMissing("EXAMPLES/CommandsOnStart-en.yml", "distribution/EXAMPLES/CommandsOnStart-en.yml");
         this.installIfMissing("EXAMPLES/CommandsOnStart-es.yml", "distribution/EXAMPLES/CommandsOnStart-es.yml");
         this.installIfMissing("EXAMPLES/CommandsOnEnd-en.yml", "distribution/EXAMPLES/CommandsOnEnd-en.yml");
         this.installIfMissing("EXAMPLES/CommandsOnEnd-es.yml", "distribution/EXAMPLES/CommandsOnEnd-es.yml");
         this.installIfMissing("EXAMPLES/SurvivorRewards-en.yml", "distribution/EXAMPLES/SurvivorRewards-en.yml");
         this.installIfMissing("EXAMPLES/SurvivorRewards-es.yml", "distribution/EXAMPLES/SurvivorRewards-es.yml");
      }

      this.reload();
   }

   private void installIfMissing(String target, String resource) {
      File f = new File(this.plugin.getDataFolder(), target);
      if (!f.exists()) {
         f.getParentFile().mkdirs();

         try {
            this.plugin.saveResource(resource, false);
         } catch (IllegalArgumentException var5) {
            this.plugin.getLogger().fine("Recurso no empaquetado: " + resource + " (se omite)");
         }
      }
   }

   public void reload() {
      this.cache.clear();

      for (World w : Bukkit.getWorlds()) {
         this.loadFor(w.getName());
      }
   }

   public WorldConfig loadFor(String worldName) {
      File dir = new File(this.worldsDir, worldName);
      if (!dir.exists()) {
         dir.mkdirs();
      }

      File cfgFile = new File(dir, "config.yml");
      if (!cfgFile.exists()) {
         this.plugin.saveResource("worlds-template/config.yml", false);
         File template = new File(this.plugin.getDataFolder(), "worlds-template/config.yml");
         if (template.exists()) {
            try {
               Files.copy(template.toPath(), cfgFile.toPath());
            } catch (IOException var6) {
               this.plugin.getLogger().warning("Could not create config for '" + worldName + "': " + var6.getMessage());
            }
         }
      }

      YamlConfiguration yml = YamlConfiguration.loadConfiguration(cfgFile);
      WorldConfig wc = new WorldConfig(worldName, yml);
      this.cache.put(worldName, wc);
      return wc;
   }

   public WorldConfig get(String worldName) {
      WorldConfig c = this.cache.get(worldName);
      if (c == null) {
         c = this.loadFor(worldName);
      }

      return c;
   }

   public Collection<WorldConfig> all() {
      return this.cache.values();
   }
}
