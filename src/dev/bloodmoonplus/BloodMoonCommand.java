package dev.bloodmoonplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;

public final class BloodMoonCommand implements TabExecutor {
   private static final Map<String, String> ALIASES = new LinkedHashMap<>();
   private static final List<String> USE_SUBS = List.of("next", "help");
   private static final List<String> ADMIN_SUBS = List.of("start", "stop", "blood", "black", "purple", "warn", "countdown", "cancel", "time", "difficulty", "dynamic", "boss", "horde", "killmobs", "lightning", "sound", "evolution", "give", "items", "chances", "reload");
   private static final Map<String, ScarySound> SCARY_SOUNDS = new LinkedHashMap<>();

   private record ScarySound(Sound sound, float pitch, String label) {
   }
   private final BloodMoonPlus plugin;

   BloodMoonCommand(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   private Messages m() {
      return this.plugin.messages();
   }

   public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
      if (args.length == 0) {
         this.status(s);
         return true;
      } else {
         String sub = ALIASES.getOrDefault(args[0].toLowerCase(Locale.ROOT), "");
         if (sub.isEmpty()) {
            this.m().send(s, "cmd.usage");
            return true;
         } else if (!USE_SUBS.contains(sub) && !s.hasPermission("bloodmoon.admin")) {
            this.m().send(s, "no-permission");
            return true;
         } else {
            String[] a = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
            switch (sub) {
               case "next":
                  this.next(s);
                  break;
               case "help":
                  this.help(s);
                  break;
               case "sound":
                  this.sound(s, a);
                  break;
               case "start":
                  this.start(s, a);
                  break;
               case "stop":
                  this.stop(s, a);
                  break;
               case "difficulty":
                  this.difficulty(s, a);
                  break;
               case "boss":
                  this.boss(s, a);
                  break;
               case "horde":
                  this.horde(s, a);
                  break;
               case "killmobs":
                  this.killmobs(s, a);
                  break;
               case "give":
                  this.give(s, a);
                  break;
               case "items":
                  this.m().send(s, "cmd.items-header");

                  for (String id : this.plugin.items().ids()) {
                     ItemFactory.ItemDef d = this.plugin.items().get(id);
                     s.sendMessage(
                        Util.mini(
                           "<dark_gray>- <white>"
                              + id
                              + " <gray>"
                              + this.plugin.loot().rarityDisplay(d.rarity())
                              + (d.limit() > 0 ? " <red>(" + this.plugin.data().created(id) + "/" + d.limit() + ")" : "")
                        )
                     );
                  }
                  break;
               case "chances":
                  if (a.length < 1) {
                     this.m().send(s, "cmd.usage-chances");
                     return true;
                  }

                  List<String> lines = this.plugin.loot().describe(a[0]);
                  if (lines == null) {
                     this.m().send(s, "cmd.table-unknown", "table", a[0]);
                     return true;
                  }

                  for (String l : lines) {
                     s.sendMessage(Util.mini(l));
                  }
                  break;
               case "reload":
                  this.plugin.reloadAll();
                  this.m().send(s, "cmd.reloaded");
                  break;
               case "warn":
                  this.warn(s, a);
                  break;
               case "evolution":
                  if (this.plugin.quitPenalty() != null) {
                     this.plugin.quitPenalty().broadcastEvolutionAlert();
                     s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m001")));
                  }
                  break;
               case "black":
                  this.startSpecial(s, a, MoonType.BLACK);
                  break;
               case "purple":
                  this.startSpecial(s, a, MoonType.PURPLE);
                  break;
               case "blood":
                  this.startSpecial(s, a, MoonType.BLOOD);
                  break;
               case "countdown":
                  this.countdown(s, a);
                  break;
               case "cancel":
                  this.plugin.countdown().cancel(true);
                  s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m002")));
                  break;
               case "lightning":
                  this.lightning(s, a);
                  break;
               case "time":
               case "duration":
                  this.time(s, a);
                  break;
               case "dynamic":
                  this.dynamic(s, a);
                  break;
               default:
                  this.m().send(s, "cmd.usage");
            }

            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
      boolean admin = s.hasPermission("bloodmoon.admin");
      if (args.length == 1) {
         List<String> subs = new ArrayList<>(USE_SUBS);
         if (admin) {
            subs.addAll(ADMIN_SUBS);
         }

         return filter(subs, args[0]);
      } else if (!admin) {
         return List.of();
      } else {
         String sub = ALIASES.getOrDefault(args[0].toLowerCase(Locale.ROOT), "");
         List<String> worlds = Bukkit.getWorlds().stream().<String>map(WorldInfo::getName).toList();
         String cur = args[args.length - 1];
         switch (sub) {
            case "start": {
               List<String> o = new ArrayList<>(worlds);
               o.addAll(List.of("x1", "x2", "x3", "x5", "x10", "15m", "20m", "25m", "30m", "10s", "30s", "60s", "in", "black", "purple", "blood"));
               return filter(o, cur);
            }
            case "countdown": {
               if (args.length == 2) {
                  return filter(List.of("1m", "2m", "3m", "5m", "10m", "15m", "30s", "60s", "120s", "cancel"), cur);
               } else if (args.length == 3) {
                  return filter(List.of("normal", "dangerous", "lethal", "black", "purple", "blood", "x1", "x2", "x3", "x5"), cur);
               } else if (args.length == 4) {
                  List<String> o = new ArrayList<>(List.of("black", "purple", "blood", "normal", "dangerous", "lethal"));
                  o.addAll(worlds);
                  return filter(o, cur);
               } else {
                  return filter(worlds, cur);
               }
            }
            case "warn": {
               if (args.length == 2) {
                  return filter(List.of("10m", "5m", "3m", "2m", "1m", "30s"), cur);
               } else if (args.length == 3) {
                  return filter(List.of("normal", "dangerous", "lethal", "black", "purple", "blood", "start"), cur);
               } else if (args.length == 4) {
                  return filter(List.of("black", "purple", "blood", "start"), cur);
               } else {
                  return filter(List.of("start"), cur);
               }
            }
            case "black":
            case "purple":
            case "blood": {
               List<String> o = new ArrayList<>(worlds);
               o.addAll(List.of("15m", "20m", "25m", "30m", "x1", "x2", "x3", "x5"));
               return filter(o, cur);
            }
            case "stop": {
               List<String> o = new ArrayList<>(worlds);
               o.add("all");
               return filter(o, cur);
            }
            case "difficulty":
               return args.length == 2 ? filter(List.of("x1", "x2", "x3", "x5", "x10", "x50", "x100", "x500", "x1000"), cur) : filter(worlds, cur);
            case "boss":
               if (args.length == 2) {
                  return filter(new ArrayList<>(this.plugin.bosses().ids()), cur);
               } else if (args.length == 3) {
                  List<String> opts = new ArrayList<>(worlds);
                  opts.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                  return filter(opts, cur);
               }
               return List.of();
            case "horde":
               return filter(worlds, cur);
            case "killmobs":
               return filter(worlds, cur);
            case "chances":
               return args.length == 2 ? filter(new ArrayList<>(this.plugin.loot().tableIds()), cur) : List.of();
            case "give":
               if (args.length == 2) {
                  return filter(Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList(), cur);
               } else {
                  if (args.length == 3) {
                     return filter(new ArrayList<>(this.plugin.items().ids()), cur);
                  }

                  return List.of();
               }
            case "lightning": {
               List<String> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().<String>map(Player::getName).toList());
               if (args.length == 2) {
                  return filter(List.of("storm", "smite", "strike", "frequency", "stop", "status", "help"), cur);
               }
               String action = args[1].toLowerCase(Locale.ROOT);
               if (action.equals("frequency") || action.equals("freq") || action.equals("частота")) {
                  if (args.length == 3) {
                     return filter(List.of("storm", "smite"), cur);
                  } else if (args.length == 4) {
                     List<String> opts = new ArrayList<>(players);
                     opts.add("all");
                     return filter(opts, cur);
                  } else if (args.length == 5) {
                     return args[2].equalsIgnoreCase("smite")
                        ? filter(List.of("0.25", "0.5", "1", "2", "5"), cur)
                        : filter(List.of("0.5", "1", "3", "5", "10", "20"), cur);
                  }
                  return List.of();
               }
               if (action.equals("storm") || action.equals("шторм")) {
                  if (args.length == 3) {
                     List<String> opts = new ArrayList<>(players);
                     opts.add("all");
                     opts.add("stop");
                     return filter(opts, cur);
                  } else if (args.length == 4) {
                     return filter(List.of("1", "3", "5", "10", "20"), cur);
                  } else if (args.length == 5) {
                     return filter(List.of("10", "15", "25", "50"), cur);
                  } else if (args.length == 6) {
                     return filter(List.of("30", "60", "120", "300"), cur);
                  }
               } else if (action.equals("smite") || action.equals("кара")) {
                  if (args.length == 3) {
                     List<String> opts = new ArrayList<>(players);
                     opts.add("stop");
                     return filter(opts, cur);
                  } else if (args.length == 4) {
                     return filter(List.of("0.5", "1", "2", "3", "5", "10"), cur);
                  } else if (args.length == 5) {
                     return filter(List.of("1.0", "2.0", "4.0", "10.0"), cur);
                  } else if (args.length == 6) {
                     return filter(List.of("15", "30", "60", "120"), cur);
                  }
               } else if (action.equals("strike") || action.equals("молния")) {
                  if (args.length == 3) {
                     return filter(players, cur);
                  } else if (args.length == 4) {
                     return filter(List.of("0.0", "1.0", "2.0", "4.0"), cur);
                  }
               } else if (action.equals("stop") || action.equals("стоп")) {
                  if (args.length == 3) {
                     List<String> opts = new ArrayList<>(players);
                     opts.add("all");
                     return filter(opts, cur);
                  }
               }
               return List.of();
            }
            case "sound": {
               if (args.length == 2) {
                  List<String> o = new ArrayList<>(SCARY_SOUNDS.keySet());
                  o.add("random");
                  return filter(o, cur);
               } else if (args.length == 3) {
                  List<String> o = new ArrayList<>();
                  o.add("all");
                  o.addAll(worlds);
                  o.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                  return filter(o, cur);
               } else if (args.length == 4) {
                  return filter(List.of("0.5", "1.0", "1.5", "2.0"), cur);
               }
               return List.of();
            }
            case "time":
            case "duration": {
               if (args.length == 2) {
                  return filter(List.of("status", "add", "set", "take", "help"), cur);
               } else if (args.length == 3) {
                  String act = args[1].toLowerCase(Locale.ROOT);
                  if (act.equals("add") || act.equals("set") || act.equals("take") || act.equals("sub")
                      || act.equals("добавить") || act.equals("установить") || act.equals("убавить")) {
                     return filter(List.of("30s", "1m", "2m", "5m", "10m", "15m", "20m", "30m"), cur);
                  }
               } else if (args.length == 4) {
                  List<String> opts = new ArrayList<>(worlds);
                  opts.add("all");
                  return filter(opts, cur);
               }
               return List.of();
            }
            case "dynamic": {
               if (args.length == 2) {
                  return filter(List.of("status", "on", "off", "reset", "help"), cur);
               } else if (args.length == 3 && (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("сброс"))) {
                  List<String> opts = new ArrayList<>(worlds);
                  opts.add("all");
                  return filter(opts, cur);
               }
               return List.of();
            }
            default:
               return List.of();
         }
      }
   }

   private static List<String> filter(List<String> opts, String cur) {
      String c = cur.toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();

      for (String o : opts) {
         if (o.toLowerCase(Locale.ROOT).startsWith(c)) {
            out.add(o);
         }
      }

      return out;
   }

   static Double parseMult(String t) {
      String x = t.toLowerCase(Locale.ROOT);
      if (x.equals("triple")) {
         return 3.0;
      } else {
         if (x.length() > 1 && x.charAt(0) == 'x') {
            try {
               return Double.parseDouble(x.substring(1));
            } catch (NumberFormatException ignored) {
            }
         }
         try {
            return Double.parseDouble(x);
         } catch (NumberFormatException ignored) {
         }
         return null;
      }
   }

   static Integer parseMinutes(String t) {
      String x = t.toLowerCase(Locale.ROOT);
      if (x.length() > 1 && x.endsWith("m")) {
         try {
            return Integer.parseInt(x.substring(0, x.length() - 1));
         } catch (NumberFormatException var3) {
         }
      }

      return null;
   }

   static Integer parseSeconds(String t) {
      String x = t.toLowerCase(Locale.ROOT);
      if (x.endsWith("s") || x.endsWith("sec")) {
         String num = x.replaceAll("[^0-9]", "");
         if (!num.isEmpty()) {
            try {
               return Integer.parseInt(num);
            } catch (NumberFormatException ignored) {
            }
         }
      }
      return null;
   }

   private void startSpecial(CommandSender s, String[] a, MoonType type) {
      List<String> list = new ArrayList<>(Arrays.asList(a));
      list.add(type.getId());
      this.start(s, list.toArray(new String[0]));
   }

   private void start(CommandSender s, String[] a) {
      Integer countdownSec = null;
      Double diff = null;
      Integer minutes = null;
      String worldName = null;
      MoonType moonType = MoonType.BLOOD;
      List<String> knownWorlds = Bukkit.getWorlds().stream().<String>map(WorldInfo::getName).toList();

      for (int i = 0; i < a.length; i++) {
         String t = a[i];
         if (t.equalsIgnoreCase("black") || t.equalsIgnoreCase("черная") || t.equalsIgnoreCase("чёрная")) {
            moonType = MoonType.BLACK;
            continue;
         }
         if (t.equalsIgnoreCase("purple") || t.equalsIgnoreCase("фиолетовая") || t.equalsIgnoreCase("void")) {
            moonType = MoonType.PURPLE;
            continue;
         }
         if (t.equalsIgnoreCase("blood") || t.equalsIgnoreCase("кровавая")) {
            moonType = MoonType.BLOOD;
            continue;
         }
         if (t.equalsIgnoreCase("in") && i + 1 < a.length) {
            try {
               countdownSec = Integer.parseInt(a[i + 1].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
            }
            continue;
         }
         Integer ps = parseSeconds(t);
         if (ps != null) {
            countdownSec = ps;
            continue;
         }
         Double d = parseMult(t);
         Integer mi = parseMinutes(t);
         if (d != null && !t.endsWith("s") && !t.endsWith("m")) {
            diff = d;
         } else if (mi != null) {
            minutes = mi;
         } else if (knownWorlds.contains(t)) {
            worldName = t;
         }
      }

      double dv = diff != null ? diff : this.plugin.getConfig().getDouble("difficulty.default", 1.0);
      int mv = minutes != null ? minutes : this.plugin.getConfig().getInt("event.duration-minutes", 20);

      if (countdownSec != null && countdownSec > 0) {
         List<World> targets = worldName != null
            ? List.of(Bukkit.getWorld(worldName))
            : this.plugin.events().defaultWorlds();
         this.plugin.countdown().start(countdownSec, dv, Math.max(1, mv), targets, s, CountdownManager.WarningType.NORMAL, moonType);
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m003", "display-name", String.valueOf(moonType.getDisplayName()), "countdown-sec", String.valueOf(countdownSec))));
         return;
      }

      String startedLabel;
      if (worldName != null) {
         World w = Bukkit.getWorld(worldName);
         if (w == null) {
            this.m().send(s, "unknown-world");
            return;
         }

         if (!this.plugin.events().start(w, dv, Math.max(1, mv), moonType)) {
            this.m().send(s, "event.already-active");
            return;
         }

         startedLabel = w.getName();
      } else {
         List<World> targets = this.plugin.events().defaultWorlds();
         if (targets.isEmpty()) {
            this.m().send(s, "unknown-world");
            return;
         }

         if (!this.plugin.events().startIn(targets, dv, Math.max(1, mv), moonType)) {
            this.m().send(s, "event.already-active");
            return;
         }

         startedLabel = targets.size() == 1 ? targets.get(0).getName() : "global";
      }

      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m004", "display-name", String.valueOf(moonType.getDisplayName()), "started-label", String.valueOf(startedLabel))));
   }

   private void countdown(CommandSender s, String[] a) {
      if (a.length > 0 && (a[0].equalsIgnoreCase("cancel") || a[0].equalsIgnoreCase("stop") || a[0].equalsIgnoreCase("abort"))) {
         this.plugin.countdown().cancel(true);
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m005")));
         return;
      }
      Integer sec = null;
      Double diff = null;
      Integer minutes = null;
      String worldName = null;
      MoonType moonType = MoonType.BLOOD;
      CountdownManager.WarningType warnType = CountdownManager.WarningType.NORMAL;
      boolean diffExplicit = false;
      List<String> knownWorlds = Bukkit.getWorlds().stream().<String>map(WorldInfo::getName).toList();

      for (int i = 0; i < a.length; i++) {
         String t = a[i];
         if (t.equalsIgnoreCase("black") || t.equalsIgnoreCase("черная") || t.equalsIgnoreCase("чёрная")) {
            moonType = MoonType.BLACK;
            continue;
         }
         if (t.equalsIgnoreCase("purple") || t.equalsIgnoreCase("фиолетовая") || t.equalsIgnoreCase("void")) {
            moonType = MoonType.PURPLE;
            continue;
         }
         if (t.equalsIgnoreCase("blood") || t.equalsIgnoreCase("кровавая")) {
            moonType = MoonType.BLOOD;
            continue;
         }
         if (t.equalsIgnoreCase("lethal") || t.equalsIgnoreCase("смертельная") || t.equalsIgnoreCase("deadly")) {
            warnType = CountdownManager.WarningType.LETHAL;
            continue;
         }
         if (t.equalsIgnoreCase("dangerous") || t.equalsIgnoreCase("опасная") || t.equalsIgnoreCase("danger")) {
            warnType = CountdownManager.WarningType.DANGEROUS;
            continue;
         }
         if (t.equalsIgnoreCase("normal") || t.equalsIgnoreCase("обычная")) {
            warnType = CountdownManager.WarningType.NORMAL;
            continue;
         }
         if (t.equalsIgnoreCase("in") && i + 1 < a.length) {
            Long dIn = parseDurationMs(a[i + 1]);
            if (dIn != null) {
               sec = (int) (dIn / 1000L);
            }
            i++;
            continue;
         }
         if (t.startsWith("x") || t.startsWith("X")) {
            Double d = parseMult(t);
            if (d != null) {
               diff = d;
               diffExplicit = true;
               continue;
            }
         }
         if (t.equalsIgnoreCase("dur") || t.equalsIgnoreCase("duration") || t.equalsIgnoreCase("длительность")) {
            if (i + 1 < a.length) {
               Long dDur = parseDurationMs(a[i + 1]);
               if (dDur != null) {
                  minutes = Math.max(1, (int) (dDur / 60000L));
               }
               i++;
               continue;
            }
         }
         Long durMs = parseDurationMs(t);
         if (durMs != null && durMs > 0 && sec == null) {
            sec = (int) (durMs / 1000L);
            continue;
         }
         Double d = parseMult(t);
         if (d != null && !t.endsWith("m") && !t.endsWith("s")) {
            diff = d;
            diffExplicit = true;
         } else if (knownWorlds.contains(t)) {
            worldName = t;
         }
      }

      int finalSec = sec != null ? sec : 30;
      double dv = diffExplicit && diff != null ? diff : this.plugin.countdown().getDefaultDifficulty(warnType);
      int mv = minutes != null ? minutes : this.plugin.getConfig().getInt("event.duration-minutes", 20);
      List<World> targets = worldName != null ? List.of(Bukkit.getWorld(worldName)) : this.plugin.events().defaultWorlds();

      this.plugin.countdown().start(finalSec, dv, Math.max(1, mv), targets, s, warnType, moonType);
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m006", "display-name", String.valueOf(moonType.getDisplayName()), "final-sec", String.valueOf(this.plugin.countdown().formatTime(finalSec)), "difficulty", String.valueOf(Util.fmt(dv)), "name", String.valueOf(warnType.name()))));
   }

   private void warn(CommandSender s, String[] a) {
      if (a.length == 0) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m007")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m008")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m009")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m010")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m011")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m012")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m013")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m014")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m015")));
         return;
      }

      int sec = 600;
      CountdownManager.WarningType warnType = CountdownManager.WarningType.NORMAL;
      MoonType moonType = MoonType.BLOOD;
      boolean shouldStart = false;

      for (String t : a) {
         if (t.equalsIgnoreCase("start") || t.equalsIgnoreCase("старт") || t.equalsIgnoreCase("--start")) {
            shouldStart = true;
            continue;
         }
         if (t.equalsIgnoreCase("black") || t.equalsIgnoreCase("черная") || t.equalsIgnoreCase("чёрная")) {
            moonType = MoonType.BLACK;
            continue;
         }
         if (t.equalsIgnoreCase("purple") || t.equalsIgnoreCase("фиолетовая")) {
            moonType = MoonType.PURPLE;
            continue;
         }
         if (t.equalsIgnoreCase("blood") || t.equalsIgnoreCase("кровавая")) {
            moonType = MoonType.BLOOD;
            continue;
         }
         if (t.equalsIgnoreCase("lethal") || t.equalsIgnoreCase("смертельная") || t.equalsIgnoreCase("deadly")) {
            warnType = CountdownManager.WarningType.LETHAL;
            continue;
         }
         if (t.equalsIgnoreCase("dangerous") || t.equalsIgnoreCase("опасная") || t.equalsIgnoreCase("danger")) {
            warnType = CountdownManager.WarningType.DANGEROUS;
            continue;
         }
         if (t.equalsIgnoreCase("normal") || t.equalsIgnoreCase("обычная")) {
            warnType = CountdownManager.WarningType.NORMAL;
            continue;
         }
         Long dur = parseDurationMs(t);
         if (dur != null) {
            sec = (int)(dur / 1000L);
         }
      }

      if (shouldStart) {
         double dv = this.plugin.countdown().getDefaultDifficulty(warnType);
         int mv = this.plugin.getConfig().getInt("event.duration-minutes", 20);
         this.plugin.countdown().start(sec, dv, mv, this.plugin.events().defaultWorlds(), s, warnType, moonType);
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m016", "display-name", String.valueOf(moonType.getDisplayName()), "sec", String.valueOf(this.plugin.countdown().formatTime(sec)))));
      } else {
         this.plugin.countdown().broadcastTypeWarning(warnType, sec, moonType);
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m017", "name", String.valueOf(warnType.name()), "display-name", String.valueOf(moonType.getDisplayName()), "sec", String.valueOf(this.plugin.countdown().formatTime(sec)))));
      }
   }

   private void stop(CommandSender s, String[] a) {
      if (a.length > 0 && a[0].equalsIgnoreCase("all")) {
         if (!this.plugin.events().stop(null, false)) {
            this.m().send(s, "event.not-active");
         } else {
            this.m().send(s, "cmd.stopped-all");
         }
      } else if (a.length > 0) {
         World w = Bukkit.getWorld(a[0]);
         if (w == null) {
            this.m().send(s, "unknown-world");
         } else if (!this.plugin.events().stop(w, false)) {
            this.m().send(s, "event.not-active");
         } else {
            this.m().send(s, "cmd.stopped", "world", w.getName());
         }
      } else if (!this.plugin.events().stop(null, false)) {
         this.m().send(s, "event.not-active");
      } else {
         this.m().send(s, "cmd.stopped-all");
      }
   }

   private void difficulty(CommandSender s, String[] a) {
      if (a.length < 1) {
         this.m().send(s, "cmd.usage-difficulty");
      } else {
         Double d = parseMult(a[0]);
         if (d == null) {
            this.m().send(s, "cmd.usage-difficulty");
         } else {
            Collection<EventManager.Session> all = this.plugin.events().sessions();
            if (all.isEmpty()) {
               this.m().send(s, "event.not-active");
            } else {
               double v = Math.max(0.1, Math.min(this.plugin.getConfig().getDouble("difficulty.max", 1000.0), d));

               for (EventManager.Session ses : all) {
                  ses.difficulty = v;
               }

               this.m().send(s, "cmd.difficulty-set", "difficulty", Util.fmt(v));
            }
         }
      }
   }

   private void boss(CommandSender s, String[] a) {
      if (a.length < 1) {
         this.m().send(s, "cmd.usage-boss", "bosses", String.join(", ", this.plugin.bosses().ids()));
         return;
      }

      String bossId = a[0];
      if (!this.plugin.bosses().ids().contains(bossId)) {
         this.m().send(s, "cmd.boss-failed", "boss", bossId);
         return;
      }

      Collection<EventManager.Session> all = this.plugin.events().sessions();
      if (!all.isEmpty() && a.length < 2) {
         int spawned = 0;
         for (EventManager.Session ses : all) {
            if (this.plugin.bosses().spawn(ses, bossId)) {
               spawned++;
            }
         }

         if (spawned > 0) {
            this.m().send(s, "cmd.boss-spawned", "boss", bossId);
         } else {
            this.m().send(s, "cmd.boss-failed", "boss", bossId);
         }
         return;
      }

      World targetWorld = null;
      Player targetPlayer = null;
      Location spawnLoc = null;

      if (a.length > 1) {
         Player pArg = Bukkit.getPlayer(a[1]);
         if (pArg != null) {
            targetPlayer = pArg;
            targetWorld = pArg.getWorld();
            spawnLoc = this.plugin.events().findSpawn(pArg.getLocation(), 8.0, 16.0);
            if (spawnLoc == null) {
               spawnLoc = pArg.getLocation().add(0.0, 1.0, 0.0);
            }
         } else {
            World wArg = Bukkit.getWorld(a[1]);
            if (wArg != null) {
               targetWorld = wArg;
            }
         }
      }

      if (targetWorld == null) {
         if (s instanceof Player p) {
            targetWorld = p.getWorld();
            targetPlayer = p;
            spawnLoc = this.plugin.events().findSpawn(p.getLocation(), 8.0, 16.0);
            if (spawnLoc == null) {
               spawnLoc = p.getLocation().add(0.0, 1.0, 0.0);
            }
         } else {
            targetWorld = Bukkit.getWorlds().get(0);
         }
      }

      if (spawnLoc == null) {
         List<Player> inWorld = targetWorld.getPlayers().stream()
            .filter(pl -> pl.getGameMode() == GameMode.SURVIVAL || pl.getGameMode() == GameMode.ADVENTURE)
            .toList();
         if (!inWorld.isEmpty()) {
            targetPlayer = inWorld.get(0);
            spawnLoc = this.plugin.events().findSpawn(targetPlayer.getLocation(), 8.0, 16.0);
            if (spawnLoc == null) {
               spawnLoc = targetPlayer.getLocation().add(0.0, 1.0, 0.0);
            }
         } else {
            spawnLoc = targetWorld.getSpawnLocation().add(0.0, 1.0, 0.0);
         }
      }

      if (this.plugin.bosses().spawnAt(spawnLoc, bossId, 1.0, targetPlayer)) {
         this.m().send(s, "cmd.boss-spawned", "boss", bossId);
      } else {
         this.m().send(s, "cmd.boss-failed", "boss", bossId);
      }
   }

   private void horde(CommandSender s, String[] a) {
      Collection<EventManager.Session> all = this.plugin.events().sessions();
      if (all.isEmpty()) {
         this.m().send(s, "event.not-active");
      } else {
         int total = 0;

         for (EventManager.Session ses : all) {
            total += this.plugin.events().spawnWave(ses);
         }

         this.m().send(s, "cmd.horde-spawned", "count", String.valueOf(total));
      }
   }

   private void killmobs(CommandSender s, String[] a) {
      int removed = 0;

      for (World w : Bukkit.getWorlds()) {
         removed += this.plugin.bosses().removeAll(w);
         removed += this.plugin.events().removeTagged(w);
      }

      this.m().send(s, "cmd.killed", "count", String.valueOf(removed));
   }

   private void give(CommandSender s, String[] a) {
      if (a.length < 2) {
         this.m().send(s, "cmd.usage-give");
      } else {
         Player p = Bukkit.getPlayerExact(a[0]);
         if (p == null) {
            this.m().send(s, "cmd.player-unknown", "player", a[0]);
         } else {
            ItemFactory.ItemDef d = this.plugin.items().get(a[1]);
            if (d == null) {
               this.m().send(s, "cmd.item-unknown", "item", a[1]);
            } else {
               int n = 1;
               if (a.length > 2) {
                  try {
                     n = Math.max(1, Math.min(64, Integer.parseInt(a[2])));
                  } catch (NumberFormatException var12) {
                  }
               }

               int serial = 0;
               List<ItemStack> created = new ArrayList<>();
               long deadline = System.currentTimeMillis() + 2000L;

               while (System.currentTimeMillis() < deadline) {
                  ItemStack ci = this.plugin.items().create(d, 1, p.getName());
                  if (ci == null) {
                     break;
                  }

                  created.add(ci);
                  if (++serial >= n) {
                     break;
                  }
               }

               if (created.isEmpty()) {
                  this.m().send(s, "cmd.item-unknown", "item", a[1]);
               } else {
                  for (ItemStack it : created) {
                     this.plugin.loot().giveOrDrop(p, it);
                  }

                  this.m().send(s, "cmd.given", "player", p.getName(), "item", a[1], "amount", String.valueOf(created.size()));
               }
            }
         }
      }
   }

   private void status(CommandSender s) {
      EventManager.Session ses = this.plugin.events().globalSession();
      if (ses == null) {
         this.m().send(s, "cmd.status-none");
         List<ScheduleManager.Upcoming> up = this.plugin.schedule().upcoming(1);
         if (!up.isEmpty()) {
            ScheduleManager.Upcoming u = up.get(0);
            this.m()
               .send(
                  s,
                  "cmd.next-line",
                  "when",
                  this.plugin.schedule().format(u),
                  "in",
                  Util.human(this.plugin.schedule().secondsUntil(u)),
                  "difficulty",
                  Util.fmt(u.slot().difficulty())
               );
         }
      } else {
         String worldLabel = this.plugin.events().activeWorlds() > 1 ? "global" : ses.world.getName();
         this.m()
            .send(
               s,
               "cmd.status-line",
               "world",
               worldLabel,
               "time",
               Util.dur((ses.endMs - System.currentTimeMillis()) / 1000L),
               "difficulty",
               Util.fmt(ses.difficulty)
            );
         List<ScheduleManager.Upcoming> up = this.plugin.schedule().upcoming(1);
         if (!up.isEmpty()) {
            ScheduleManager.Upcoming u = up.get(0);
            this.m()
               .send(
                  s,
                  "cmd.next-line",
                  "when",
                  this.plugin.schedule().format(u),
                  "in",
                  Util.human(this.plugin.schedule().secondsUntil(u)),
                  "difficulty",
                  Util.fmt(u.slot().difficulty())
               );
         }
      }
   }

   private void next(CommandSender s) {
      List<ScheduleManager.Upcoming> up = this.plugin.schedule().upcoming(6);
      if (up.isEmpty()) {
         this.m().send(s, "cmd.next-none");
      } else {
         this.m().send(s, "cmd.next-header");

         for (ScheduleManager.Upcoming u : up) {
            s.sendMessage(
               Util.mini(
                  this.m()
                     .fill(
                        this.m().raw("cmd.next-entry"),
                        "when",
                        this.plugin.schedule().format(u),
                        "in",
                        Util.human(this.plugin.schedule().secondsUntil(u)),
                        "difficulty",
                        Util.fmt(u.slot().difficulty())
                     )
               )
            );
         }
      }
   }

   private void lightning(CommandSender s, String[] a) {
      if (a.length == 0 || a[0].equalsIgnoreCase("help") || a[0].equalsIgnoreCase("помощь")) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m018")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m019")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m020")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m021")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m022")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m023")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m024")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m025")));
         return;
      }

      String action = a[0].toLowerCase(Locale.ROOT);
      LightningEffectManager lm = this.plugin.lightning();
      if (lm == null) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m026")));
         return;
      }

      switch (action) {
         case "storm":
         case "шторм": {
            if (a.length > 1 && a[1].equalsIgnoreCase("stop")) {
               lm.stopStorm(LightningEffectManager.ALL_PLAYERS);
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m027")));
               return;
            }

            UUID targetId = LightningEffectManager.ALL_PLAYERS;
            String targetName = this.plugin.locales().get("text.command.all-players");

            int argIdx = 1;
            if (a.length > 1) {
               if (a[1].equalsIgnoreCase("all") || a[1].equalsIgnoreCase("все")) {
                  targetId = LightningEffectManager.ALL_PLAYERS;
                  targetName = this.plugin.locales().get("text.command.all-players");
                  argIdx = 2;
               } else {
                  Player p = Bukkit.getPlayerExact(a[1]);
                  if (p != null) {
                     targetId = p.getUniqueId();
                     targetName = p.getName();
                     argIdx = 2;
                  } else if (!isDouble(a[1]) && !isInteger(a[1])) {
                     s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m028", "input", String.valueOf(a[1]))));
                     return;
                  }
               }
            }

            double rate = 3.0;
            if (a.length > argIdx) {
               try { rate = lm.clampStormRate(Double.parseDouble(a[argIdx])); } catch (NumberFormatException ignored) {}
               argIdx++;
            }

            double radius = 15.0;
            if (a.length > argIdx) {
               try { radius = Math.max(1.0, Double.parseDouble(a[argIdx])); } catch (NumberFormatException ignored) {}
               argIdx++;
            }

            long duration = 0L;
            if (a.length > argIdx) {
               try { duration = Math.max(0L, Long.parseLong(a[argIdx])); } catch (NumberFormatException ignored) {}
            }

            lm.startStorm(targetId, rate, radius, duration);
            String durStr = duration > 0 ? this.plugin.locales().get("text.command.for-seconds", "seconds", String.valueOf(duration)) : this.plugin.locales().get("text.command.permanent");
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m029", "target-name", String.valueOf(targetName), "rate", String.valueOf(rate), "radius", String.valueOf(radius), "dur-str", String.valueOf(durStr))));
            break;
         }
         case "smite":
         case "кара": {
            if (a.length < 2) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m030")));
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m031")));
               return;
            }

            if (a.length > 2 && a[2].equalsIgnoreCase("stop")) {
               Player p = Bukkit.getPlayerExact(a[1]);
               if (p != null) {
                  lm.stopSmite(p.getUniqueId());
                  s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m032", "player", String.valueOf(p.getName()))));
               }
               return;
            }

            Player p = Bukkit.getPlayerExact(a[1]);
            if (p == null) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m033", "input", String.valueOf(a[1]))));
               return;
            }

            double interval = 2.0;
            if (a.length > 2) {
               try { interval = Math.max(LightningEffectManager.MIN_SMITE_INTERVAL, Double.parseDouble(a[2])); } catch (NumberFormatException ignored) {}
            }

            double damage = 1.0;
            if (a.length > 3) {
               try { damage = Math.max(0.0, Double.parseDouble(a[3])); } catch (NumberFormatException ignored) {}
            }

            long duration = 0L;
            if (a.length > 4) {
               try { duration = Math.max(0L, Long.parseLong(a[4])); } catch (NumberFormatException ignored) {}
            }

            lm.startSmite(p.getUniqueId(), damage, interval, duration);
            double hearts = damage / 2.0;
            String durStr = duration > 0 ? this.plugin.locales().get("text.command.for-seconds", "seconds", String.valueOf(duration)) : this.plugin.locales().get("text.command.until-stopped");
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m034", "player", String.valueOf(p.getName()), "interval", String.valueOf(interval), "damage", String.valueOf(damage), "hearts", String.valueOf(hearts), "dur-str", String.valueOf(durStr))));
            break;
         }
         case "strike":
         case "молния": {
            if (a.length < 2) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m035")));
               return;
            }

            Player p = Bukkit.getPlayerExact(a[1]);
            if (p == null) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m036", "input", String.valueOf(a[1]))));
               return;
            }

            double damage = 1.0;
            if (a.length > 2) {
               try { damage = Math.max(0.0, Double.parseDouble(a[2])); } catch (NumberFormatException ignored) {}
            }

            lm.singleStrike(p, damage);
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m037", "player", String.valueOf(p.getName()), "damage", String.valueOf(damage))));
            break;
         }
         case "stop":
         case "стоп": {
            if (a.length > 1 && !a[1].equalsIgnoreCase("all") && !a[1].equalsIgnoreCase("все")) {
               Player p = Bukkit.getPlayerExact(a[1]);
               if (p != null) {
                  lm.stopAll(p.getUniqueId());
                  s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m038", "player", String.valueOf(p.getName()))));
               } else {
                  s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m039", "input", String.valueOf(a[1]))));
               }
            } else {
               lm.stopAllGlobal();
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m040")));
            }
            break;
         }
         case "frequency":
         case "freq":
         case "частота": {
            if (a.length < 4 || (!a[1].equalsIgnoreCase("storm") && !a[1].equalsIgnoreCase("smite"))) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m041")));
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m042")));
               return;
            }
            UUID target = null;
            String targetName = this.plugin.locales().get("text.command.everyone");
            if (!a[2].equalsIgnoreCase("all")) {
               Player p = Bukkit.getPlayerExact(a[2]);
               if (p == null) {
                  s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m043", "input", String.valueOf(a[2]))));
                  return;
               }
               target = p.getUniqueId();
               targetName = p.getName();
            }
            double value;
            try {
               value = Double.parseDouble(a[3].replace(',', '.'));
            } catch (NumberFormatException ex) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m044", "input", String.valueOf(a[3]))));
               return;
            }
            boolean storm = a[1].equalsIgnoreCase("storm");
            int changed = storm ? lm.setStormRate(target, value) : lm.setSmiteInterval(target, value);
            if (changed == 0) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m045", "target-name", String.valueOf(targetName), "type", String.valueOf((storm ? "storm" : "smite")))));
            } else if (storm) {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m046", "target-name", String.valueOf(targetName), "value", String.valueOf(lm.clampStormRate(value)), "changed", String.valueOf(changed))));
            } else {
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m047", "target-name", String.valueOf(targetName), "value", String.valueOf(Math.max(LightningEffectManager.MIN_SMITE_INTERVAL, value)), "changed", String.valueOf(changed))));
            }
            break;
         }
         case "status":
         case "статус": {
            int stormCount = lm.getStorms().size();
            int smiteCount = lm.getSmites().size();
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m048")));
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m049", "storm-count", String.valueOf(stormCount))));
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m050", "smite-count", String.valueOf(smiteCount))));
            break;
         }
         default:
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m051")));
      }
   }

   private void help(CommandSender s) {
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m052")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m053")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m054")));
      if (!s.hasPermission("bloodmoon.admin")) {
         return;
      }
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m055")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m056")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m057")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m058")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m059")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m060")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m061")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m062")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m063")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m064")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m065")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m066")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m067")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m068")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m069")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m070")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m071")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m072")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m073")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m074")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m075")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m076")));
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m077")));
   }

   private void sound(CommandSender s, String[] a) {
      if (a.length == 0) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m078")));
         StringBuilder sb = new StringBuilder("<gray>" + this.plugin.locales().get("text.command.sounds-header"));
         for (Map.Entry<String, ScarySound> en : SCARY_SOUNDS.entrySet()) {
            sb.append(" <white>").append(en.getKey()).append("</white> (").append(this.plugin.locales().get("text.sound." + en.getValue().label())).append(")");
         }
         s.sendMessage(Util.mini(sb.append("</gray>").toString()));
         return;
      }
      String key = a[0].toLowerCase(Locale.ROOT);
      ScarySound snd;
      if (key.equals("random")) {
         List<ScarySound> all = new ArrayList<>(SCARY_SOUNDS.values());
         snd = all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
      } else {
         snd = SCARY_SOUNDS.get(key);
      }
      if (snd == null) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m079", "input", String.valueOf(a[0]))));
         return;
      }
      List<Player> targets = new ArrayList<>();
      String targetName = this.plugin.locales().get("text.command.everyone");
      if (a.length < 2 || a[1].equalsIgnoreCase("all")) {
         targets.addAll(Bukkit.getOnlinePlayers());
      } else {
         World w = Bukkit.getWorld(a[1]);
         Player p = Bukkit.getPlayerExact(a[1]);
         if (w != null) {
            targets.addAll(w.getPlayers());
            targetName = this.plugin.locales().get("text.command.world-target", "world", w.getName());
         } else if (p != null) {
            targets.add(p);
            targetName = p.getName();
         } else {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m080", "input", String.valueOf(a[1]))));
            return;
         }
      }
      float volume = 1.0F;
      if (a.length > 2) {
         try { volume = (float)Math.max(0.1, Math.min(4.0, Double.parseDouble(a[2].replace(',', '.')))); } catch (NumberFormatException ignored) {}
      }
      for (Player p : targets) {
         p.playSound(p.getLocation(), snd.sound(), SoundCategory.MASTER, volume, snd.pitch());
      }
      s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m081", "label", String.valueOf(this.plugin.locales().get("text.sound." + snd.label())), "target-name", String.valueOf(targetName), "size", String.valueOf(targets.size()))));
   }

   private void time(CommandSender s, String[] a) {
      if (a.length == 0 || a[0].equalsIgnoreCase("status") || a[0].equalsIgnoreCase("статус")) {
         Collection<EventManager.Session> sessions = this.plugin.events().sessions();
         if (sessions.isEmpty()) {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m082")));
            return;
         }
         long now = System.currentTimeMillis();
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m083")));
         for (EventManager.Session ses : sessions) {
            long remMs = Math.max(0L, ses.endMs - now);
            long totalSec = remMs / 1000L;
            long m = totalSec / 60L;
            long sec = totalSec % 60L;
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m084", "name", String.valueOf(ses.world.getName()), "min", String.valueOf(m), "sec", String.valueOf(sec), "difficulty", String.valueOf(String.format(Locale.ROOT, "%.2f", ses.difficulty)))));
         }
         return;
      }

      String action = a[0].toLowerCase(Locale.ROOT);
      if (action.equals("help") || action.equals("помощь") || action.equals("?")) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m085")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m086")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m087")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m088")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m089")));
         return;
      }

      if (a.length < 2) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m090")));
         return;
      }

      Long durMs = parseDurationMs(a[1]);
      if (durMs == null || durMs <= 0L) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m091")));
         return;
      }

      World targetWorld = null;
      if (a.length >= 3 && !a[2].equalsIgnoreCase("all") && !a[2].equalsIgnoreCase("все")) {
         targetWorld = Bukkit.getWorld(a[2]);
         if (targetWorld == null) {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m092", "input", String.valueOf(a[2]))));
            return;
         }
      }

      if (action.equals("add") || action.equals("добавить") || action.equals("+")) {
         int count = this.plugin.events().adjustDuration(targetWorld, durMs);
         if (count == 0) {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m093")));
         } else {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m094", "duration", String.valueOf(formatDuration(durMs)), "count", String.valueOf(count))));
         }
      } else if (action.equals("take") || action.equals("sub") || action.equals("убавить") || action.equals("отнять") || action.equals("-")) {
         int count = this.plugin.events().adjustDuration(targetWorld, -durMs);
         if (count == 0) {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m095")));
         } else {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m096", "duration", String.valueOf(formatDuration(durMs)), "count", String.valueOf(count))));
         }
      } else if (action.equals("set") || action.equals("установить") || action.equals("задать")) {
         int count = this.plugin.events().setDurationRemaining(targetWorld, durMs);
         if (count == 0) {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m097")));
         } else {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m098", "duration", String.valueOf(formatDuration(durMs)), "count", String.valueOf(count))));
         }
      } else {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m099")));
      }
   }

   private void dynamic(CommandSender s, String[] a) {
      DynamicDifficultyManager dm = this.plugin.dynamicDifficulty();
      if (dm == null) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m100")));
         return;
      }

      if (a.length == 0 || a[0].equalsIgnoreCase("status") || a[0].equalsIgnoreCase("статус")) {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m101")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m102", "is-enabled", String.valueOf(dm.isEnabled()))));
         Collection<EventManager.Session> sessions = this.plugin.events().sessions();
         if (sessions.isEmpty()) {
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m103")));
         } else {
            for (EventManager.Session ses : sessions) {
               double base = dm.getBaseDifficulty(ses.world.getName());
               s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m104", "name", String.valueOf(ses.world.getName()), "difficulty", String.valueOf(String.format(Locale.ROOT, "%.2f", ses.difficulty)), "base", String.valueOf(String.format(Locale.ROOT, "%.2f", base)))));
            }
         }
         return;
      }

      String action = a[0].toLowerCase(Locale.ROOT);
      if (action.equals("on") || action.equals("enable") || action.equals("вкл")) {
         dm.setEnabled(true);
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m105")));
      } else if (action.equals("off") || action.equals("disable") || action.equals("выкл")) {
         dm.setEnabled(false);
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m106")));
      } else if (action.equals("reset") || action.equals("сброс")) {
         if (a.length >= 2 && !a[1].equalsIgnoreCase("all")) {
            dm.resetDifficulty(a[1]);
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m107", "input", String.valueOf(a[1]))));
         } else {
            for (EventManager.Session ses : this.plugin.events().sessions()) {
               dm.resetDifficulty(ses.world.getName());
            }
            s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m108")));
         }
      } else {
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m109")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m110")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m111")));
         s.sendMessage(Util.mini(this.plugin.locales().get("text.command.m112")));
      }
   }

   private static Long parseDurationMs(String input) {
      if (input == null || input.trim().isEmpty()) return null;
      String str = input.trim().toLowerCase(Locale.ROOT);
      long multiplier = 1000L;
      if (str.endsWith("ms") || str.endsWith("мс")) {
         str = str.substring(0, str.length() - 2);
         multiplier = 1L;
      } else if (str.endsWith("s") || str.endsWith("с") || str.endsWith("sec")) {
         str = str.replaceAll("[^0-9.]", "");
         multiplier = 1000L;
      } else if (str.endsWith("m") || str.endsWith("м") || str.endsWith("min")) {
         str = str.replaceAll("[^0-9.]", "");
         multiplier = 60000L;
      } else if (str.endsWith("h") || str.endsWith("ч") || str.endsWith("hour")) {
         str = str.replaceAll("[^0-9.]", "");
         multiplier = 3600000L;
      } else {
         str = str.replaceAll("[^0-9.]", "");
         multiplier = 1000L;
      }
      try {
         double val = Double.parseDouble(str);
         return (long)(val * multiplier);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private String formatDuration(long ms) {
      long totalSec = Math.max(0L, ms / 1000L);
      long m = totalSec / 60L;
      long s = totalSec % 60L;
      if (m > 0 && s > 0) return this.plugin.locales().get("time.short-min-sec", "min", String.valueOf(m), "sec", String.valueOf(s));
      if (m > 0) return this.plugin.locales().get("time.short-min", "min", String.valueOf(m));
      return this.plugin.locales().get("time.short-sec", "sec", String.valueOf(s));
   }

   private static boolean isDouble(String str) {
      try { Double.parseDouble(str); return true; } catch (NumberFormatException e) { return false; }
   }

   private static boolean isInteger(String str) {
      try { Integer.parseInt(str); return true; } catch (NumberFormatException e) { return false; }
   }

   static {
      for (String s : new String[]{"next", "start", "stop", "difficulty", "boss", "horde", "killmobs", "give", "items", "chances", "reload", "warn", "evolution", "countdown", "cancel", "lightning", "time", "duration", "dynamic", "black", "purple", "blood"}) {
         ALIASES.put(s, s);
      }

      ALIASES.put("proxima", "next");
      ALIASES.put("siguiente", "next");
      ALIASES.put("iniciar", "start");
      ALIASES.put("detener", "stop");
      ALIASES.put("parar", "stop");
      ALIASES.put("dificultad", "difficulty");
      ALIASES.put("jefe", "boss");
      ALIASES.put("horda", "horde");
      ALIASES.put("limpiar", "killmobs");
      ALIASES.put("dar", "give");
      ALIASES.put("probabilidades", "chances");
      ALIASES.put("recargar", "reload");
      ALIASES.put("предупредить", "warn");
      ALIASES.put("оповестить", "warn");
      ALIASES.put("эволюция", "evolution");
      ALIASES.put("отсчет", "countdown");
      ALIASES.put("таймер", "time");
      ALIASES.put("отмена", "cancel");
      ALIASES.put("abort", "cancel");
      ALIASES.put("молнии", "lightning");
      ALIASES.put("гроза", "lightning");
      ALIASES.put("шторм", "lightning");
      ALIASES.put("время", "time");
      ALIASES.put("длительность", "duration");
      ALIASES.put("динамика", "dynamic");
      ALIASES.put("черная", "black");
      ALIASES.put("чёрная", "black");
      ALIASES.put("фиолетовая", "purple");
      ALIASES.put("кровавая", "blood");
      ALIASES.put("help", "help");
      ALIASES.put("помощь", "help");
      ALIASES.put("?", "help");
      ALIASES.put("sound", "sound");
      ALIASES.put("звук", "sound");
      SCARY_SOUNDS.put("wither", new ScarySound(Sound.ENTITY_WITHER_SPAWN, 0.6F, "wither"));
      SCARY_SOUNDS.put("dragon", new ScarySound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5F, "dragon"));
      SCARY_SOUNDS.put("warden", new ScarySound(Sound.ENTITY_WARDEN_EMERGE, 0.8F, "warden"));
      SCARY_SOUNDS.put("roar", new ScarySound(Sound.ENTITY_WARDEN_ROAR, 0.7F, "roar"));
      SCARY_SOUNDS.put("heartbeat", new ScarySound(Sound.ENTITY_WARDEN_HEARTBEAT, 0.6F, "heartbeat"));
      SCARY_SOUNDS.put("sonic", new ScarySound(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6F, "sonic"));
      SCARY_SOUNDS.put("curse", new ScarySound(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7F, "curse"));
      SCARY_SOUNDS.put("ghast", new ScarySound(Sound.ENTITY_GHAST_SCREAM, 0.5F, "ghast"));
      SCARY_SOUNDS.put("scream", new ScarySound(Sound.ENTITY_ENDERMAN_SCREAM, 0.5F, "scream"));
      SCARY_SOUNDS.put("cave", new ScarySound(Sound.AMBIENT_CAVE, 0.8F, "cave"));
      SCARY_SOUNDS.put("horn", new ScarySound(Sound.EVENT_RAID_HORN, 0.7F, "horn"));
      SCARY_SOUNDS.put("bell", new ScarySound(Sound.BLOCK_BELL_RESONATE, 0.5F, "bell"));
      SCARY_SOUNDS.put("thunder", new ScarySound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, "thunder"));
   }
}
