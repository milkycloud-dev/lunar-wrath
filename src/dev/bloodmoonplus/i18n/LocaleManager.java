package dev.bloodmoonplus.i18n;

import dev.bloodmoonplus.BloodMoonPlus;
import dev.bloodmoonplus.Util;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LocaleManager {
   public static final String VERSION = "1.0.9";
   private final BloodMoonPlus plugin;
   private final File localesDir;
   private YamlConfiguration active = new YamlConfiguration();
   private YamlConfiguration fallback = new YamlConfiguration();
   private String language = "en";
   private boolean useBundled = true;

   public LocaleManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.localesDir = new File(plugin.getDataFolder(), "locales");
   }

   public void init() {
      this.localesDir.mkdirs();

      for (String lang : new String[]{"en", "es", "ru"}) {
         File f = new File(this.localesDir, lang + ".yml");
         if (!f.exists()) {
            this.plugin.saveResource("locales/" + lang + ".yml", false);
         }
      }

      this.reload();
   }

   public void reload() {
      File cfgFile = new File(this.localesDir, "locales.yml");
      if (!cfgFile.exists()) {
         this.plugin.saveResource("locales/locales.yml", false);
      }

      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
      this.language = cfg.getString("Language", "en").toLowerCase(Locale.ROOT);
      this.useBundled = cfg.getBoolean("UseBundledLocales", true);
      this.active = YamlConfiguration.loadConfiguration(new File(this.localesDir, this.language + ".yml"));
      this.fallback = YamlConfiguration.loadConfiguration(new File(this.localesDir, "en.yml"));
      this.attachBundled(this.active, this.language);
      this.attachBundled(this.fallback, "en");
      this.plugin.getLogger().info("Language: " + this.language);
   }

   private void attachBundled(YamlConfiguration target, String lang) {
      try (InputStream in = this.plugin.getResource("locales/" + lang + ".yml")) {
         if (in != null) {
            target.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
         }
      } catch (Exception ex) {
         this.plugin.getLogger().warning("Locale " + lang + ": " + ex.getMessage());
      }
   }

   public boolean has(String key) {
      return this.active.getString(key) != null || this.useBundled && this.fallback.getString(key) != null;
   }

   public String get(String key) {
      if (this.useBundled) {
         String v = this.active.getString(key);
         if (v == null) {
            v = this.fallback.getString(key);
         }

         if (v != null) {
            return v;
         }
      } else {
         String vx = this.active.getString(key);
         if (vx != null) {
            return vx;
         }
      }

      return "<red>[i18n missing: " + key + "]</red>";
   }

   public String get(String key, String... kv) {
      return fill(this.get(key), kv);
   }

   public static String fill(String text, String... kv) {
      if (text == null) {
         return "";
      } else {
         for (int i = 0; i + 1 < kv.length; i += 2) {
            text = text.replace("{" + kv[i] + "}", Util.legacyToMini(kv[i + 1]));
         }

         return text;
      }
   }

   public String language() {
      return this.language;
   }
}
