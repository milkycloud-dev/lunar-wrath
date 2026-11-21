package dev.bloodmoonplus;

import dev.bloodmoonplus.config.WorldConfigRegistry;
import dev.bloodmoonplus.i18n.LocaleManager;
import dev.bloodmoonplus.integration.mythic.MythicBridge;
import dev.bloodmoonplus.placeholder.PlaceholderSnapshotService;
import dev.bloodmoonplus.stats.StatisticsStore;
import dev.bloodmoonplus.survivor.SurvivorManager;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BloodMoonPlus extends JavaPlugin {
   private Messages messages;
   private LocaleManager locales;
   private WorldConfigRegistry worldConfigs;
   private PlaceholderSnapshotService snapshots;
   private StatisticsStore stats;
   private SurvivorManager survivors;
   private MythicBridge mythic;
   private DataStore data;
   private ItemFactory items;
   private LootManager loot;
   private EventManager events;
   private BossManager bosses;
   private ScheduleManager schedule;
   private ItemAbilities abilities;
   private EpicEffectsManager epicEffects;
   private QuitPenaltyManager quitPenalty;
   private CountdownManager countdown;
   private SpectatorBlockManager spectatorBlock;
   private DimensionWrathManager dimensionWrath;
   private ElytraWrathManager elytraWrath;
   private ContainerWrathManager containerWrath;
   private DeathSummaryManager deathSummary;
   private LightningEffectManager lightning;
   private DynamicDifficultyManager dynamicDifficulty;
   private WitherControlManager witherControl;

   private static BloodMoonPlus instance;

   public static BloodMoonPlus instance() {
      return instance;
   }

   public void onEnable() {
      instance = this;
      Keys.init(this);
      this.saveDefaultConfig();

      for (String f : new String[]{"items.yml", "loot.yml", "bosses.yml"}) {
         if (!new File(this.getDataFolder(), f).exists()) {
            this.saveResource(f, false);
         }
      }

      this.locales = new LocaleManager(this);
      this.locales.init();
      this.worldConfigs = new WorldConfigRegistry(this);
      this.worldConfigs.init();
      this.stats = new StatisticsStore(this);
      this.stats.load();
      this.messages = new Messages(this, this.locales);
      this.data = new DataStore(this);
      this.items = new ItemFactory(this);
      this.loot = new LootManager(this);
      this.events = new EventManager(this);
      this.bosses = new BossManager(this);
      this.schedule = new ScheduleManager(this);
      this.abilities = new ItemAbilities(this);
      this.snapshots = new PlaceholderSnapshotService(this, this.stats);
      this.survivors = new SurvivorManager(this);
      this.mythic = new MythicBridge(this);
      this.epicEffects = new EpicEffectsManager(this);
      this.quitPenalty = new QuitPenaltyManager(this);
      this.countdown = new CountdownManager(this);
      this.spectatorBlock = new SpectatorBlockManager(this);
      this.dimensionWrath = new DimensionWrathManager(this);
      this.elytraWrath = new ElytraWrathManager(this);
      this.containerWrath = new ContainerWrathManager(this);
      this.deathSummary = new DeathSummaryManager(this);
      this.lightning = new LightningEffectManager(this);
      this.dynamicDifficulty = new DynamicDifficultyManager(this);
      this.witherControl = new WitherControlManager(this);
      this.reloadAll();
      Bukkit.getScheduler().runTaskTimer(this, () -> {
         this.schedule.tick();
         this.events.tick();
      }, 40L, 20L);
      Bukkit.getScheduler().runTaskTimer(this, () -> this.bosses.tick(), 40L, 10L);
      Bukkit.getScheduler().runTaskTimer(this, () -> this.abilities.tickPassives(), 60L, 40L);
      Bukkit.getScheduler().runTaskTimer(this, () -> this.snapshots.publish(), 40L, 10L);
      Bukkit.getScheduler().runTaskTimer(this, () -> this.data.flushIfDirty(), 200L, 200L);
      BloodMoonCommand exec = new BloodMoonCommand(this);
      PluginCommand luna = this.getCommand("luna");
      if (luna != null) {
         luna.setExecutor(exec);
         luna.setTabCompleter(exec);
      }

      Bukkit.getPluginManager().registerEvents(new EventListener(this), this);
      Bukkit.getPluginManager().registerEvents(this.abilities, this);
      Bukkit.getPluginManager().registerEvents(this.epicEffects, this);
      Bukkit.getPluginManager().registerEvents(this.quitPenalty, this);
      Bukkit.getPluginManager().registerEvents(this.spectatorBlock, this);
      Bukkit.getPluginManager().registerEvents(this.dimensionWrath, this);
      Bukkit.getPluginManager().registerEvents(this.elytraWrath, this);
      Bukkit.getPluginManager().registerEvents(this.containerWrath, this);
      Bukkit.getPluginManager().registerEvents(this.deathSummary, this);
      Bukkit.getPluginManager().registerEvents(this.lightning, this);
      Bukkit.getPluginManager().registerEvents(this.dynamicDifficulty, this);
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         new PapiHook(this).register();
         this.getLogger().info("PlaceholderAPI hooked (%bloodmoon_...%).");
      }

      Bukkit.getScheduler().runTaskLater(this, () -> this.events.resume(), 100L);
   }

   public void onDisable() {
      if (this.countdown != null) {
         this.countdown.cancel(false);
      }

      if (this.dimensionWrath != null) {
         this.dimensionWrath.shutdown();
      }

      if (this.elytraWrath != null) {
         this.elytraWrath.shutdown();
      }

      if (this.deathSummary != null) {
         this.deathSummary.shutdown();
      }

      if (this.lightning != null) {
         this.lightning.shutdown();
      }

      if (this.dynamicDifficulty != null) {
         this.dynamicDifficulty.shutdown();
      }

      if (this.witherControl != null) {
         this.witherControl.shutdown();
      }

      if (this.epicEffects != null) {
         this.epicEffects.onSessionStop(null);
      }

      if (this.events != null) {
         this.events.shutdown();
      }

      if (this.data != null) {
         this.data.saveNow();
      }

      if (this.stats != null) {
         this.stats.saveNow();
      }
   }

   public void reloadAll() {
      this.reloadConfig();
      this.locales.reload();
      this.worldConfigs.reload();
      this.messages.reload();
      this.loot.reload();
      this.items.reload();
      this.bosses.reload();
      this.events.reload();
      this.schedule.reload();
      if (this.quitPenalty != null) {
         this.quitPenalty.reload();
      }
      if (this.countdown != null) {
         this.countdown.reload();
      }
      if (this.spectatorBlock != null) {
         this.spectatorBlock.reload();
      }
      if (this.dimensionWrath != null) {
         this.dimensionWrath.reload();
      }
      if (this.elytraWrath != null) {
         this.elytraWrath.reload();
      }
      if (this.containerWrath != null) {
         this.containerWrath.reload();
      }
      if (this.deathSummary != null) {
         this.deathSummary.reload();
      }
      if (this.dynamicDifficulty != null) {
         this.dynamicDifficulty.reload();
      }
      if (this.witherControl != null) {
         this.witherControl.reload();
      }
      this.getLogger().info("LunarWrath reloaded.");
   }

   public Keys keys() {
      return Keys.instance();
   }

   public double scaled(double diff, String key) {
      double maxDiff = this.getConfig().getDouble("difficulty.scaling." + key + "-max-difficulty", Double.MAX_VALUE);
      double effectiveDiff = Math.min(diff, maxDiff);
      return Math.max(0.05, 1.0 + (effectiveDiff - 1.0) * this.getConfig().getDouble("difficulty.scaling." + key, 0.0));
   }

   public EpicEffectsManager epicEffects() {
      return this.epicEffects;
   }

   public QuitPenaltyManager quitPenalty() {
      return this.quitPenalty;
   }

   public CountdownManager countdown() {
      return this.countdown;
   }

   public SpectatorBlockManager spectatorBlock() {
      return this.spectatorBlock;
   }

   public DimensionWrathManager dimensionWrath() {
      return this.dimensionWrath;
   }

   public DeathSummaryManager deathSummary() {
      return this.deathSummary;
   }

   public ElytraWrathManager elytraWrath() {
      return this.elytraWrath;
   }

   public LightningEffectManager lightning() {
      return this.lightning;
   }

   public WitherControlManager witherControl() {
      return this.witherControl;
   }

   public DynamicDifficultyManager dynamicDifficulty() {
      return this.dynamicDifficulty;
   }

   public double getSpeedCap() {
      return this.getConfig().getDouble("difficulty.speed-cap", 1.35);
   }

   public double getMaxHealthCap() {
      return this.getConfig().getDouble("difficulty.max-health-cap", 2048.0);
   }

   public boolean isLevelEnabled() {
      return this.getConfig().getBoolean("difficulty.level.enabled", true);
   }

   public String getLevelFormat() {
      return this.getConfig().getString("difficulty.level.format", "<gray>[<gradient:#ff5555:#ffaa00>Lv. {level}</gradient>] {name}");
   }

   public Messages messages() {
      return this.messages;
   }

   public LocaleManager locales() {
      return this.locales;
   }

   public WorldConfigRegistry worldConfigs() {
      return this.worldConfigs;
   }

   public PlaceholderSnapshotService snapshots() {
      return this.snapshots;
   }

   public StatisticsStore stats() {
      return this.stats;
   }

   public SurvivorManager survivors() {
      return this.survivors;
   }

   public MythicBridge mythic() {
      return this.mythic;
   }

   public DataStore data() {
      return this.data;
   }

   public ItemFactory items() {
      return this.items;
   }

   public LootManager loot() {
      return this.loot;
   }

   public EventManager events() {
      return this.events;
   }

   public ItemAbilities abilities() {
      return this.abilities;
   }

   public BossManager bosses() {
      return this.bosses;
   }

   public ScheduleManager schedule() {
      return this.schedule;
   }
}
