package dev.bloodmoonplus;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public final class SpectatorBlockManager implements Listener {
   private final BloodMoonPlus plugin;
   private boolean enabled = true;
   private boolean kickExisting = true;
   private String bypassPermission = "";
   private String denyMessage;
   private String kickMessage;

   public SpectatorBlockManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("spectator-block.enabled", true);
      this.kickExisting = cfg.getBoolean("spectator-block.kick-existing", true);
      this.bypassPermission = cfg.getString("spectator-block.bypass-permission", "");
      this.denyMessage = cfg.getString(
         "spectator-block.deny-message",
         "<red><bold>☠ The Blood Moon forbids spectator mode! Fight or perish!</bold></red>"
      );
      this.kickMessage = cfg.getString(
         "spectator-block.kick-message",
         "<red><bold>☠ The Blood Moon has risen: spectator mode is off, you are back in survival!</bold></red>"
      );
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isExempt(Player p) {
      if (p == null) {
         return true;
      }
      if (p.isOp()) {
         return true;
      }
      if (this.bypassPermission != null && !this.bypassPermission.isEmpty() && p.hasPermission(this.bypassPermission)) {
         return true;
      }
      return p.hasPermission("bloodmoon.bypass") || p.hasPermission("bloodmoon.admin");
   }

   public boolean isRestricted(World w) {
      boolean countdownRunning = this.plugin.countdown() != null && this.plugin.countdown().isRunning();
      boolean bmActive = (w != null && this.plugin.events().isActive(w)) || this.plugin.events().hasGlobalSession();
      return countdownRunning || bmActive;
   }

   public void kickAllSpectators(World w) {
      if (!this.enabled || !this.kickExisting) {
         return;
      }
      Iterable<? extends Player> players = (w != null && !this.plugin.events().isGlobalMode()) ? w.getPlayers() : Bukkit.getOnlinePlayers();
      for (Player p : players) {
         if (p.getGameMode() == GameMode.SPECTATOR && !this.isExempt(p)) {
            p.setGameMode(GameMode.SURVIVAL);
            if (this.kickMessage != null && !this.kickMessage.isEmpty()) {
               p.sendMessage(Util.mini(this.kickMessage));
            }
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.8F);
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onGameModeChange(PlayerGameModeChangeEvent e) {
      if (!this.enabled || e.getNewGameMode() != GameMode.SPECTATOR) {
         return;
      }
      Player p = e.getPlayer();
      if (this.isExempt(p)) {
         return;
      }
      if (!this.isRestricted(p.getWorld())) {
         return;
      }

      e.setCancelled(true);
      if (this.denyMessage != null && !this.denyMessage.isEmpty()) {
         p.sendMessage(Util.mini(this.denyMessage));
      }
      p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.7F);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
      if (!this.enabled) {
         return;
      }
      String raw = e.getMessage().toLowerCase(Locale.ROOT).trim();
      if (!isSpectatorCommand(raw)) {
         return;
      }
      Player p = e.getPlayer();
      if (this.isExempt(p)) {
         return;
      }
      if (!this.isRestricted(p.getWorld())) {
         return;
      }

      e.setCancelled(true);
      if (this.denyMessage != null && !this.denyMessage.isEmpty()) {
         p.sendMessage(Util.mini(this.denyMessage));
      }
      p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.7F);
   }

   private static boolean isSpectatorCommand(String cmd) {
      if (cmd.startsWith("/")) {
         cmd = cmd.substring(1).trim();
      }
      if (cmd.contains(":")) {
         int colon = cmd.indexOf(':');
         cmd = cmd.substring(colon + 1).trim();
      }

      if (cmd.equals("gm 3") || cmd.startsWith("gm 3 ") ||
          cmd.equals("gm sp") || cmd.startsWith("gm sp ") ||
          cmd.equals("gm spectator") || cmd.startsWith("gm spectator ")) {
         return true;
      }

      if (cmd.equals("gamemode 3") || cmd.startsWith("gamemode 3 ") ||
          cmd.equals("gamemode sp") || cmd.startsWith("gamemode sp ") ||
          cmd.equals("gamemode spectator") || cmd.startsWith("gamemode spectator ")) {
         return true;
      }

      return false;
   }
}
