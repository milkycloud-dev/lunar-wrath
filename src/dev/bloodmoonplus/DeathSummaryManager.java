package dev.bloodmoonplus;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;

public final class DeathSummaryManager implements Listener {
   private final BloodMoonPlus plugin;
   private BukkitTask task;

   private boolean enabled = true;
   private int windowSeconds = 60;
   private String summaryMessage;

   private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
   private final Set<UUID> suppressedNow = ConcurrentHashMap.newKeySet();

   private static final class Window {
      String name;
      long startMs;
      int extraDeaths;
   }

   public DeathSummaryManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.reload();
      this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
   }

   public void reload() {
      FileConfiguration cfg = this.plugin.getConfig();
      this.enabled = cfg.getBoolean("death-summary.enabled", true);
      this.windowSeconds = Math.max(10, cfg.getInt("death-summary.window-seconds", 60));
      this.summaryMessage = cfg.getString(
         "death-summary.message",
         "<dark_red>☠</dark_red> <white><bold>{player}</bold></white> <red>died <yellow>{count}</yellow> more times in the last minute.</red>"
      );
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
      this.windows.clear();
      this.suppressedNow.clear();
   }

   public boolean isSuppressed(Player p) {
      return this.suppressedNow.contains(p.getUniqueId());
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onDeathEarly(PlayerDeathEvent e) {
      Player p = e.getEntity();
      this.suppressedNow.remove(p.getUniqueId());
      if (!this.enabled || this.plugin.events() == null || this.plugin.events().session(p.getWorld()) == null) {
         return;
      }
      long now = System.currentTimeMillis();
      Window w = this.windows.get(p.getUniqueId());
      if (w == null) {
         w = new Window();
         w.name = p.getName();
         w.startMs = now;
         this.windows.put(p.getUniqueId(), w);
         return;
      }
      w.name = p.getName();
      w.extraDeaths++;
      this.suppressedNow.add(p.getUniqueId());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeathLate(PlayerDeathEvent e) {
      if (this.suppressedNow.remove(e.getEntity().getUniqueId())) {
         e.deathMessage(null);
      }
   }

   private void tick() {
      long now = System.currentTimeMillis();
      long windowMs = (long)this.windowSeconds * 1000L;
      Iterator<Map.Entry<UUID, Window>> it = this.windows.entrySet().iterator();
      while (it.hasNext()) {
         Window w = it.next().getValue();
         if (now - w.startMs < windowMs) {
            continue;
         }
         if (w.extraDeaths <= 0) {
            it.remove();
            continue;
         }
         if (this.summaryMessage != null && !this.summaryMessage.isEmpty()) {
            Bukkit.broadcast(Util.mini(this.summaryMessage
               .replace("{player}", w.name)
               .replace("{count}", String.valueOf(w.extraDeaths))
               .replace("{seconds}", String.valueOf(this.windowSeconds))));
         }
         w.extraDeaths = 0;
         w.startMs = now;
      }
   }
}
