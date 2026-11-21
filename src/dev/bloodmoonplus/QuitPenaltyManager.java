package dev.bloodmoonplus;

import java.time.Duration;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class QuitPenaltyManager implements Listener {
   private final BloodMoonPlus plugin;
   private boolean enabled = true;
   private int durationSeconds = 60;
   private boolean exemptCreative = true;
   private String bypassPermission = "bloodmoon.bypass.quitpenalty";
   private String chatWarning;
   private int titleDelaySeconds = 3;
   private String screenTitle;
   private String screenSubtitle;
   private int fadeInTicks = 10;
   private int stayTicks = 120;
   private int fadeOutTicks = 20;
   private String soundName;
   private String joinWarning;
   private String joinTitle;
   private String joinSubtitle;
   private boolean killOnQuit = true;
   private boolean dropItems = true;
   private boolean forceDropItems = false;
   private boolean lightningOnQuit = true;
   private boolean broadcastDeath = true;
   private String deathBroadcastMessage;
   private String reconnectDeathMessage;

   public QuitPenaltyManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("quit-penalty.enabled", true);
      this.durationSeconds = cfg.getInt("quit-penalty.duration-seconds", 60);
      this.exemptCreative = cfg.getBoolean("quit-penalty.exempt-creative", true);
      this.bypassPermission = cfg.getString("quit-penalty.bypass-permission", "bloodmoon.bypass.quitpenalty");

      this.chatWarning = cfg.getString(
         "quit-penalty.announcement.chat",
         "<gradient:#ff0000:#880000><bold>☠ The Blood Moon evolves:</bold></gradient> <red><bold>leave the server after the moon rises and you die!</bold></red>"
      );
      this.titleDelaySeconds = cfg.getInt("quit-penalty.announcement.title-delay-seconds", 3);
      this.screenTitle = cfg.getString("quit-penalty.announcement.title", "<dark_red><bold>DON'T LEAVE!</bold></dark_red>");
      this.screenSubtitle = cfg.getString(
         "quit-penalty.announcement.subtitle",
         "<red>Leaving now means death</red>"
      );
      this.fadeInTicks = cfg.getInt("quit-penalty.announcement.title-times.fade-in", 10);
      this.stayTicks = cfg.getInt("quit-penalty.announcement.title-times.stay", 120);
      this.fadeOutTicks = cfg.getInt("quit-penalty.announcement.title-times.fade-out", 20);
      this.soundName = cfg.getString("quit-penalty.announcement.sound", "entity.ender_dragon.growl");

      this.joinWarning = cfg.getString(
         "quit-penalty.announcement.join-warning",
         "<gradient:#ff0000:#770000><bold>☠ WARNING:</bold></gradient> <red>A Blood Moon is active! Leaving within {remaining}s means instant death!</red>"
      );
      this.joinTitle = cfg.getString("quit-penalty.announcement.join-title", "<dark_red><bold>MOON IS ACTIVE</bold></dark_red>");
      this.joinSubtitle = cfg.getString("quit-penalty.announcement.join-subtitle", "<red>Don't leave for {remaining}s</red>");

      this.killOnQuit = cfg.getBoolean("quit-penalty.on-quit.kill", true);
      this.dropItems = cfg.getBoolean("quit-penalty.on-quit.drop-items", true);
      this.forceDropItems = cfg.getBoolean("quit-penalty.on-quit.force-drop-items", false);
      this.lightningOnQuit = cfg.getBoolean("quit-penalty.on-quit.lightning", true);
      this.broadcastDeath = cfg.getBoolean("quit-penalty.on-quit.broadcast", true);
      this.deathBroadcastMessage = cfg.getString(
         "quit-penalty.on-quit.death-message",
         "<gradient:#ff2222:#880000><bold>☠ <yellow>{player}</yellow> tried to flee the Blood Moon, but death caught up!</gradient>"
      );
      this.reconnectDeathMessage = cfg.getString(
         "quit-penalty.on-quit.reconnect-message",
         "<dark_red><bold>☠ You were executed for fleeing the Blood Moon!</bold></dark_red>"
      );
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public int getDurationSeconds() {
      return this.durationSeconds;
   }

   private boolean isExempt(Player p) {
      if (p == null) {
         return true;
      }
      if (p.isOp()) {
         return true;
      }
      if (this.bypassPermission != null && !this.bypassPermission.isEmpty() && p.hasPermission(this.bypassPermission)) {
         return true;
      }
      if (this.exemptCreative) {
         GameMode gm = p.getGameMode();
         if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return true;
         }
      }
      return false;
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

   public void broadcastEvolutionAlert() {
      if (this.chatWarning != null && !this.chatWarning.isEmpty()) {
         String text = this.chatWarning.replace("{cooldown}", String.valueOf(this.durationSeconds));
         Bukkit.broadcast(Util.mini(text));
      }

      Sound sfx = this.parseSound(this.soundName, Sound.ENTITY_ENDER_DRAGON_GROWL);
      for (Player p : Bukkit.getOnlinePlayers()) {
         if (sfx != null) {
            p.playSound(p.getLocation(), sfx, 1.0F, 0.6F);
         }
      }

      if (this.screenTitle != null && !this.screenTitle.isEmpty()) {
         String tStr = this.screenTitle.replace("{cooldown}", String.valueOf(this.durationSeconds));
         String subStr = (this.screenSubtitle != null ? this.screenSubtitle : "").replace("{cooldown}", String.valueOf(this.durationSeconds));

         Times times = Times.times(
            Duration.ofMillis((long)this.fadeInTicks * 50L),
            Duration.ofMillis((long)this.stayTicks * 50L),
            Duration.ofMillis((long)this.fadeOutTicks * 50L)
         );
         Title t = Title.title(Util.mini(tStr), Util.mini(subStr), times);

         for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(t);
         }
      }
   }

   public void onBloodMoonStart(World w, EventManager.Session s) {
      if (!this.enabled) {
         return;
      }

      Iterable<? extends Player> recipients = (w != null) ? w.getPlayers() : Bukkit.getOnlinePlayers();

      if (this.chatWarning != null && !this.chatWarning.isEmpty()) {
         String text = this.chatWarning.replace("{cooldown}", String.valueOf(this.durationSeconds));
         Component comp = Util.mini(text);
         for (Player p : recipients) {
            p.sendMessage(comp);
         }
      }

      Sound sfx = this.parseSound(this.soundName, Sound.ENTITY_ENDER_DRAGON_GROWL);
      if (sfx != null) {
         for (Player p : recipients) {
            p.playSound(p.getLocation(), sfx, 1.0F, 0.6F);
         }
      }

      long delayTicks = Math.max(0L, (long)this.titleDelaySeconds * 20L);
      Runnable titleTask = () -> {
         if (w != null && !this.plugin.events().isActive(w)) {
            return;
         }
         if (this.screenTitle == null || this.screenTitle.isEmpty()) {
            return;
         }

         String tStr = this.screenTitle.replace("{cooldown}", String.valueOf(this.durationSeconds));
         String subStr = (this.screenSubtitle != null ? this.screenSubtitle : "").replace("{cooldown}", String.valueOf(this.durationSeconds));

         Times times = Times.times(
            Duration.ofMillis((long)this.fadeInTicks * 50L),
            Duration.ofMillis((long)this.stayTicks * 50L),
            Duration.ofMillis((long)this.fadeOutTicks * 50L)
         );
         Title t = Title.title(Util.mini(tStr), Util.mini(subStr), times);

         Iterable<? extends Player> currentRecipients = (w != null) ? w.getPlayers() : Bukkit.getOnlinePlayers();
         for (Player p : currentRecipients) {
            p.showTitle(t);
         }
      };

      if (delayTicks <= 0L) {
         titleTask.run();
      } else {
         Bukkit.getScheduler().runTaskLater(this.plugin, titleTask, delayTicks);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPlayerQuit(PlayerQuitEvent e) {
      if (!this.enabled) {
         return;
      }
      Player p = e.getPlayer();
      if (this.isExempt(p)) {
         return;
      }

      EventManager.Session s = this.plugin.events().session(p.getWorld());
      if (s == null && this.plugin.events().isGlobalMode()) {
         s = this.plugin.events().globalSession();
      }
      if (s == null) {
         return;
      }

      long now = System.currentTimeMillis();
      long elapsedSec = (now - s.startMs) / 1000L;
      if (this.durationSeconds > 0 && elapsedSec >= (long)this.durationSeconds) {
         return;
      }

      Location loc = p.getLocation();
      World world = p.getWorld();

      p.getPersistentDataContainer().set(Keys.QUIT_PENALIZED, PersistentDataType.BYTE, (byte)1);

      if (this.lightningOnQuit && world != null) {
         world.strikeLightningEffect(loc);
      }
      if (world != null) {
         world.playSound(loc, Sound.ENTITY_WITHER_DEATH, 1.0F, 0.8F);
         world.spawnParticle(Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 50, 1.0, 1.0, 1.0, 0.1, new Particle.DustOptions(Color.fromRGB(220, 0, 0), 2.0F));
      }

      if (this.forceDropItems && world != null) {
         for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
               world.dropItemNaturally(loc, item.clone());
            }
         }
         p.getInventory().clear();
      }

      if (this.broadcastDeath && this.deathBroadcastMessage != null && !this.deathBroadcastMessage.isEmpty()) {
         String msg = this.deathBroadcastMessage.replace("{player}", p.getName());
         Bukkit.broadcast(Util.mini(msg));
      }

      if (this.killOnQuit) {
         p.setHealth(0.0);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerDeath(PlayerDeathEvent e) {
      Player p = e.getPlayer();
      if (p.getPersistentDataContainer().has(Keys.QUIT_PENALIZED, PersistentDataType.BYTE)) {
         if (this.broadcastDeath) {
            e.deathMessage(null);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPlayerJoin(PlayerJoinEvent e) {
      Player p = e.getPlayer();
      if (p.getPersistentDataContainer().has(Keys.QUIT_PENALIZED, PersistentDataType.BYTE)) {
         p.getPersistentDataContainer().remove(Keys.QUIT_PENALIZED);
         if (!p.isDead() && p.getHealth() > 0.0) {
            p.setHealth(0.0);
         }
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (p.isOnline() && this.reconnectDeathMessage != null && !this.reconnectDeathMessage.isEmpty()) {
               p.sendMessage(Util.mini(this.reconnectDeathMessage));
            }
         }, 20L);
         return;
      }

      if (!this.enabled || this.isExempt(p)) {
         return;
      }

      EventManager.Session s = this.plugin.events().session(p.getWorld());
      if (s == null && this.plugin.events().isGlobalMode()) {
         s = this.plugin.events().globalSession();
      }
      if (s == null) {
         return;
      }

      long now = System.currentTimeMillis();
      long elapsedSec = (now - s.startMs) / 1000L;
      if (this.durationSeconds <= 0 || elapsedSec < (long)this.durationSeconds) {
         long remaining = this.durationSeconds > 0 ? ((long)this.durationSeconds - elapsedSec) : Math.max(1L, (s.endMs - now) / 1000L);
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!p.isOnline()) {
               return;
            }
            if (this.joinWarning != null && !this.joinWarning.isEmpty()) {
               p.sendMessage(Util.mini(this.joinWarning.replace("{remaining}", String.valueOf(remaining))));
            }
            if (this.joinTitle != null && !this.joinTitle.isEmpty()) {
               String sub = (this.joinSubtitle != null ? this.joinSubtitle : "").replace("{remaining}", String.valueOf(remaining));
               Title t = Title.title(
                  Util.mini(this.joinTitle),
                  Util.mini(sub),
                  Times.times(Duration.ofMillis(500L), Duration.ofSeconds(3L), Duration.ofMillis(500L))
               );
               p.showTitle(t);
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 0.5F);
         }, 25L);
      }
   }
}
