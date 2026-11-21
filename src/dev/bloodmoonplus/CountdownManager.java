package dev.bloodmoonplus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class CountdownManager {
   public enum WarningType {
      NORMAL, DANGEROUS, LETHAL;

      public static WarningType fromString(String str) {
         if (str == null) return NORMAL;
         String s = str.trim().toLowerCase(Locale.ROOT);
         if (s.contains("lethal") || s.contains("deadly") || s.contains("смерт") || s.contains("fatal") || s.equals("3")) {
            return LETHAL;
         }
         if (s.contains("danger") || s.contains("опасн") || s.contains("жестк") || s.equals("2")) {
            return DANGEROUS;
         }
         return NORMAL;
      }
   }

   private final BloodMoonPlus plugin;
   private BukkitTask task;
   private int remainingSeconds;
   private int totalSeconds;
   private double difficulty;
   private int durationMinutes;
   private List<World> targets = new ArrayList<>();
   private CommandSender initiator;
   private WarningType warningType = WarningType.NORMAL;
   private MoonType moonType = MoonType.BLOOD;

   private boolean showParameters = false;
   private boolean periodicAlerts = false;
   private String soundName = "entity.wither.ambient";
   private float soundVolume = 1.0F;
   private float soundPitch = 0.6F;
   private List<Integer> alertSeconds = List.of(300, 180, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1);
   private String tickSoundName = "block.note_block.basedrum";
   private String chatMsg;
   private String chatWithParamsMsg;
   private String titleMsg;
   private String subtitleMsg;
   private String subtitleWithParamsMsg;

   // 3 Warning types configuration
   private String normalChat;
   private String normalTitle;
   private String normalSubtitle;
   private String normalSound;
   private float normalVolume = 1.0F;
   private float normalPitch = 0.8F;
   private double normalDefaultDiff = 1.0;

   private String dangerChat;
   private String dangerTitle;
   private String dangerSubtitle;
   private String dangerSound;
   private float dangerVolume = 1.0F;
   private float dangerPitch = 0.9F;
   private double dangerDefaultDiff = 2.5;

   private String lethalChat;
   private String lethalTitle;
   private String lethalSubtitle;
   private String lethalSound;
   private float lethalVolume = 1.0F;
   private float lethalPitch = 0.6F;
   private double lethalDefaultDiff = 5.0;

   public CountdownManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.showParameters = cfg.getBoolean("countdown.show-parameters", cfg.getBoolean("warning.show-parameters", false));
      this.periodicAlerts = cfg.getBoolean("countdown.periodic-alerts", false);
      this.soundName = cfg.getString("countdown.sound", cfg.getString("warning.sound", "entity.wither.ambient"));
      this.soundVolume = (float)cfg.getDouble("countdown.sound-volume", cfg.getDouble("warning.sound-volume", 1.0));
      this.soundPitch = (float)cfg.getDouble("countdown.sound-pitch", cfg.getDouble("warning.sound-pitch", 0.6));

      List<Integer> list = cfg.getIntegerList("countdown.alert-seconds");
      if (list != null && !list.isEmpty()) {
         this.alertSeconds = list;
      } else {
         this.alertSeconds = List.of(300, 180, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1);
      }

      this.tickSoundName = cfg.getString("countdown.tick-sound", "block.note_block.basedrum");
      this.chatMsg = cfg.getString(
         "countdown.chat",
         "<dark_red><bold>☠ WARNING:</bold></dark_red> <red>The Blood Moon rises in <yellow>{time}</yellow>! Prepare for the nightmare!</red>"
      );
      this.chatWithParamsMsg = cfg.getString(
         "countdown.chat-with-params",
         "<dark_red><bold>☠ WARNING:</bold></dark_red> <red>The Blood Moon rises in <yellow>{time}</yellow>! <gray>(Difficulty <red>x{difficulty}</red> · Duration <white>{duration} min</white>)</gray></red>"
      );
      this.titleMsg = cfg.getString("countdown.title", "<dark_red><bold>BLOOD MOON</bold></dark_red>");
      this.subtitleMsg = cfg.getString("countdown.subtitle", "<red>Rises in {time}...</red>");
      this.subtitleWithParamsMsg = cfg.getString("countdown.subtitle-with-params", "<red>Rises in {time} <gray>(x{difficulty})</gray></red>");

      // 1. Normal
      this.normalChat = cfg.getString(
         "warning-types.normal.chat",
         "<gradient:#ff4500:#b30000><bold>🌙 OMEN OF THE BLOOD MOON:</bold></gradient> <red>The horizon turns crimson... The Blood Moon rises in <yellow>{time}</yellow>! Ready your weapons and fortify your shelters!</red>"
      );
      this.normalTitle = cfg.getString("warning-types.normal.title", "<gradient:#ff5555:#aa0000><bold>BLOOD MOON</bold></gradient>");
      this.normalSubtitle = cfg.getString("warning-types.normal.subtitle", "<gray>Rises in <yellow>{time}</yellow></gray>");
      this.normalSound = cfg.getString("warning-types.normal.sound", "ambient.cave");
      this.normalVolume = (float)cfg.getDouble("warning-types.normal.volume", 1.0);
      this.normalPitch = (float)cfg.getDouble("warning-types.normal.pitch", 0.8);
      this.normalDefaultDiff = cfg.getDouble("warning-types.normal.default-difficulty", 1.0);

      // 2. Dangerous
      this.dangerChat = cfg.getString(
         "warning-types.dangerous.chat",
         "<gradient:#ff0055:#990022><bold>☠ OMINOUS SIGN:</bold></gradient> <red>The air smells of ash and sulfur... A <bold><dark_red>DANGEROUS Blood Moon</dark_red></bold> rises in <yellow>{time}</yellow>! Ancient hordes thirst for blood!</red>"
      );
      this.dangerTitle = cfg.getString("warning-types.dangerous.title", "<gradient:#ff0033:#770011><bold>DANGEROUS MOON</bold></gradient>");
      this.dangerSubtitle = cfg.getString("warning-types.dangerous.subtitle", "<red>In <yellow>{time}</yellow>! Run!</red>");
      this.dangerSound = cfg.getString("warning-types.dangerous.sound", "entity.ender_dragon.growl");
      this.dangerVolume = (float)cfg.getDouble("warning-types.dangerous.volume", 1.0);
      this.dangerPitch = (float)cfg.getDouble("warning-types.dangerous.pitch", 0.9);
      this.dangerDefaultDiff = cfg.getDouble("warning-types.dangerous.default-difficulty", 2.5);

      // 3. Lethal
      this.lethalChat = cfg.getString(
         "warning-types.lethal.chat",
         "<gradient:#880000:#ff0000><bold>⚡ DEADLY DOOM:</bold></gradient> <red><bold>The ground shakes and the sky weeps blood...</bold> In <yellow>{time}</yellow> a <gradient:#ff0000:#330000><bold>LETHAL Blood Moon</bold></gradient> rises! Almost no one survives. There will be no mercy!</red>"
      );
      this.lethalTitle = cfg.getString("warning-types.lethal.title", "<gradient:#330000:#ff0000><bold>MOON OF DEATH</bold></gradient>");
      this.lethalSubtitle = cfg.getString("warning-types.lethal.subtitle", "<dark_red>In {time}. No one leaves alive</dark_red>");
      this.lethalSound = cfg.getString("warning-types.lethal.sound", "entity.wither.spawn");
      this.lethalVolume = (float)cfg.getDouble("warning-types.lethal.volume", 1.0);
      this.lethalPitch = (float)cfg.getDouble("warning-types.lethal.pitch", 0.6);
      this.lethalDefaultDiff = cfg.getDouble("warning-types.lethal.default-difficulty", 5.0);
   }

   public boolean isRunning() {
      return this.task != null && !this.task.isCancelled();
   }

   public int getRemainingSeconds() {
      return this.remainingSeconds;
   }

   public double getDefaultDifficulty(WarningType type) {
      if (type == WarningType.DANGEROUS) return this.dangerDefaultDiff;
      if (type == WarningType.LETHAL) return this.lethalDefaultDiff;
      return this.normalDefaultDiff;
   }

   public boolean start(int seconds, double difficulty, int durationMinutes, List<World> targets, CommandSender initiator) {
      return this.start(seconds, difficulty, durationMinutes, targets, initiator, WarningType.NORMAL, MoonType.BLOOD);
   }

   public boolean start(int seconds, double difficulty, int durationMinutes, List<World> targets, CommandSender initiator, WarningType type) {
      return this.start(seconds, difficulty, durationMinutes, targets, initiator, type, MoonType.BLOOD);
   }

   public boolean start(int seconds, double difficulty, int durationMinutes, List<World> targets, CommandSender initiator, WarningType type, MoonType moonType) {
      if (seconds <= 0) {
         return false;
      }

      this.cancel(false);

      this.warningType = type != null ? type : WarningType.NORMAL;
      this.moonType = moonType != null ? moonType : MoonType.BLOOD;
      this.totalSeconds = seconds;
      this.remainingSeconds = seconds;
      this.difficulty = difficulty;
      this.durationMinutes = durationMinutes;
      this.targets = targets != null && !targets.isEmpty() ? new ArrayList<>(targets) : this.plugin.events().defaultWorlds();
      this.initiator = initiator;

      // Broadcast atmospheric warning
      this.broadcastTypeWarning(this.warningType, this.remainingSeconds, this.moonType);

      if (this.plugin.spectatorBlock() != null) {
         this.plugin.spectatorBlock().kickAllSpectators(null);
      }

      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         this.remainingSeconds--;
         if (this.remainingSeconds <= 0) {
            this.finish();
         } else if (this.periodicAlerts && this.alertSeconds.contains(this.remainingSeconds) && this.remainingSeconds != this.totalSeconds) {
            this.broadcastAlert(this.remainingSeconds);
         } else if (this.periodicAlerts && this.remainingSeconds <= 5) {
            this.playTickSound(this.remainingSeconds);
         }
      }, 20L, 20L);

      return true;
   }

   public void broadcastTypeWarning(WarningType type, int seconds) {
      this.broadcastTypeWarning(type, seconds, MoonType.BLOOD);
   }

   public void broadcastTypeWarning(WarningType type, int seconds, MoonType moonType) {
      String timeStr = this.formatTime(seconds);
      String moonName = (moonType != null ? moonType : MoonType.BLOOD).getDisplayName();
      String chat = this.normalChat;
      String title = this.normalTitle;
      String sub = this.normalSubtitle;
      String sfxName = this.normalSound;
      float vol = this.normalVolume;
      float pitch = this.normalPitch;

      if (type == WarningType.DANGEROUS) {
         chat = this.dangerChat;
         title = this.dangerTitle;
         sub = this.dangerSubtitle;
         sfxName = this.dangerSound;
         vol = this.dangerVolume;
         pitch = this.dangerPitch;
      } else if (type == WarningType.LETHAL) {
         chat = this.lethalChat;
         title = this.lethalTitle;
         sub = this.lethalSubtitle;
         sfxName = this.lethalSound;
         vol = this.lethalVolume;
         pitch = this.lethalPitch;
      }

      if (chat != null && !chat.isEmpty()) {
         String c = chat.replace("{time}", timeStr);
         c = this.renameMoon(c, moonType);
         Bukkit.broadcast(Util.mini(c));
      }

      Sound sfx = this.parseSound(sfxName, Sound.ENTITY_WITHER_AMBIENT);
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (sfx != null) {
            p.playSound(p.getLocation(), sfx, vol, pitch);
         }
      }

      if (title != null && !title.isEmpty()) {
         String tStr = title.replace("{time}", timeStr);
         String sStr = sub != null ? sub.replace("{time}", timeStr) : "";
         tStr = this.renameMoon(tStr, moonType);
         Times times = Times.times(Duration.ofMillis(300L), Duration.ofMillis(3000L), Duration.ofMillis(500L));
         Title t = Title.title(Util.mini(tStr), Util.mini(sStr), times);
         for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
         }
      }
   }

   public void cancel(boolean announce) {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
         if (announce) {
            Bukkit.broadcast(Util.mini(this.plugin.locales().get("text.countdown.m001")));
         }
      }
   }

   private void finish() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
      this.plugin.events().startIn(this.targets, this.difficulty, Math.max(1, this.durationMinutes), this.moonType);
   }

   private String renameMoon(String text, MoonType moonType) {
      if (text == null || moonType == null || moonType == MoonType.BLOOD) {
         return text;
      }
      String rules = this.plugin.locales().get("moon.rename." + moonType.getId());
      for (String rule : rules.split("\\|")) {
         int i = rule.indexOf("=>");
         if (i > 0) {
            text = text.replace(rule.substring(0, i), rule.substring(i + 2));
         }
      }
      return text;
   }

   public String formatTime(int sec) {
      if (sec >= 60 && sec % 60 == 0) {
         return this.plugin.locales().get("time.min", "min", String.valueOf(sec / 60));
      } else if (sec >= 60) {
         return this.plugin.locales().get("time.min-sec", "min", String.valueOf(sec / 60), "sec", String.valueOf(sec % 60));
      } else {
         return this.plugin.locales().get("time.sec", "sec", String.valueOf(sec));
      }
   }

   private Sound parseSound(String name, Sound def) {
      if (name == null || name.trim().isEmpty()) {
         return def;
      }
      String clean = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
      try {
         return Sound.valueOf(clean);
      } catch (Exception ex) {
         return def;
      }
   }

   private void playTickSound(int sec) {
      Sound sfx = this.parseSound(this.tickSoundName, Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
      float pitch = (float)(0.6 + (5 - sec) * 0.15);
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (sfx != null) {
            p.playSound(p.getLocation(), sfx, 0.8F, pitch);
         }
      }
   }

   private void broadcastAlert(int sec) {
      String timeStr = this.formatTime(sec);
      String diffStr = Util.fmt(this.difficulty);
      String durStr = String.valueOf(this.durationMinutes);

      String chatPattern = this.showParameters ? this.chatWithParamsMsg : this.chatMsg;
      if (chatPattern != null && !chatPattern.isEmpty()) {
         String text = chatPattern
            .replace("{time}", timeStr)
            .replace("{difficulty}", diffStr)
            .replace("{duration}", durStr);
         Bukkit.broadcast(Util.mini(text));
      }

      Sound scarySfx = this.parseSound(this.soundName, Sound.ENTITY_WITHER_AMBIENT);
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (scarySfx != null) {
            p.playSound(p.getLocation(), scarySfx, this.soundVolume, this.soundPitch);
         }
      }

      if (this.titleMsg != null && !this.titleMsg.isEmpty()) {
         String subPattern = this.showParameters ? this.subtitleWithParamsMsg : this.subtitleMsg;
         String t = this.titleMsg.replace("{time}", timeStr).replace("{difficulty}", diffStr).replace("{duration}", durStr);
         String sub = subPattern != null
            ? subPattern.replace("{time}", timeStr).replace("{difficulty}", diffStr).replace("{duration}", durStr)
            : "";

         int stayTicks = sec <= 3 ? 15 : 40;
         Times times = Times.times(Duration.ofMillis(200L), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(300L));
         Title title = Title.title(Util.mini(t), Util.mini(sub), times);

         for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
         }
      }
   }
}
