package dev.bloodmoonplus;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ScheduleManager {
   private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");
   private static final Locale ES = Locale.of("es", "MX");
   private final BloodMoonPlus plugin;
   private final List<ScheduleManager.Slot> slots = new ArrayList<>();
   private final Map<String, Long> warned = new HashMap<>();
   private ZoneId zone = ZoneId.of("America/Mexico_City");
   private boolean enabled;
   private boolean skipEmpty;
   private int graceSec;
   private List<Integer> warnMin = List.of();
   private ScheduleManager.Mode mode = ScheduleManager.Mode.WEEKLY;
   private int intervalHours = 72;

   ScheduleManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   void reload() {
      this.slots.clear();
      this.warned.clear();
      FileConfiguration c = this.plugin.getConfig();
      this.enabled = c.getBoolean("schedule.enabled", true);
      this.skipEmpty = c.getBoolean("schedule.skip-if-no-players", true);
      this.graceSec = c.getInt("schedule.grace-seconds", 180);
      List<Integer> wm = c.getIntegerList("schedule.warnings-minutes");
      this.warnMin = (wm != null && !wm.isEmpty()) ? wm : List.of(10);

      try {
         this.zone = ZoneId.of(c.getString("schedule.timezone", "America/Mexico_City"));
      } catch (DateTimeException var22) {
         this.plugin.getLogger().warning("Invalid timezone, using UTC");
         this.zone = ZoneId.of("America/Mexico_City");
      }

      double defDiff = c.getDouble("difficulty.default", 1.0);
      int defDur = c.getInt("event.duration-minutes", 20);
      String modeStr = c.getString("schedule.mode", "weekly").toLowerCase(Locale.ROOT);
      switch (modeStr) {
         case "interval":
         case "horas":
         case "hours":
            this.mode = ScheduleManager.Mode.INTERVAL;
            break;
         default:
            this.mode = ScheduleManager.Mode.WEEKLY;
      }

      if (this.mode == ScheduleManager.Mode.INTERVAL) {
         this.intervalHours = Math.max(1, c.getInt("schedule.interval-hours", 72));
         this.plugin.getLogger().info("Modo interval: cada " + this.intervalHours + "h reales");
      } else {
         ConfigurationSection days = c.getConfigurationSection("schedule.days");
         if (days != null) {
            for (String key : days.getKeys(false)) {
               List<DayOfWeek> targets = parseDays(key);
               if (targets.isEmpty()) {
                  this.plugin.getLogger().warning("schedule.days: unknown day '" + key + "'");
               } else {
                  for (Object o : days.getList(key, List.of())) {
                     LocalTime t = null;
                     double diff = defDiff;
                     int dur = defDur;
                     List<String> worlds = List.of();
                     if (!(o instanceof Map<?, ?> m)) {
                        t = parseTime(o);
                     } else {
                        t = parseTime(m.get("time"));
                        diff = Util.num(m, "difficulty", defDiff);
                        dur = (int)Util.num(m, "duration-minutes", (double)defDur);
                        if (m.get("worlds") instanceof List<?> wl) {
                           List<String> tmp = new ArrayList<>();

                           for (Object x : wl) {
                              tmp.add(x.toString());
                           }

                           worlds = tmp;
                        }
                     }

                     if (t == null) {
                        this.plugin.getLogger().warning("schedule.days." + key + ": invalid time '" + o + "' (use \"21:30\" in quotes)");
                     } else {
                        for (DayOfWeek d : targets) {
                           this.slots.add(new ScheduleManager.Slot(d, t, diff, dur, worlds));
                        }
                     }
                  }
               }
            }

            this.plugin.getLogger().info("Schedule loaded: " + this.slots.size() + " slots (weekly mode, zone " + this.zone + ")");
         }
      }
   }

   static LocalTime parseTime(Object o) {
      if (o == null) {
         return null;
      } else if (!(o instanceof Number n)) {
         try {
            return LocalTime.parse(o.toString().trim(), TIME_FMT);
         } catch (DateTimeException var3) {
            return null;
         }
      } else {
         int v = n.intValue();
         return v >= 0 && v < 1440 ? LocalTime.of(v / 60, v % 60) : null;
      }
   }

   static List<DayOfWeek> parseDays(String key) {
      if (key == null) {
         return List.of();
      } else {
         String k = Normalizer.normalize(key, Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT);
         switch (k) {
            case "ALL":
            case "TODOS":
            case "DIARIO":
            case "DIARIAMENTE":
               return Arrays.asList(DayOfWeek.values());
            case "LUNES":
               k = "MONDAY";
               break;
            case "MARTES":
               k = "TUESDAY";
               break;
            case "MIERCOLES":
               k = "WEDNESDAY";
               break;
            case "JUEVES":
               k = "THURSDAY";
               break;
            case "VIERNES":
               k = "FRIDAY";
               break;
            case "SABADO":
               k = "SATURDAY";
               break;
            case "DOMINGO":
               k = "SUNDAY";
         }

         try {
            return List.of(DayOfWeek.valueOf(k));
         } catch (IllegalArgumentException var4) {
            return List.of();
         }
      }
   }

   public ZoneId zone() {
      return this.zone;
   }

   public ScheduleManager.Mode mode() {
      return this.mode;
   }

   public void tick() {
      if (this.enabled) {
         if (this.mode == ScheduleManager.Mode.INTERVAL) {
            this.tickInterval();
         } else {
            this.tickWeekly();
         }
      }
   }

   private void tickWeekly() {
      if (!this.slots.isEmpty()) {
         ZonedDateTime now = ZonedDateTime.now(this.zone);

         for (int off = -1; off <= 1; off++) {
            LocalDate date = now.toLocalDate().plusDays((long)off);

            for (ScheduleManager.Slot slot : this.slots) {
               if (slot.day() == date.getDayOfWeek()) {
                  ZonedDateTime at = ZonedDateTime.of(date, slot.time(), this.zone);
                  long past = Duration.between(at, now).getSeconds();
                  String key = date + "T" + slot.time();
                  if (past >= 0L && past <= (long)this.graceSec) {
                     if (!this.plugin.data().isFired(key)) {
                        this.plugin.data().markFired(key);
                        this.fire(slot);
                     }
                  } else if (past < 0L) {
                     long until = -past;

                     for (int m : this.warnMin) {
                        String wkey = key + "/" + m;
                        if (until <= (long)m * 60L && until > (long)m * 60L - 30L && !this.warned.containsKey(wkey)) {
                           this.warned.put(wkey, System.currentTimeMillis());
                           this.warn(slot, m);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void tickInterval() {
      long nowMs = System.currentTimeMillis();
      String key = "interval";
      Long lastFired = this.plugin.data().getLastIntervalFire();
      if (lastFired != null && lastFired > 0L) {
         long nextFire = lastFired + (long)this.intervalHours * 3600000L;
         long untilMs = nextFire - nowMs;
         long untilSec = untilMs / 1000L;

         for (int m : this.warnMin) {
            String wkey = key + "/" + m;
            if (untilSec <= (long)m * 60L && untilSec > (long)m * 60L - 30L && !this.warned.containsKey(wkey)) {
               this.warned.put(wkey, System.currentTimeMillis());
               this.warnInterval(m);
            }
         }

         if (untilMs <= 0L) {
            this.plugin.data().setLastIntervalFire(nowMs);
            this.warned.clear();
            this.fireInterval();
         }

         this.warned.entrySet().removeIf(e -> nowMs - e.getValue() > 3600000L);
      } else {
         this.plugin.data().setLastIntervalFire(nowMs);
         this.warned.clear();
      }
   }

   private List<World> resolveWorlds(List<String> slotWorlds) {
      List<World> out = new ArrayList<>();
      if (slotWorlds != null && !slotWorlds.isEmpty()) {
         for (String wn : slotWorlds) {
            World w = Bukkit.getWorld(wn);
            if (w == null) {
               this.plugin.getLogger().warning("World '" + wn + "' is not loaded.");
            } else {
               out.add(w);
            }
         }

         return out;
      } else {
         if (this.plugin.events().isGlobalMode()) {
            out.addAll(Bukkit.getWorlds());
         } else {
            for (String wnx : this.plugin.getConfig().getStringList("worlds")) {
               World w = Bukkit.getWorld(wnx);
               if (w != null) {
                  out.add(w);
               }
            }
         }

         return out;
      }
   }

   private List<World> startable(List<World> worlds) {
      List<World> out = new ArrayList<>();

      for (World w : worlds) {
         if (!this.plugin.events().isActive(w)) {
            if (this.skipEmpty && w.getPlayers().isEmpty()) {
               this.plugin.getLogger().info("Moon skipped in '" + w.getName() + "': no players.");
            } else {
               out.add(w);
            }
         }
      }

      return out;
   }

   private static Sound parseSound(String name, Sound def) {
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

   private void warnInterval(int minutes) {
      double defDiff = this.plugin.getConfig().getDouble("difficulty.default", 1.0);
      boolean showParams = this.plugin.getConfig().getBoolean("warning.show-parameters", false);
      String key = showParams ? "schedule.warning-params" : "schedule.warning";
      String soundName = this.plugin.getConfig().getString("warning.sound", "entity.wither.ambient");
      Sound sfx = parseSound(soundName, Sound.ENTITY_WITHER_AMBIENT);

      for (World w : this.resolveWorlds(List.of())) {
         if (!this.plugin.events().isActive(w) && this.plugin.messages().announce("warnings", false)) {
            this.plugin.messages().world(w, key, "min", String.valueOf(minutes), "difficulty", Util.fmt(defDiff));
            if (sfx != null) {
               for (Player p : w.getPlayers()) {
                  p.playSound(p.getLocation(), sfx, 1.0F, 0.6F);
               }
            }
         }
      }
   }

   private void fireInterval() {
      double defDiff = this.plugin.getConfig().getDouble("difficulty.default", 1.0);
      int defDur = this.plugin.getConfig().getInt("event.duration-minutes", 20);
      List<World> targets = this.startable(this.resolveWorlds(List.of()));
      if (!targets.isEmpty()) {
         if (this.plugin.events().startIn(targets, defDiff, Math.max(1, defDur))) {
            this.plugin.getLogger().info("Interval moon started in " + targets.size() + " world(s) (every " + this.intervalHours + "h)");
         }
      }
   }

   private void fire(ScheduleManager.Slot slot) {
      List<World> targets = this.startable(this.resolveWorlds(slot.worlds()));
      if (!targets.isEmpty()) {
         this.plugin.events().startIn(targets, slot.difficulty(), slot.duration());
      }
   }

   private void warn(ScheduleManager.Slot slot, int minutes) {
      boolean showParams = this.plugin.getConfig().getBoolean("warning.show-parameters", false);
      String key = showParams ? "schedule.warning-params" : "schedule.warning";
      String soundName = this.plugin.getConfig().getString("warning.sound", "entity.wither.ambient");
      Sound sfx = parseSound(soundName, Sound.ENTITY_WITHER_AMBIENT);

      for (World w : this.resolveWorlds(slot.worlds())) {
         if (!this.plugin.events().isActive(w) && this.plugin.messages().announce("warnings", false)) {
            this.plugin.messages().world(w, key, "min", String.valueOf(minutes), "difficulty", Util.fmt(slot.difficulty()));
            if (sfx != null) {
               for (Player p : w.getPlayers()) {
                  p.playSound(p.getLocation(), sfx, 1.0F, 0.6F);
               }
            }
         }
      }
   }

   public List<ScheduleManager.Upcoming> upcoming(int limit) {
      ZonedDateTime now = ZonedDateTime.now(this.zone);
      List<ScheduleManager.Upcoming> out = new ArrayList<>();
      if (this.mode == ScheduleManager.Mode.INTERVAL) {
         Long lastFired = this.plugin.data().getLastIntervalFire();
         long nextFireMs = (lastFired != null ? lastFired : nowMs()) + (long)this.intervalHours * 3600000L;
         long secUntil = Math.max(0L, (nextFireMs - nowMs()) / 1000L);
         ZonedDateTime at = now.plusSeconds(secUntil);
         double defDiff = this.plugin.getConfig().getDouble("difficulty.default", 1.0);
         int defDur = this.plugin.getConfig().getInt("event.duration-minutes", 20);
         List<String> worlds = this.plugin.getConfig().getStringList("worlds");
         out.add(new ScheduleManager.Upcoming(new ScheduleManager.Slot(null, null, defDiff, defDur, worlds), at));
         return out;
      } else {
         for (int off = 0; off <= 7; off++) {
            LocalDate date = now.toLocalDate().plusDays((long)off);

            for (ScheduleManager.Slot s : this.slots) {
               if (s.day() == date.getDayOfWeek()) {
                  ZonedDateTime at = ZonedDateTime.of(date, s.time(), this.zone);
                  if (at.isAfter(now)) {
                     out.add(new ScheduleManager.Upcoming(s, at));
                  }
               }
            }
         }

         out.sort(Comparator.comparing(ScheduleManager.Upcoming::at));
         return (List<ScheduleManager.Upcoming>)(out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out);
      }
   }

   private static long nowMs() {
      return System.currentTimeMillis();
   }

   public long secondsUntil(ScheduleManager.Upcoming u) {
      return Math.max(0L, Duration.between(ZonedDateTime.now(this.zone), u.at()).getSeconds());
   }

   public String format(ScheduleManager.Upcoming u) {
      if (this.mode != ScheduleManager.Mode.INTERVAL && u.slot().day() != null) {
         String day = u.at().getDayOfWeek().getDisplayName(TextStyle.FULL, ES);
         return day + " " + u.at().format(DateTimeFormatter.ofPattern("HH:mm")) + " (" + this.zone.getId() + ")";
      } else {
         return u.at().format(DateTimeFormatter.ofPattern("EEEE HH:mm", ES)) + " (" + this.zone.getId() + ")";
      }
   }

   public int intervalHours() {
      return this.intervalHours;
   }

   public static enum Mode {
      INTERVAL,
      WEEKLY;
   }

   public static record Slot(DayOfWeek day, LocalTime time, double difficulty, int duration, List<String> worlds) {
   }

   public static record Upcoming(ScheduleManager.Slot slot, ZonedDateTime at) {
   }
}
