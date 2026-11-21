package dev.bloodmoonplus.integration.mythic;

import dev.bloodmoonplus.BloodMoonPlus;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public final class MythicBridge {
   private final BloodMoonPlus plugin;
   private boolean available = false;
   private Object mythicProvider;
   private Method getMobManager;
   private Method getMythicMobInstance;
   private Method spawnMob;
   private Method getInternalName;
   private Method isPresent;

   public MythicBridge(BloodMoonPlus plugin) {
      this.plugin = plugin;
      if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
         plugin.getLogger().info("MythicMobs is not installed; MythicMobs mode disabled.");
      } else {
         try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method inst = mythicBukkit.getMethod("inst");
            this.mythicProvider = inst.invoke(null);
            this.getMobManager = this.mythicProvider.getClass().getMethod("getMobManager");
            Class<?> mobManagerClass = Class.forName("io.lumine.mythic.core.mobs.MobManager");
            this.getMythicMobInstance = mobManagerClass.getMethod("getMythicMob", String.class);
            Class<?> mythicMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            this.spawnMob = mythicMobClass.getMethod("spawn", LivingEntity.class, Location.class, double.class, double.class);
            this.getInternalName = Class.forName("io.lumine.mythic.core.mobs.MobExecutor").getMethod("getInternalName");
            this.isPresent = Class.forName("io.lumine.mythic.core.mobs.MobManager").getMethod("getMythicMob", String.class);
            this.available = true;
            plugin.getLogger().info("MythicMobs hooked.");
         } catch (Exception var6) {
            plugin.getLogger().log(Level.WARNING, "Could not hook MythicMobs (incompatible version): " + var6.getMessage(), (Throwable)var6);
            this.available = false;
         }
      }
   }

   public boolean isAvailable() {
      return this.available;
   }

   public boolean mobExists(String internalName) {
      if (!this.available) {
         return false;
      } else {
         try {
            Object mm = this.getMobManager.invoke(this.mythicProvider);
            Object mob = this.getMythicMobInstance.invoke(mm, internalName);
            return mob != null;
         } catch (Exception var4) {
            return false;
         }
      }
   }

   public LivingEntity spawn(String internalName, Location loc, double level) {
      if (!this.available) {
         return null;
      } else {
         try {
            Object mm = this.getMobManager.invoke(this.mythicProvider);
            Object mob = this.getMythicMobInstance.invoke(mm, internalName);
            if (mob == null) {
               return null;
            } else {
               Class<?> mythicMobClass = Class.forName("io.lumine.mythic.api.mobs.MythicMob");
               Method spawn = mythicMobClass.getMethod("spawn", LivingEntity.class, Location.class, double.class, double.class);
               Object active = spawn.invoke(mob, (LivingEntity)null, loc, level, level);
               if (active == null) {
                  return null;
               } else {
                  Method getEntity = active.getClass().getMethod("getEntity");
                  return getEntity.invoke(active) instanceof LivingEntity le ? le : null;
               }
            }
         } catch (Exception var13) {
            this.plugin.getLogger().warning("Error spawneando MythicMob '" + internalName + "': " + var13.getMessage());
            return null;
         }
      }
   }

   public String getDisplayName(String internalName) {
      if (!this.available) {
         return internalName;
      } else {
         try {
            Object mm = this.getMobManager.invoke(this.mythicProvider);
            Object mob = this.getMythicMobInstance.invoke(mm, internalName);
            if (mob == null) {
               return internalName;
            } else {
               Class<?> mobClass = Class.forName("io.lumine.mythic.api.mobs.MythicMob");
               Method getDisplay = mobClass.getMethod("getDisplayName");
               Object display = getDisplay.invoke(mob);
               return display == null ? internalName : display.toString().replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
            }
         } catch (Exception var7) {
            return internalName;
         }
      }
   }
}
