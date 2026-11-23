package dev.bloodmoonplus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

public final class EventListener implements Listener {
   private final BloodMoonPlus plugin;

   EventListener(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   private static Entity source(Entity damager) {
      if (damager instanceof org.bukkit.entity.ComplexEntityPart part) {
         return part.getParent();
      }
      if (damager instanceof org.bukkit.entity.AreaEffectCloud cloud && cloud.getSource() instanceof Entity owner) {
         return owner;
      }
      if (damager instanceof Projectile p) {
         ProjectileSource var3 = p.getShooter();
         if (var3 instanceof Entity) {
            return (Entity)var3;
         }
      }

      return damager;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onMobDamage(EntityDamageByEntityEvent e) {
      if (BossManager.isAbilityHit()) {
         return;
      }
      if (source(e.getDamager()) instanceof LivingEntity le && EventManager.isTagged(le)) {
         Double scale = (Double)le.getPersistentDataContainer().get(Keys.SCALE, PersistentDataType.DOUBLE);
         if (scale != null && scale != 1.0) {
            e.setDamage(e.getDamage() * scale);
         }

         return;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onBossHitCap(EntityDamageByEntityEvent e) {
      if (!(e.getEntity() instanceof Player victim)) {
         return;
      }
      Entity src = source(e.getDamager());
      BossManager.BossDef d = this.plugin.bosses().defOf(src);
      double raw;
      double cap;
      if (d != null) {
         raw = this.plugin.bosses().rawCap(d);
         cap = this.plugin.bosses().hitCap(d, victim);
      } else if (src != null && EventManager.isTagged(src)) {
         raw = this.plugin.getConfig().getDouble("horde.max-damage", 0.0);
         cap = BossManager.finalCap(
            this.plugin.getConfig().getDouble("horde.max-hit", 0.0), this.plugin.getConfig().getDouble("horde.max-hit-fraction", 0.0), victim
         );
      } else {
         return;
      }
      if (raw > 0.0 && e.getDamage() > raw) {
         e.setDamage(raw);
      }
      for (int i = 0; i < 4 && e.getFinalDamage() > cap && e.getDamage() > 0.0; i++) {
         e.setDamage(e.getDamage() * cap / e.getFinalDamage());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onWitherSpawn(CreatureSpawnEvent e) {
      if (e.getEntityType() != EntityType.WITHER) {
         return;
      }

      if (BossManager.isSpawningBoss() || e.getEntity().getScoreboardTags().contains("bloodmoon_boss") || this.plugin.bosses().isBoss(e.getEntity())) {
         e.setCancelled(false);
         return;
      }

      World w = e.getLocation().getWorld();
      if (w == null) {
         return;
      }

      boolean isActive = this.plugin.events().isActive(w);

      if (this.plugin.witherControl() != null && this.plugin.witherControl().isControlledWorld(w)) {
         if (this.plugin.witherControl().isAllowOnlyDuringBloodMoon()) {
            if (!isActive) {
               e.setCancelled(true);
               Location loc = e.getLocation();
               for (Player p : w.getPlayers()) {
                  if (p.getLocation().distanceSquared(loc) <= 2304.0) {
                     p.sendMessage(this.plugin.messages().prefixed("event.wither-only-bloodmoon"));
                  }
               }
            }
            return;
         }
      }

      if (this.plugin.events().blockWitherSpawn() && isActive) {
         e.setCancelled(true);
         Location loc = e.getLocation();
         for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 2304.0) {
               p.sendMessage(this.plugin.messages().prefixed("event.wither-blocked"));
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onMobExplode(EntityExplodeEvent e) {
      Entity ent = e.getEntity();
      Entity src = source(ent);
      boolean isBossOrSpecial = ent instanceof EnderDragon || ent instanceof Wither ||
                                ent instanceof WitherSkull || ent instanceof DragonFireball ||
                                src instanceof EnderDragon || src instanceof Wither ||
                                this.plugin.bosses().isBoss(src) || this.plugin.bosses().isBoss(ent) ||
                                (ent != null && ent.getScoreboardTags().contains("bloodmoon_boss")) ||
                                (src != null && src.getScoreboardTags().contains("bloodmoon_boss"));
      if (isBossOrSpecial) {
         e.blockList().clear();
         e.setYield(0.0F);
         return;
      }
      if (this.plugin.events().protectTerrain()) {
         World w = e.getLocation().getWorld();
         if (w != null && this.plugin.events().isActive(w)) {
            e.blockList().clear();
            e.setYield(0.0F);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onExplosionPrime(ExplosionPrimeEvent e) {
      Entity ent = e.getEntity();
      Entity src = source(ent);
      boolean isBossOrSpecial = ent instanceof EnderDragon || ent instanceof Wither ||
                                ent instanceof WitherSkull || ent instanceof DragonFireball ||
                                src instanceof EnderDragon || src instanceof Wither ||
                                this.plugin.bosses().isBoss(src) || this.plugin.bosses().isBoss(ent) ||
                                (ent != null && ent.getScoreboardTags().contains("bloodmoon_boss")) ||
                                (src != null && src.getScoreboardTags().contains("bloodmoon_boss"));
      if (isBossOrSpecial) {
         e.setFire(false);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onBlockExplode(BlockExplodeEvent e) {
      if (this.plugin.events().protectTerrain() && this.plugin.events().isActive(e.getBlock().getWorld())) {
         e.blockList().clear();
         e.setYield(0.0F);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onEntityChangeBlock(EntityChangeBlockEvent e) {
      Entity ent = e.getEntity();
      if (ent instanceof EnderDragon || ent instanceof Wither || ent instanceof WitherSkull) {
         e.setCancelled(true);
         return;
      }
      if (this.plugin.bosses().isBoss(ent) || (ent != null && ent.getScoreboardTags().contains("bloodmoon_boss"))) {
         e.setCancelled(true);
         return;
      }
      if (this.plugin.events().protectTerrain() && EventManager.isTagged(ent)) {
         World w = e.getBlock().getWorld();
         if (w != null && this.plugin.events().isActive(w)) {
            e.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDragonChangePhase(EnderDragonChangePhaseEvent e) {
      EnderDragon dragon = e.getEntity();
      if (this.plugin.bosses().isBoss(dragon) || dragon.getScoreboardTags().contains("bloodmoon_boss")) {
         if (e.getNewPhase() == EnderDragon.Phase.FLY_TO_PORTAL || e.getNewPhase() == EnderDragon.Phase.LAND_ON_PORTAL) {
            e.setNewPhase(EnderDragon.Phase.CHARGE_PLAYER);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onEntityTarget(EntityTargetLivingEntityEvent e) {
      if (e.getTarget() instanceof Player p) {
         if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
            e.setCancelled(true);
            return;
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onBossDamaged(EntityDamageByEntityEvent e) {
      if (this.plugin.bosses().isBoss(e.getEntity())) {
         if (source(e.getDamager()) instanceof Player p) {
            this.plugin.bosses().addDamage(e.getEntity(), p, e.getFinalDamage());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDeath(EntityDeathEvent e) {
      LivingEntity ent = e.getEntity();
      if (EventManager.isTagged(ent)) {
         if (ent.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) {
            this.plugin.bosses().handleDeath(ent, e);
         } else {
            Player killer = ent.getKiller();
            EventManager.Session s = this.plugin.events().session(ent.getWorld());
            if (killer != null && s != null) {
               s.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            }

            if (!this.plugin.events().keepVanillaDrops()) {
               e.getDrops().clear();
            }

            if (killer != null || !this.plugin.getConfig().getBoolean("loot.require-player-kill", true)) {
               PersistentDataContainer pdc = ent.getPersistentDataContainer();
               String table = (String)pdc.get(Keys.TABLE, PersistentDataType.STRING);
               if (table == null) {
                  table = this.plugin.events().defaultTable();
               }

               double diff = (Double)pdc.getOrDefault(Keys.DIFF, PersistentDataType.DOUBLE, 1.0);
               List<ItemStack> loot = this.plugin.loot().roll(table, killer, diff, true);
               Location loc = ent.getLocation();
               boolean forceAll = this.plugin.getConfig().getBoolean("loot.direct-to-inventory", false);

               for (ItemStack it : loot) {
                  boolean direct = killer != null && (forceAll || this.plugin.loot().directToInventory(this.plugin.loot().rarityOf(it)));
                  if (direct) {
                     this.plugin.loot().giveOrDrop(killer, it);
                  } else {
                     this.plugin.loot().dropAt(loc, it);
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onPlayerDeath(PlayerDeathEvent e) {
      EventManager.Session s = this.plugin.events().session(e.getEntity().getWorld());
      if (s != null) {
         Player p = e.getEntity();
         s.dead.add(p.getUniqueId());
         s.died.add(p.getUniqueId());
         World w = p.getWorld();
         w.strikeLightningEffect(p.getLocation());
         w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7F, 0.5F);

         if (p.getPersistentDataContainer().has(Keys.QUIT_PENALIZED, PersistentDataType.BYTE)) {
            return;
         }

         if (this.plugin.deathSummary() != null && this.plugin.deathSummary().isSuppressed(p)) {
            e.deathMessage(null);
            return;
         }

         if (this.plugin.messages().announce("player-deaths", true)) {
            String rawCause = null;
            if (e.deathMessage() != null) {
               rawCause = PlainTextComponentSerializer.plainText().serialize(e.deathMessage());
            } else if (e.getDeathMessage() != null) {
               rawCause = e.getDeathMessage();
            }

            String cause;
            if (rawCause != null && !rawCause.trim().isEmpty()) {
               String pName = p.getName();
               if (rawCause.startsWith(pName + " ")) {
                  cause = rawCause.substring(pName.length() + 1).trim();
               } else {
                  cause = rawCause.trim();
               }
            } else {
               cause = this.plugin.locales().get("event.death-cause-unknown");
            }

            if (rawCause != null && !rawCause.trim().isEmpty()) {
               cause = Util.MM.escapeTags(cause.replace('§', '&'));
            }
            e.deathMessage(null);
            this.plugin.messages().all("event.player-died", "player", p.getName(), "cause", cause);

            String actionbarText = this.plugin.locales().get("event.player-died-actionbar", "player", p.getName());
            if (!this.plugin.locales().has("event.player-died-actionbar")) {
               actionbarText = "<dark_red>☠ <white>" + p.getName() + "</white> died during the Blood Moon!</dark_red>";
            }
            Component abComp = Util.mini(actionbarText);

            for (Player wp : w.getPlayers()) {
               wp.playSound(wp.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 0.6F);
               wp.sendActionBar(abComp);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onMobDeathAnnounce(EntityDeathEvent e) {
      if (this.plugin.messages().announce("mob-kills", false)) {
         if (EventManager.isTagged(e.getEntity())) {
            if (!e.getEntity().getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) {
               if (ThreadLocalRandom.current().nextInt(5) == 0) {
                  Player killer = e.getEntity().getKiller();
                  if (killer != null) {
                     String mob = e.getEntity().customName() != null
                        ? Util.MM.escapeTags(PlainTextComponentSerializer.plainText().serialize(e.getEntity().customName()).replace('§', '&'))
                        : "<lang:" + e.getEntity().getType().translationKey() + ">";
                     this.plugin.messages().all("event.mob-slain", "player", killer.getName(), "mob", mob);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onEntitiesLoad(EntitiesLoadEvent e) {
      boolean active = this.plugin.events().isActive(e.getWorld());
      for (Entity ent : e.getEntities()) {
         if (!active && ent instanceof LivingEntity && EventManager.isTagged(ent)) {
            ent.remove();
         } else {
            this.plugin.bosses().dropOrphan(ent);
         }
      }
   }

   @EventHandler
   public void onPlayerRespawn(PlayerRespawnEvent e) {
      EventManager.Session s = this.plugin.events().session(e.getPlayer().getWorld());
      if (s != null) {
         s.dead.remove(e.getPlayer().getUniqueId());
      }

      this.plugin.abilities().cleanStalePassives(e.getPlayer());
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent e) {
      EventManager.Session s = this.plugin.events().session(e.getFrom());
      if (s != null) {
         e.getPlayer().hideBossBar(s.bar);
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent e) {
      EventManager.Session s = this.plugin.events().session(e.getPlayer().getWorld());
      if (s != null) {
         s.dead.remove(e.getPlayer().getUniqueId());
         s.participants.remove(e.getPlayer().getUniqueId());
      }
   }

   @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent e) {
      Player p = e.getPlayer();
      World w = p.getWorld();
      if (!this.plugin.events().isActive(w)) {
         return;
      }

      boolean allow = this.plugin.getConfig().getBoolean("block-placement.allow", true);
      if (allow) {
         return;
      }

      boolean exemptCreative = this.plugin.getConfig().getBoolean("block-placement.exempt-creative", true);
      if (exemptCreative && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
         return;
      }

      String bypass = this.plugin.getConfig().getString("block-placement.bypass-permission", "bloodmoon.bypass.blockplace");
      if (bypass != null && !bypass.isEmpty() && p.hasPermission(bypass)) {
         return;
      }

      e.setCancelled(true);
      String msg = this.plugin.getConfig().getString(
         "block-placement.deny-message",
         "<red>☠ The Blood Moon's dark magic binds the ground: building is forbidden!</red>"
      );
      if (msg != null && !msg.isEmpty()) {
         p.sendMessage(Util.mini(msg));
      }
      p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6F, 0.5F);
   }
}
