package dev.bloodmoonplus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EventManager {
   private static final int[] DY = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, 8, -8};
   private final BloodMoonPlus plugin;
   private final Map<String, EventManager.Session> sessions = new LinkedHashMap<>();
   private final List<EventManager.MobDef> mobs = new ArrayList<>();
   private boolean hordeEnabled;
   private boolean globalMode = true;
   private boolean skipEmpty = true;
   private double waveInterval;
   private double minDist;
   private double maxDist;
   private double firstWaveDelay;
   private int waveMaxSize = 30;
   private int baseSize;
   private int maxPerPlayer;
   private boolean reqArmor;
   private boolean lockNight;
   private boolean clearWeather;
   private boolean removeOnEnd;
   private boolean rewardOnStop;
   private int reqPieces;
   private List<String> reqMaterials = List.of();
   private long nightTime;
   private long endTime;
   private double maxMult;
   private List<String> cmdStart = List.of();
   private List<String> cmdEnd = List.of();
   private List<String> cmdSurvivor = List.of();
   private String survivorTable;
   private String defaultTable;
   private boolean keepVanilla;
   private boolean forceDifficulty;
   private boolean protectTerrain;
   private boolean blockWitherSpawn;
   private boolean ignoreOps = true;
   private final Set<String> worldsWithoutClock = new HashSet<>();
   private long lastSpecialEventMs = 0L;

   EventManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   void reload() {
      FileConfiguration c = this.plugin.getConfig();
      this.hordeEnabled = c.getBoolean("horde.enabled", true);
      this.waveInterval = c.getDouble("horde.interval-seconds", 25.0);
      this.baseSize = c.getInt("horde.base-size", 5);
      this.waveMaxSize = Math.max(1, c.getInt("horde.wave-max-size", 30));
      this.maxPerPlayer = c.getInt("horde.max-alive-per-player", 25);
      this.minDist = c.getDouble("horde.spawn-distance.min", 14.0);
      this.maxDist = Math.max(this.minDist + 1.0, c.getDouble("horde.spawn-distance.max", 28.0));
      this.firstWaveDelay = c.getDouble("event.first-wave-delay-seconds", 10.0);
      this.reqArmor = c.getBoolean("horde.require-armor.enabled", false);
      this.reqPieces = c.getInt("horde.require-armor.min-pieces", 1);
      this.reqMaterials = c.getStringList("horde.require-armor.materials");
      this.lockNight = c.getBoolean("event.lock-night", true);
      this.nightTime = c.getLong("event.night-time-ticks", 18000L);
      this.endTime = c.getLong("event.end-time-ticks", 23000L);
      this.clearWeather = c.getBoolean("event.clear-weather", true);
      this.removeOnEnd = c.getBoolean("event.remove-mobs-on-end", true);
      this.forceDifficulty = c.getBoolean("event.force-difficulty", true);
      this.protectTerrain = c.getBoolean("event.protect-terrain", true);
      this.blockWitherSpawn = c.getBoolean("event.block-wither-spawn", true);
      this.ignoreOps = c.getBoolean("event.ignore-ops", true);
      this.rewardOnStop = c.getBoolean("event.reward-on-stop", false);
      this.cmdStart = c.getStringList("event.commands-on-start");
      this.cmdEnd = c.getStringList("event.commands-on-end");
      this.cmdSurvivor = c.getStringList("event.survivor-commands");
      this.maxMult = c.getDouble("difficulty.max", 10.0);
      this.survivorTable = c.getString("loot.survivor-table", "survivors");
      this.defaultTable = c.getString("loot.default-table", "mob_horde");
      this.skipEmpty = c.getBoolean("schedule.skip-if-no-players", true);
      this.globalMode = c.getBoolean("event.global", true);
      this.keepVanilla = c.getBoolean("loot.keep-vanilla-drops", false);
      if (!c.contains("loot.keep-vanilla-drops")) {
         this.keepVanilla = false;
      }

      this.mobs.clear();

      for (Map<?, ?> m : c.getMapList("horde.mobs")) {
         EntityType type;
         try {
            type = EntityType.valueOf(Util.str(m, "type", "ZOMBIE").toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var6) {
            this.plugin.getLogger().warning("horde.mobs: invalid type " + m.get("type"));
            continue;
         }

         this.mobs
            .add(
               new EventManager.MobDef(
                  type,
                  Util.num(m, "weight", 1.0),
                  Util.num(m, "health", 0.0),
                  Util.num(m, "damage-multiplier", 1.0),
                  Util.num(m, "speed-multiplier", 1.0),
                  Util.str(m, "name", null),
                  parseEquipment(m.get("equipment")),
                  Util.num(m, "equipment-chance", 1.0),
                  Util.str(m, "loot-table", null)
               )
            );
      }
   }

   static Map<EquipmentSlot, Material> parseEquipment(Object o) {
      Map<EquipmentSlot, Material> out = new EnumMap<>(EquipmentSlot.class);
      if (o instanceof Map<?, ?> map) {
         for (Entry<?, ?> e : map.entrySet()) {
            String slotName = e.getKey().toString().toLowerCase(Locale.ROOT);

            EquipmentSlot slot = switch (slotName) {
               case "main-hand", "hand" -> EquipmentSlot.HAND;
               case "off-hand" -> EquipmentSlot.OFF_HAND;
               case "head", "helmet" -> EquipmentSlot.HEAD;
               case "chest", "chestplate" -> EquipmentSlot.CHEST;
               case "legs", "leggings" -> EquipmentSlot.LEGS;
               case "feet", "boots" -> EquipmentSlot.FEET;
               default -> null;
            };
            Material mat = Material.matchMaterial(e.getValue().toString());
            if (slot != null && mat != null) {
               out.put(slot, mat);
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public boolean keepVanillaDrops() {
      return this.keepVanilla;
   }

   public String defaultTable() {
      return this.defaultTable;
   }

   public boolean blockWitherSpawn() {
      return this.blockWitherSpawn;
   }

   public boolean protectTerrain() {
      return this.protectTerrain;
   }

   public EventManager.Session session(World w) {
      return w == null ? null : this.sessions.get(w.getName());
   }

   public boolean isActive(World w) {
      return w != null && this.sessions.containsKey(w.getName());
   }

   public EventManager.Session globalSession() {
      return this.sessions.isEmpty() ? null : this.sessions.values().iterator().next();
   }

   public boolean hasGlobalSession() {
      return !this.sessions.isEmpty();
   }

   public Collection<EventManager.Session> sessions() {
      return this.sessions.values();
   }

   public int activeWorlds() {
      return this.sessions.size();
   }

   public boolean isGlobalMode() {
      return this.globalMode;
   }

   public List<World> defaultWorlds() {
      List<World> out = new ArrayList<>();
      if (this.globalMode) {
         out.addAll(Bukkit.getWorlds());
      } else {
         for (String wn : this.plugin.getConfig().getStringList("worlds")) {
            World w = Bukkit.getWorld(wn);
            if (w != null) {
               out.add(w);
            }
         }
      }

      return out;
   }

   private void setWorldTime(World w, long time) {
      if (w != null && !this.worldsWithoutClock.contains(w.getName())) {
         try {
            w.setTime(time);
         } catch (IllegalArgumentException var5) {
            this.worldsWithoutClock.add(w.getName());
            this.plugin
               .getLogger()
               .warning(
                  "Cannot set the time in '" + w.getName() + "': this world has no own clock on this Paper version, night lock does not apply there."
               );
         }
      }
   }

   public boolean start(World w, double diff, int minutes) {
      return this.start(w, diff, minutes, MoonType.BLOOD);
   }

   public boolean start(World w, double diff, int minutes, MoonType type) {
      long now = System.currentTimeMillis();
      return this.startInternal(w, diff, now, now + (long)Math.max(1, minutes) * 60000L, false, true, type);
   }

   public boolean startIn(Collection<World> worlds, double diff, int minutes) {
      return this.startIn(worlds, diff, minutes, MoonType.BLOOD);
   }

   public boolean startIn(Collection<World> worlds, double diff, int minutes, MoonType type) {
      if (worlds != null && !worlds.isEmpty()) {
         long now = System.currentTimeMillis();
         long endMs = now + (long)Math.max(1, minutes) * 60000L;
         boolean any = false;

         for (World w : worlds) {
            if (w != null && this.startInternal(w, diff, now, endMs, false, !any, type)) {
               any = true;
            }
         }

         return any;
      } else {
         return false;
      }
   }

   private boolean startInternal(World w, double diff, long startMs, long endMs, boolean resumed, boolean announce) {
      return this.startInternal(w, diff, startMs, endMs, resumed, announce, MoonType.BLOOD);
   }

   private boolean startInternal(World w, double diff, long startMs, long endMs, boolean resumed, boolean announce, MoonType type) {
      if (w != null && !this.sessions.containsKey(w.getName())) {
         diff = Math.max(0.1, Math.min(this.maxMult, diff));
         MoonType mt = type != null ? type : MoonType.BLOOD;
         EventManager.Session s = new EventManager.Session(w, startMs, endMs, diff, mt);
         double minInterval = this.plugin.getConfig().getDouble("horde.min-interval-seconds", 15.0);
         double firstInterval = Math.max(minInterval, this.waveInterval / this.plugin.scaled(diff, "spawn-speed"));
         s.lastWaveMs = startMs + (long)(this.firstWaveDelay * 1000.0) - (long)(firstInterval * 1000.0);
         this.sessions.put(w.getName(), s);
         this.plugin.data().saveActive(w.getName(), startMs, endMs, diff, mt);
         if (this.lockNight) {
            this.setWorldTime(w, this.nightTime);
         }

         if (this.clearWeather) {
            w.setStorm(false);
            w.setThundering(false);
         }

         if (this.forceDifficulty && w.getDifficulty() == Difficulty.PEACEFUL) {
            s.originalDifficulty = Difficulty.PEACEFUL;
            w.setDifficulty(Difficulty.EASY);
            this.plugin.getLogger().info("Difficulty of '" + w.getName() + "' raised to EASY for the moon (it was Peaceful).");
         }

         if (!resumed) {
            long minutes = Math.max(1L, (endMs - startMs) / 60000L);
            String d = Util.fmt(diff);
            w.setStorm(true);
            w.setThundering(true);

            if (mt == MoonType.BLACK) {
               String chat = this.plugin.getConfig().getString(
                  "moon-types.black.announcement.chat",
                  "<gradient:#222222:#880088><bold>🌑 ECLIPSE OF THE BLACK MOON:</bold></gradient> <dark_gray><bold>Darkness swallowed the sky...</bold></dark_gray> <red>The BLACK MOON has risen! Its spawn are 50% stronger and more numerous!</red>"
               );
               String titleStr = this.plugin.getConfig().getString("moon-types.black.announcement.title", "<gradient:#111111:#660066><bold>BLACK MOON</bold></gradient>");
               String subStr = this.plugin.getConfig().getString("moon-types.black.announcement.subtitle", "<gray>Darkness covers the world. Fight!</gray>");

               Title t = Title.title(Util.mini(titleStr), Util.mini(subStr), Times.times(Duration.ofMillis(500L), Duration.ofSeconds(4L), Duration.ofSeconds(1L)));
               for (Player p : w.getPlayers()) {
                  p.showTitle(t);
                  p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 0.5F);
                  p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false));
                  p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 25, 1.0, 1.0, 1.0, 0.05);
               }
               if (announce) {
                  Bukkit.broadcast(Util.mini(chat));
               }
            } else if (mt == MoonType.PURPLE) {
               String chat = this.plugin.getConfig().getString(
                  "moon-types.purple.announcement.chat",
                  "<gradient:#9900ff:#e000ff><bold>🔮 VOID RIFT:</bold></gradient> <light_purple>The sky splits with a violet glow... The PURPLE MOON has risen! Astral creatures have entered this world!</light_purple>"
               );
               String titleStr = this.plugin.getConfig().getString("moon-types.purple.announcement.title", "<gradient:#8800ff:#ff00ff><bold>PURPLE MOON</bold></gradient>");
               String subStr = this.plugin.getConfig().getString("moon-types.purple.announcement.subtitle", "<light_purple>The void rift is open!</light_purple>");

               Title t = Title.title(Util.mini(titleStr), Util.mini(subStr), Times.times(Duration.ofMillis(500L), Duration.ofSeconds(4L), Duration.ofSeconds(1L)));
               for (Player p : w.getPlayers()) {
                  p.showTitle(t);
                  p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.1F);
                  p.getWorld().spawnParticle(Particle.DRAGON_BREATH, p.getLocation().add(0, 1, 0), 25, 1.0, 1.0, 1.0, 0.05, 1.0F);
               }
               if (announce) {
                  Bukkit.broadcast(Util.mini(chat));
               }
            } else {
               boolean showParams = this.plugin.getConfig().getBoolean("warning.show-parameters", false);
               String titleOverride = this.plugin.getConfig().getString("event.start-announcement.title", "");
               String subOverride = this.plugin.getConfig().getString(
                  showParams ? "event.start-announcement.subtitle-with-params" : "event.start-announcement.subtitle",
                  ""
               );
               String chatOverride = this.plugin.getConfig().getString(
                  showParams ? "event.start-announcement.chat-with-params" : "event.start-announcement.chat",
                  ""
               );

               Component title = !titleOverride.isEmpty()
                  ? Util.mini(titleOverride.replace("{difficulty}", d).replace("{minutes}", String.valueOf(minutes)))
                  : this.plugin.messages().get("event.title", "difficulty", d);

               Component sub = !subOverride.isEmpty()
                  ? Util.mini(subOverride.replace("{difficulty}", d).replace("{minutes}", String.valueOf(minutes)))
                  : (showParams ? this.plugin.messages().get("event.subtitle", "difficulty", d) : this.plugin.messages().get("event.subtitle-clean", "difficulty", d));

               for (Player p : w.getPlayers()) {
                  p.showTitle(Title.title(title, sub, Times.times(Duration.ofMillis(500L), Duration.ofSeconds(4L), Duration.ofSeconds(1L))));
                  p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0F, 0.5F);
                  p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0F, 0.6F);
                  p.getWorld()
                     .spawnParticle(Particle.DUST, p.getLocation().add(0.0, 1.0, 0.0), 30, 1.5, 1.5, 1.5, 0.05, new DustOptions(Color.fromRGB(180, 0, 0), 1.8F));
                  p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0, false, false));
               }

               if (announce && this.plugin.messages().announce("event", false)) {
                  if (!chatOverride.isEmpty()) {
                     this.plugin.messages().allDirect(chatOverride.replace("{difficulty}", d).replace("{minutes}", String.valueOf(minutes)));
                  } else if (showParams) {
                     this.plugin.messages().all("event.start", "difficulty", d, "minutes", String.valueOf(minutes));
                  } else {
                     this.plugin.messages().all("event.start-clean", "difficulty", d, "minutes", String.valueOf(minutes));
                  }
               }
            }

            if (this.plugin.quitPenalty() != null) {
               this.plugin.quitPenalty().onBloodMoonStart(w, s);
            }

            if (this.plugin.spectatorBlock() != null) {
               this.plugin.spectatorBlock().kickAllSpectators(w);
            }

            this.runCommands(this.cmdStart, w, null);
         }

         if (this.plugin.witherControl() != null) {
            this.plugin.witherControl().onBloodMoonStart(w);
         }

         this.plugin.bosses().plan(s, resumed);
         return true;
      } else {
         return false;
      }
   }

   public boolean stop(World w, boolean natural) {
      List<EventManager.Session> toStop = new ArrayList<>();
      if (w == null) {
         toStop.addAll(this.sessions.values());
      } else {
         EventManager.Session s = this.sessions.get(w.getName());
         if (s != null) {
            toStop.add(s);
         }
      }

      if (toStop.isEmpty()) {
         return false;
      } else {
         this.stopSessions(toStop, natural);
         return true;
      }
   }

   public int adjustDuration(World w, long deltaMs) {
      long now = System.currentTimeMillis();
      if (w != null) {
         EventManager.Session s = this.sessions.get(w.getName());
         if (s == null) return 0;
         s.endMs = Math.max(now + 2000L, s.endMs + deltaMs);
         this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);
         return 1;
      } else {
         int count = 0;
         for (EventManager.Session s : this.sessions.values()) {
            s.endMs = Math.max(now + 2000L, s.endMs + deltaMs);
            this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);
            count++;
         }
         return count;
      }
   }

   public int setDurationRemaining(World w, long remainingMs) {
      long now = System.currentTimeMillis();
      if (w != null) {
         EventManager.Session s = this.sessions.get(w.getName());
         if (s == null) return 0;
         s.endMs = Math.max(now + 2000L, now + remainingMs);
         this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);
         return 1;
      } else {
         int count = 0;
         for (EventManager.Session s : this.sessions.values()) {
            s.endMs = Math.max(now + 2000L, now + remainingMs);
            this.plugin.data().saveActive(s.world.getName(), s.startMs, s.endMs, s.difficulty, s.moonType);
            count++;
         }
         return count;
      }
   }

   private void stopSessions(List<EventManager.Session> toStop, boolean natural) {
      if (natural) {
         this.recordHistory(toStop);
      }

      for (EventManager.Session s : toStop) {
         this.sessions.remove(s.world.getName());
         this.stopOne(s, natural);
      }
   }

   private void recordHistory(List<EventManager.Session> group) {
      long durationSec = 0L;
      long deaths = 0L;
      long uniqueDeaths = 0L;
      long participants = 0L;
      long survivorsCount = 0L;
      String bossName = "";
      String bossType = "NONE";

      for (EventManager.Session s : group) {
         durationSec = Math.max(durationSec, Math.max(0L, (s.endMs - s.startMs) / 1000L));
         deaths += (long)s.kills.values().stream().mapToInt(Integer::intValue).sum();
         uniqueDeaths += (long)s.died.size();
         participants += (long)s.participants.size();
         survivorsCount += (long)(s.participants.size() - s.died.size());
         if (s.lastBossName != null && !s.lastBossName.isEmpty()) {
            bossName = s.lastBossName;
            bossType = s.lastBossType;
         }
      }

      String world = group.size() == 1 ? group.get(0).world.getName() : "global";
      this.plugin.stats().recordEventCompleted(world, durationSec, deaths, uniqueDeaths, participants, survivorsCount, bossName, bossType);
   }

   private void stopOne(EventManager.Session s, boolean natural) {
      this.plugin.data().clearActive(s.world.getName());

      for (Player p : Bukkit.getOnlinePlayers()) {
         p.hideBossBar(s.bar);
      }

      this.plugin.bosses().removeAll(s.world);
      if (this.plugin.witherControl() != null) {
         this.plugin.witherControl().onBloodMoonStop(s.world);
      }
      if (this.plugin.epicEffects() != null) {
         this.plugin.epicEffects().onSessionStop(s.world);
      }
      if (this.removeOnEnd) {
         this.removeTagged(s.world);
      }

      if (this.lockNight) {
         this.setWorldTime(s.world, this.endTime);
      }

      this.restoreDifficulty(s.world, s);
      List<Player> survivors = new ArrayList<>();

      for (UUID id : s.participants) {
         Player p = Bukkit.getPlayer(id);
         if (p != null && p.isOnline() && p.getWorld().equals(s.world) && !s.died.contains(id)) {
            survivors.add(p);
         }
      }

      if (natural || this.rewardOnStop) {
         List<Player> eligible = this.plugin.survivors().processRewards(s, natural);
         if (!eligible.isEmpty()) {
            this.giveSurvivorRewards(s, eligible);
         }
      }

      if (this.plugin.messages().announce("event", false)) {
         this.plugin.messages().world(s.world, natural ? "event.end" : "event.stopped");
         if (!s.kills.isEmpty()) {
            List<Entry<UUID, Integer>> top = new ArrayList<>(s.kills.entrySet());
            top.sort((a, b) -> b.getValue() - a.getValue());
            this.plugin.messages().world(s.world, "event.summary-header");

            for (int i = 0; i < Math.min(3, top.size()); i++) {
               String name = Bukkit.getOfflinePlayer(top.get(i).getKey()).getName();
               this.plugin
                  .messages()
                  .world(
                     s.world,
                     "event.summary-line",
                     "pos",
                     String.valueOf(i + 1),
                     "player",
                     name == null ? "?" : name,
                     "kills",
                     String.valueOf(top.get(i).getValue())
                  );
            }
         }
      }

      this.runCommands(this.cmdEnd, s.world, null);
   }

   private void giveSurvivorRewards(EventManager.Session s, List<Player> survivors) {
      for (Player p : survivors) {
         for (ItemStack it : this.plugin.loot().roll(this.survivorTable, p, s.difficulty, false)) {
            this.plugin.loot().giveOrDrop(p, it);
         }

         this.runCommands(this.cmdSurvivor, s.world, p);
         p.sendMessage(this.plugin.messages().prefixed("event.survivors"));
      }
   }

   void runCommands(List<String> cmds, World w, Player p) {
      for (String c : cmds) {
         String cmd = c.replace("{world}", w.getName());
         if (p != null) {
            cmd = cmd.replace("{player}", p.getName());
         } else if (cmd.contains("{player}")) {
            continue;
         }

         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
      }
   }

   public void resume() {
      long now = System.currentTimeMillis();
      ConfigurationSection a = this.plugin.data().active();
      if (a != null) {
         for (String wn : a.getKeys(false)) {
            World w = Bukkit.getWorld(wn);
            long end = a.getLong(wn + ".end");
            if (w != null && end > now) {
               if (this.startInternal(w, a.getDouble(wn + ".difficulty", 1.0), a.getLong(wn + ".start", now), end, true, false, MoonType.fromString(a.getString(wn + ".type")))) {
                  this.plugin.getLogger().info("Evento retomado en '" + wn + "' (quedan " + Util.dur((end - now) / 1000L) + ").");
               }
            } else {
               this.plugin.data().clearActive(wn);
            }
         }
      }

      for (String wnx : this.plugin.getConfig().getStringList("worlds")) {
         World w = Bukkit.getWorld(wnx);
         if (w != null && this.sessions.isEmpty()) {
            this.removeTagged(w);
         }
      }
   }

   public void shutdown() {
      for (EventManager.Session s : new ArrayList<>(this.sessions.values())) {
         for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(s.bar);
         }

         this.plugin.bosses().removeAll(s.world);
         this.removeTagged(s.world);
      }

      this.sessions.clear();
   }

   private void restoreDifficulty(World w, EventManager.Session s) {
      if (s.originalDifficulty != null && w.getDifficulty() != s.originalDifficulty) {
         w.setDifficulty(s.originalDifficulty);
      }
   }

   public void tick() {
      long now = System.currentTimeMillis();
      if (!this.sessions.isEmpty()) {
         List<EventManager.Session> ended = null;
         List<EventManager.Session> orphan = null;

         for (EventManager.Session s : this.sessions.values()) {
            if (Bukkit.getWorld(s.world.getName()) == null) {
               if (orphan == null) {
                  orphan = new ArrayList<>();
               }

               orphan.add(s);
            } else if (now >= s.endMs) {
               if (ended == null) {
                  ended = new ArrayList<>();
               }

               ended.add(s);
            }
         }

         if (orphan != null) {
            for (EventManager.Session sx : orphan) {
               this.sessions.remove(sx.world.getName());
               this.plugin.data().clearActive(sx.world.getName());
            }
         }

         if (ended != null) {
            this.stopSessions(ended, true);
         }

         for (EventManager.Session sx : new ArrayList<>(this.sessions.values())) {
            try {
               this.tickSession(sx, now);
            } catch (Exception var8) {
               this.plugin
                  .getLogger()
                  .log(Level.WARNING, "Moon tick failed in world '" + sx.world.getName() + "': " + var8.getMessage(), (Throwable)var8);
            }
         }
      }
   }

   private void tickSession(EventManager.Session global, long now) {
      World w = global.world;
      if (Bukkit.getWorld(w.getName()) != null) {
         for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
               global.participants.add(p.getUniqueId());
            }
         }

         this.updateBar(global, now);
         if (this.lockNight) {
            this.setWorldTime(w, this.nightTime);
         }

         if (w.hasStorm()) {
            w.setStorm(false);
         }

         if (!w.isThundering()) {
            w.setThundering(true);
         }

         if (now - global.lastAmbientThunderMs > 40000L + (long)ThreadLocalRandom.current().nextInt(30000)) {
            global.lastAmbientThunderMs = now;
            if (!w.getPlayers().isEmpty()) {
               Player rp = (Player)w.getPlayers().get(ThreadLocalRandom.current().nextInt(w.getPlayers().size()));
               rp.playSound(rp.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4F, 0.35F);
            }
         }

         if (now / 1000L % 30L == 0L && !w.getPlayers().isEmpty()) {
            Player rp = (Player)w.getPlayers().get(ThreadLocalRandom.current().nextInt(w.getPlayers().size()));
            rp.playSound(rp.getLocation(), Sound.AMBIENT_CAVE, 0.35F, 0.45F);
         }

         if (now / 1000L % 40L == 10L && !w.getPlayers().isEmpty()) {
            Player rp = (Player)w.getPlayers().get(ThreadLocalRandom.current().nextInt(w.getPlayers().size()));
            rp.playSound(rp.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.25F, 0.7F);
         }

         if (now / 1000L % 4L == 0L) {
            for (Player px : w.getPlayers()) {
               w.spawnParticle(Particle.DUST, px.getLocation().add(0.0, 0.5, 0.0), 6, 0.7, 0.35, 0.7, 0.02, new DustOptions(Color.fromRGB(140, 0, 0), 0.9F));
               if (!px.hasPotionEffect(PotionEffectType.GLOWING)) {
                  px.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
               }
            }
         }

         double minInterval = this.plugin.getConfig().getDouble("horde.min-interval-seconds", 15.0);
         double interval = Math.max(minInterval, this.waveInterval / this.plugin.scaled(global.difficulty, "spawn-speed"));
         if ((double)(now - global.lastWaveMs) >= interval * 1000.0) {
            global.lastWaveMs = now;
            this.spawnWave(global);
         }

         this.plugin.bosses().tickSpawns(global, now);
         this.tickSpecialEvent(global, now);
         this.plugin.epicEffects().tick(global, now);
      }
   }

   private void tickSpecialEvent(EventManager.Session s, long now) {
      if (this.lastSpecialEventMs == 0L) {
         this.lastSpecialEventMs = now;
      }

      long since = now - this.lastSpecialEventMs;
      if (since >= 300000L) {
         if (ThreadLocalRandom.current().nextDouble() < (double)(since - 300000L) / 30000.0) {
            this.lastSpecialEventMs = now;
            this.triggerRandomSpecial(s);
         }
      }
   }

   private void triggerRandomSpecial(EventManager.Session s) {
      int pick = ThreadLocalRandom.current().nextInt(4);
      World w = s.world;
      boolean sp = this.plugin.messages().announce("specials", false);
      switch (pick) {
         case 0:
            this.setWorldTime(w, 18000L);
            w.playSound(net.kyori.adventure.sound.Sound.sound(Sound.ENTITY_ENDER_DRAGON_GROWL, Source.MASTER, 1.0F, 0.6F));
            if (sp) {
               this.plugin.messages().world(w, "special.eclipse");
            }
            break;
         case 1:
            w.setStorm(true);
            w.setThundering(true);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (Bukkit.getWorld(w.getName()) != null) {
                  w.setStorm(false);
                  w.setThundering(false);
               }
            }, 1200L);
            if (sp) {
               this.plugin.messages().world(w, "special.storm");
            }
            break;
         case 2:
            for (Player p : w.getPlayers()) {
               p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 900, 0));
            }

            if (sp) {
               this.plugin.messages().world(w, "special.fog");
            }
            break;
         case 3:
            w.playSound(net.kyori.adventure.sound.Sound.sound(Sound.ENTITY_WARDEN_HEARTBEAT, Source.MASTER, 1.0F, 1.0F));

            for (Player p : w.getPlayers()) {
               p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 600, 0));
            }

            if (sp) {
               this.plugin.messages().world(w, "special.silence");
            }
      }
   }

   private void updateBar(EventManager.Session s, long now) {
      long rem = s.endMs - now;
      float prog = (float)Math.max(0.0, Math.min(1.0, (double)rem / (double)Math.max(1L, s.endMs - s.startMs)));
      s.bar.progress(prog);

      if (s.moonType == MoonType.BLACK) {
         s.bar.color(net.kyori.adventure.bossbar.BossBar.Color.PURPLE);
         s.bar.name(Util.mini(this.plugin.locales().get("text.event.m001", "time", String.valueOf(Util.dur(rem / 1000L)), "difficulty", String.valueOf(Util.fmt(s.difficulty)))));
      } else if (s.moonType == MoonType.PURPLE) {
         s.bar.color(net.kyori.adventure.bossbar.BossBar.Color.PURPLE);
         s.bar.name(Util.mini(this.plugin.locales().get("text.event.m002", "time", String.valueOf(Util.dur(rem / 1000L)), "difficulty", String.valueOf(Util.fmt(s.difficulty)))));
      } else {
         s.bar.color(net.kyori.adventure.bossbar.BossBar.Color.RED);
         s.bar.name(this.plugin.messages().get("event.bar", "time", Util.dur(rem / 1000L), "difficulty", Util.fmt(s.difficulty)));
      }

      for (Player p : s.world.getPlayers()) {
         p.showBossBar(s.bar);
      }
   }

   boolean eligible(Player p, EventManager.Session s) {
      if (!p.isDead() && !s.dead.contains(p.getUniqueId())) {
         if (this.ignoreOps && p.isOp()) {
            return false;
         }
         if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            return false;
         } else if (!this.reqArmor) {
            return true;
         } else {
            int c = 0;

            for (ItemStack a : p.getInventory().getArmorContents()) {
               if (a != null) {
                  String n = a.getType().name();

                  for (String m : this.reqMaterials) {
                     if (n.startsWith(m.toUpperCase(Locale.ROOT) + "_")) {
                        c++;
                        break;
                     }
                  }
               }
            }

            return c >= this.reqPieces;
         }
      } else {
         return false;
      }
   }

   boolean hasEligiblePlayers(EventManager.Session s) {
      for (Player p : s.world.getPlayers()) {
         if (this.eligible(p, s)) {
            return true;
         }
      }

      return false;
   }

   public int spawnWave(EventManager.Session s) {
      if (this.hordeEnabled && !this.mobs.isEmpty()) {
         if (!this.hasEligiblePlayers(s)) {
            return 0;
         } else {
            World w = s.world;
            double sizeScale = this.plugin.scaled(s.difficulty, "horde-size");
            double maxHordeMult = this.plugin.getConfig().getDouble("difficulty.max-horde-size-multiplier", 2.0);
            if (maxHordeMult > 0.0) {
               sizeScale = Math.min(maxHordeMult, sizeScale);
            }
            s.waveCount++;
            int dynamicBase = Math.min(this.waveMaxSize, this.baseSize * s.waveCount);
            int perWave = Math.max(1, (int)Math.round((double)dynamicBase * sizeScale));
            int nearGlobal = 0;

            for (Player p : w.getPlayers()) {
               if (this.eligible(p, s)) {
                  for (Entity e : p.getNearbyEntities(48.0, 48.0, 48.0)) {
                     if (e instanceof LivingEntity le && isTagged(le)) {
                        nearGlobal++;
                        break;
                     }
                  }
               }
            }

            int playerCount = (int)w.getPlayers().stream().filter(px -> this.eligible(px, s)).count();
            int saturation = Math.min(100, nearGlobal / Math.max(1, playerCount) + playerCount * 6);
            int cap = Math.max(
               1,
               (int)Math.round((double)this.maxPerPlayer * sizeScale * (1.0 + (double)s.waveCount * 0.15) * (double)(100 - Math.min(40, saturation)) / 100.0)
            );
            int spawned = 0;

            for (Player px : w.getPlayers()) {
               if (this.eligible(px, s)) {
                  int near = 0;

                  for (Entity ex : px.getNearbyEntities(48.0, 48.0, 48.0)) {
                     if (ex instanceof LivingEntity) {
                        LivingEntity le = (LivingEntity)ex;
                        if (isTagged(le)) {
                           near++;
                        }
                     }
                  }

                  int amount = Math.min(perWave, Math.max(0, cap - near));
                  if (s.moonType == MoonType.BLACK) {
                     double hsm = this.plugin.getConfig().getDouble("moon-types.black.horde-size-multiplier", 1.5);
                     amount = (int)(amount * hsm);
                  } else if (s.moonType == MoonType.PURPLE) {
                     double hsm = this.plugin.getConfig().getDouble("moon-types.purple.horde-size-multiplier", 1.25);
                     amount = (int)(amount * hsm);
                  }
                  if (s.lastHordeAnnounceMs == 0L || System.currentTimeMillis() - s.lastHordeAnnounceMs > 30000L) {
                     Location rayPoint = this.findSpawn(px.getLocation(), this.minDist, this.maxDist);
                     if (rayPoint != null) {
                        w.strikeLightningEffect(rayPoint);
                        w.playSound(rayPoint, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 0.8F);
                     }

                     s.lastHordeAnnounceMs = System.currentTimeMillis();
                     if (this.plugin.messages().announce("horde", false)) {
                        this.plugin.messages().all("event.horde-descending", "player", px.getName());
                     }
                     String personalHorde = this.plugin.locales().get("event.horde-descending-personal");
                     if (personalHorde == null || personalHorde.isEmpty() || personalHorde.equals("event.horde-descending-personal")) {
                        personalHorde = "<red><bold>⚡ A horde is coming for you! ⚡</bold></red>";
                     }
                     px.sendActionBar(Util.mini(personalHorde));
                     px.playSound(px.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7F, 0.8F);
                  }

                  for (int i = 0; i < amount; i++) {
                     Location loc = this.findSpawn(px.getLocation(), this.minDist, this.maxDist);
                     if (loc != null) {
                        if (i % 5 == 0) {
                           Location spot = this.findSpawn(px.getLocation(), this.minDist, this.maxDist);
                           if (spot != null) {
                              w.strikeLightningEffect(spot);
                              w.spawnParticle(Particle.DUST, spot.add(0.0, 1.0, 0.0), 10, 0.3, 0.4, 0.3, 0.05, new DustOptions(Color.RED, 1.2F));
                           }
                        }

                        boolean spawnInc = s.difficulty >= this.plugin.epicEffects().getMinDifficulty()
                           && this.plugin.getConfig().getBoolean("difficulty.incendium.enabled", true)
                           && ThreadLocalRandom.current().nextDouble() <= this.plugin.getConfig().getDouble("difficulty.incendium.chance-in-horde", 0.20);

                        LivingEntity le = null;
                        if (spawnInc) {
                           List<String> types = new ArrayList<>();
                           types.add("paratrooper");
                           types.add("crossbowman");
                           double pyroMin = this.plugin.getConfig().getDouble("difficulty.incendium.pyromancer.min-difficulty", 6.0);
                           if (s.difficulty >= pyroMin) {
                              types.add("pyromancer");
                           }
                           String incType = types.get(ThreadLocalRandom.current().nextInt(types.size()));
                           le = this.plugin.epicEffects().spawnIncendiumMob(w, loc, incType, s.difficulty);
                        } else {
                           EventManager.MobDef d = Util.pick(this.mobs, EventManager.MobDef::weight);
                           if (d != null) {
                              le = this.spawn(
                                 loc, d.type(), d.health(), d.damage(), d.speed(), d.name(), d.equipment(), d.equipChance(), s.difficulty, d.table(), px
                              );
                           }
                        }
                        if (le != null) {
                           spawned++;
                        }
                     }
                  }
               }
            }

            return spawned;
         }
      } else {
         return 0;
      }
   }

   public LivingEntity spawnMinion(Location loc, EntityType type, double diff, Player target) {
      for (EventManager.MobDef d : this.mobs) {
         if (d.type() == type) {
            return this.spawn(loc, type, d.health(), d.damage(), d.speed(), d.name(), d.equipment(), d.equipChance(), diff, d.table(), target);
         }
      }

      return this.spawn(loc, type, 0.0, 1.0, 1.0, null, Map.of(), 1.0, diff, null, target);
   }

   public int spawnPunishmentHorde(Player target, int count, double diff) {
      if (target == null || !target.isOnline()) return 0;
      int spawned = 0;
      for (int i = 0; i < count; i++) {
         Location loc = this.findSpawn(target.getLocation(), 6.0, 16.0);
         if (loc == null) {
            loc = target.getLocation().clone().add((Math.random() - 0.5) * 10.0, 0, (Math.random() - 0.5) * 10.0);
         }
         EventManager.MobDef d = Util.pick(this.mobs, EventManager.MobDef::weight);
         if (d != null) {
            LivingEntity le = this.spawn(loc, d.type(), d.health(), d.damage(), d.speed(), d.name(), d.equipment(), d.equipChance(), diff, d.table(), target);
            if (le != null) {
               spawned++;
            }
         }
      }
      return spawned;
   }

   public LivingEntity spawn(
      Location loc,
      EntityType type,
      double health,
      double dmg,
      double speed,
      String name,
      Map<EquipmentSlot, Material> equip,
      double equipChance,
      double diff,
      String table,
      Player target
   ) {
      Entity ent;
      try {
         ent = loc.getWorld().spawnEntity(loc, type);
      } catch (IllegalStateException var25) {
         if (var25.getMessage() == null || !var25.getMessage().contains("Peaceful")) {
            this.plugin.getLogger().warning("No se pudo spawnear " + type + ": " + var25.getMessage());
         }

         return null;
      } catch (IllegalArgumentException var26) {
         this.plugin.getLogger().warning("No se pudo spawnear " + type + ": " + var26.getMessage());
         return null;
      }

      if (ent instanceof LivingEntity le) {
         EventManager.Session curSession = this.session(loc.getWorld());
         MoonType mt = curSession != null ? curSession.moonType : MoonType.BLOOD;

         PersistentDataContainer pdc = le.getPersistentDataContainer();
         pdc.set(Keys.MOB, PersistentDataType.BYTE, (byte)1);
         pdc.set(Keys.DIFF, PersistentDataType.DOUBLE, diff);
         if (Keys.MOON_TYPE != null) {
            pdc.set(Keys.MOON_TYPE, PersistentDataType.STRING, mt.getId());
         }

         double healthMult = 1.0;
         double damageMult = 1.0;
         double speedBonus = 1.0;
         if (mt == MoonType.BLACK) {
            healthMult = this.plugin.getConfig().getDouble("moon-types.black.health-multiplier", 1.5);
            damageMult = this.plugin.getConfig().getDouble("moon-types.black.damage-multiplier", 1.5);
            speedBonus = this.plugin.getConfig().getDouble("moon-types.black.speed-multiplier", 1.15);
         } else if (mt == MoonType.PURPLE) {
            healthMult = this.plugin.getConfig().getDouble("moon-types.purple.health-multiplier", 1.3);
            damageMult = this.plugin.getConfig().getDouble("moon-types.purple.damage-multiplier", 1.3);
            speedBonus = this.plugin.getConfig().getDouble("moon-types.purple.speed-multiplier", 1.25);
         }

         pdc.set(Keys.SCALE, PersistentDataType.DOUBLE, dmg * this.plugin.scaled(diff, "damage") * damageMult);
         if (table != null) {
            pdc.set(Keys.TABLE, PersistentDataType.STRING, table);
         }

         int mobLevel = Math.max(1, (int)Math.round(diff));
         if (Keys.LEVEL != null) {
            pdc.set(Keys.LEVEL, PersistentDataType.INTEGER, mobLevel);
         }
         try {
            pdc.set(new NamespacedKey("levelledmobs", "level"), PersistentDataType.INTEGER, mobLevel);
         } catch (Exception ignored) {
         }

         AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
         if (hp != null) {
            double base = health > 0.0 ? health : hp.getBaseValue();
            double rawHp = base * this.plugin.scaled(diff, "health") * healthMult;
            double hpCap = this.plugin.getMaxHealthCap();
            double v = hpCap > 0.0 ? Math.min(hpCap, rawHp) : rawHp;
            hp.setBaseValue(v);
            le.setHealth(Math.min(v, hp.getValue()));
         }

         AttributeInstance sp = le.getAttribute(Attribute.MOVEMENT_SPEED);
         if (sp != null) {
            double speedMult = speed * this.plugin.scaled(diff, "speed") * speedBonus;
            double speedCap = this.plugin.getSpeedCap();
            if (speedCap > 0.0) {
               speedMult = Math.min(speedCap, speedMult);
            }
            sp.setBaseValue(sp.getBaseValue() * speedMult);
         }

         if (name != null) {
            String formattedName = name;
            if (this.plugin.isLevelEnabled()) {
               String lvlFmt = this.plugin.getLevelFormat();
               formattedName = lvlFmt.replace("{level}", String.valueOf(mobLevel)).replace("{name}", name);
            }
            le.customName(Util.mini(formattedName));
            if (this.plugin.getConfig().getBoolean("difficulty.level.always-visible", false)) {
               le.setCustomNameVisible(true);
            }
         }

         if (le instanceof Ageable a) {
            a.setAdult();
         }

         le.setRemoveWhenFarAway(false);
         le.setPersistent(false);
         EntityEquipment eq = le.getEquipment();
         if (eq != null && equip != null) {
            for (Entry<EquipmentSlot, Material> en : equip.entrySet()) {
               if (ThreadLocalRandom.current().nextDouble() <= equipChance) {
                  eq.setItem(en.getKey(), new ItemStack(en.getValue()));
                  eq.setDropChance(en.getKey(), 0.0F);
               }
            }
         }

         if (target != null && le instanceof Mob m) {
            m.setTarget(target);
         }

         return le;
      } else {
         ent.remove();
         return null;
      }
   }

   public static boolean isTagged(Entity e) {
      return e.getPersistentDataContainer().has(Keys.MOB, PersistentDataType.BYTE);
   }

   public int removeTagged(World w) {
      int n = 0;

      for (LivingEntity le : w.getLivingEntities()) {
         if (isTagged(le)) {
            le.remove();
            n++;
         }
      }

      return n;
   }

   public Location findSpawn(Location c, double min, double max) {
      World w = c.getWorld();
      ThreadLocalRandom rnd = ThreadLocalRandom.current();

      for (int i = 0; i < 12; i++) {
         double ang = rnd.nextDouble() * Math.PI * 2.0;
         double dist = min + rnd.nextDouble() * (max - min);
         int x = c.getBlockX() + (int)Math.round(Math.cos(ang) * dist);
         int z = c.getBlockZ() + (int)Math.round(Math.sin(ang) * dist);
         if (w.isChunkLoaded(x >> 4, z >> 4)) {
            for (int dy : DY) {
               int y = c.getBlockY() + dy;
               if (y > w.getMinHeight() + 1 && y < w.getMaxHeight() - 2) {
                  Block feet = w.getBlockAt(x, y, z);
                  Block head = w.getBlockAt(x, y + 1, z);
                  Block below = w.getBlockAt(x, y - 1, z);
                  if (feet.isPassable()
                     && head.isPassable()
                     && !feet.isLiquid()
                     && !head.isLiquid()
                     && below.getType().isSolid()
                     && !below.getType().isAir()
                     && !below.getType().toString().contains("LAVA")
                     && !below.getType().toString().contains("FIRE")
                     && !below.getType().toString().contains("MAGMA")
                     && !below.getType().toString().contains("SWEET_BERRY")) {
                     return new Location(w, (double)x + 0.5, (double)y, (double)z + 0.5);
                  }
               }
            }
         }
      }

      return null;
   }

   static record MobDef(
      EntityType type,
      double weight,
      double health,
      double damage,
      double speed,
      String name,
      Map<EquipmentSlot, Material> equipment,
      double equipChance,
      String table
   ) {
   }

   public static final class Session {
      public final World world;
      public final long startMs;
      public long endMs;
      public double difficulty;
      public MoonType moonType = MoonType.BLOOD;
      final BossBar bar = BossBar.bossBar(Component.empty(), 1.0F, net.kyori.adventure.bossbar.BossBar.Color.RED, Overlay.PROGRESS);
      public final Set<UUID> dead = new HashSet<>();
      public final Set<UUID> died = new HashSet<>();
      public final Set<UUID> participants = new HashSet<>();
      public final Map<UUID, Integer> kills = new HashMap<>();
      final List<BossManager.Pending> pendingBosses = new ArrayList<>();
      Difficulty originalDifficulty;
      long lastWaveMs;
      int waveCount = 0;
      long lastHordeAnnounceMs = 0L;
      long lastAmbientThunderMs = 0L;
      String lastBossName = "";
      String lastBossType = "NONE";

      Session(World world, long startMs, long endMs, double difficulty, MoonType moonType) {
         this.world = world;
         this.startMs = startMs;
         this.endMs = endMs;
         this.difficulty = difficulty;
         this.moonType = moonType != null ? moonType : MoonType.BLOOD;
         this.bar.color(this.moonType.getBarColor());
      }

      Session(World world, long startMs, long endMs, double difficulty) {
         this(world, startMs, endMs, difficulty, MoonType.BLOOD);
      }
   }
}
