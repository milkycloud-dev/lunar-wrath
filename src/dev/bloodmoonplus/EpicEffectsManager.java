package dev.bloodmoonplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class EpicEffectsManager implements Listener {
   private final BloodMoonPlus plugin;
   private final NamespacedKey KEY_INCENDIUM_TYPE;
   private final Map<UUID, Long> lastActionMap = new ConcurrentHashMap<>();
   private final Set<Location> bloodMoonFires = ConcurrentHashMap.newKeySet();
   private long lastAmbientLightningMs = 0L;
   private long lastFireworkBarrageMs = 0L;
   private long lastThunderSoundMs = 0L;

   public EpicEffectsManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
      this.KEY_INCENDIUM_TYPE = new NamespacedKey(plugin, "incendium_type");
      Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickRapidLightning, 20L, 2L);
   }

   private void tickRapidLightning() {
      if (!this.plugin.getConfig().getBoolean("difficulty.epic-effects.lightning.enabled", true)) {
         return;
      }
      if (this.plugin.events() == null) return;
      java.util.Collection<EventManager.Session> sessions = this.plugin.events().sessions();
      if (sessions.isEmpty()) return;

      double minDiff = this.getMinDifficulty();
      double baseRate = this.plugin.getConfig().getDouble("difficulty.epic-effects.lightning.base-strikes-per-sec", 0.46);
      double maxRate = this.plugin.getConfig().getDouble("difficulty.epic-effects.lightning.max-strikes-per-sec", 45.5);
      double minR = this.plugin.getConfig().getDouble("difficulty.epic-effects.lightning.min-radius", 8.0);
      double maxR = this.plugin.getConfig().getDouble("difficulty.epic-effects.lightning.max-radius", 28.0);

      long now = System.currentTimeMillis();

      for (EventManager.Session s : sessions) {
         if (s == null || s.difficulty < minDiff) continue;
         World w = s.world;
         if (w == null) continue;
         List<Player> players = w.getPlayers();
         if (players.isEmpty()) continue;

         double diff = s.difficulty;
         double progress = Math.min(1.0, Math.max(0.0, (diff - minDiff) / Math.max(1.0, 1000.0 - minDiff)));
         double strikesPerSec = baseRate + (maxRate - baseRate) * Math.pow(progress, 0.7);

         double expected = strikesPerSec * 0.1;
         int count = (int) expected;
         if (ThreadLocalRandom.current().nextDouble() < (expected - count)) {
            count++;
         }
         if (count <= 0) continue;

         for (Player p : players) {
            Location pLoc = p.getLocation();
            for (int i = 0; i < count; i++) {
               double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
               double dist = ThreadLocalRandom.current().nextDouble(minR, maxR);
               Location strikeLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
               int highY = w.getHighestBlockYAt(strikeLoc);
               strikeLoc.setY(highY > 0 ? highY : pLoc.getY());

               w.strikeLightningEffect(strikeLoc);

               if (now - this.lastThunderSoundMs >= 320L) {
                  this.lastThunderSoundMs = now;
                  float pitch = 0.65F + ThreadLocalRandom.current().nextFloat() * 0.45F;
                  w.playSound(strikeLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45F, pitch);
               }
            }
         }
      }
   }

   public boolean isBloodMoon(World world) {
      if (world == null || this.plugin.events() == null) return false;
      return this.plugin.events().session(world) != null;
   }

   public Player getNearestPlayer(Location loc, double maxRadius) {
      if (loc == null || loc.getWorld() == null) return null;
      Player nearest = null;
      double maxDistSq = maxRadius * maxRadius;
      double nearestDistSq = maxDistSq;
      for (Player p : loc.getWorld().getPlayers()) {
         if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
         double d = p.getLocation().distanceSquared(loc);
         if (d < nearestDistSq) {
            nearestDistSq = d;
            nearest = p;
         }
      }
      return nearest;
   }

   public double getMinDifficulty() {
      return this.plugin.getConfig().getDouble("difficulty.epic-effects.min-difficulty", 3.0);
   }

   public boolean isEpic(World world) {
      if (world == null) return false;
      EventManager.Session s = this.plugin.events().session(world);
      return s != null && s.difficulty >= this.getMinDifficulty();
   }

   public void tick(EventManager.Session s, long now) {
      if (s == null || s.world == null) return;
      if (s.difficulty < this.getMinDifficulty()) return;
      if (!this.plugin.getConfig().getBoolean("difficulty.epic-effects.enabled", true)) return;

      World w = s.world;
      List<Player> players = w.getPlayers();
      if (players.isEmpty()) return;

      // 1. Weather and red sky (rain ONLY from difficulty 100+, no automatic darkness)
      if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.red-sky.enabled", true)) {
         double rainMinDiff = this.plugin.getConfig().getDouble("difficulty.epic-effects.rain-min-difficulty", 100.0);
         if (s.difficulty >= rainMinDiff) {
            if (!w.hasStorm()) w.setStorm(true);
            if (!w.isThundering()) w.setThundering(true);
         } else {
            if (w.hasStorm()) w.setStorm(false);
            if (w.isThundering()) w.setThundering(false);
         }

         boolean vignette = this.plugin.getConfig().getBoolean("difficulty.epic-effects.red-sky.red-vignette", true);

         for (Player p : players) {
            if (vignette) {
               WorldBorder wb = p.getWorldBorder();
               if (wb == null) {
                  wb = Bukkit.createWorldBorder();
                  p.setWorldBorder(wb);
               }
               wb.setCenter(p.getLocation());
               wb.setSize(59999968.0);
               wb.setWarningDistance(2147483647);
               wb.setDamageAmount(0.0);
               wb.setDamageBuffer(2147483647.0);
            }
            if (p.hasPotionEffect(PotionEffectType.DARKNESS)) {
               p.removePotionEffect(PotionEffectType.DARKNESS);
            }
         }
      }

      // 3. Blood spores and particles around players and mobs
      if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.particles.ambient-blood-spores", true)) {
         if (now / 1000L % 2L == 0L) {
            for (Player p : players) {
               w.spawnParticle(Particle.CRIMSON_SPORE, p.getLocation().add(0, 1.0, 0), 14, 1.2, 0.8, 1.2, 0.02);
               w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.6, 0), 6, 0.8, 0.4, 0.8, 0.01, new DustOptions(Color.fromRGB(220, 10, 10), 1.0F));
            }
         }
      }

      // 4. Firework projectiles fired at players
      if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.fireworks.enabled", true)) {
         long fireworkInterval = this.plugin.getConfig().getLong("difficulty.epic-effects.fireworks.interval-seconds", 12) * 1000L;
         if (now - this.lastFireworkBarrageMs >= fireworkInterval) {
            this.lastFireworkBarrageMs = now;
            double chance = this.plugin.getConfig().getDouble("difficulty.epic-effects.fireworks.launch-at-player-chance", 0.20);
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
               Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
               Location origin = target.getLocation().add(
                  ThreadLocalRandom.current().nextDouble(-10.0, 10.0),
                  ThreadLocalRandom.current().nextDouble(12.0, 18.0),
                  ThreadLocalRandom.current().nextDouble(-10.0, 10.0)
               );
               Vector dir = target.getLocation().add(0, 1.0, 0).toVector().subtract(origin.toVector()).normalize().multiply(0.65);
               Firework fw = (Firework) w.spawnEntity(origin, EntityType.FIREWORK_ROCKET);
               FireworkMeta fm = fw.getFireworkMeta();
               fm.addEffect(FireworkEffect.builder()
                  .with(FireworkEffect.Type.BURST)
                  .withColor(Color.RED, Color.MAROON)
                  .withFade(Color.BLACK)
                  .build());
               fm.setPower(1);
               fw.setFireworkMeta(fm);
               fw.setVelocity(dir);
               fw.setShotAtAngle(true);
               w.playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 0.9F);
            }
         }
      }

      // 5. AI tick for custom Incendium mobs
      this.tickIncendiumMobs(s, now);
   }

   private void tickIncendiumMobs(EventManager.Session s, long now) {
      if (!this.plugin.getConfig().getBoolean("difficulty.incendium.enabled", true)) return;
      World w = s.world;
      for (LivingEntity le : w.getLivingEntities()) {
         if (!EventManager.isTagged(le) || !le.isValid() || le.isDead()) continue;
         PersistentDataContainer pdc = le.getPersistentDataContainer();
         String incType = pdc.get(this.KEY_INCENDIUM_TYPE, PersistentDataType.STRING);
         if (incType == null) continue;

         Player nearest = null;
         double nearestDistSq = Double.MAX_VALUE;
         for (Player p : w.getPlayers()) {
            double d = p.getLocation().distanceSquared(le.getLocation());
            if (d < nearestDistSq) {
               nearestDistSq = d;
               nearest = p;
            }
         }
         if (nearest == null || nearestDistSq > 1024.0) continue; // farther than 32 blocks

         Long lastAction = this.lastActionMap.getOrDefault(le.getUniqueId(), 0L);

         if ("paratrooper".equalsIgnoreCase(incType)) {
            // Piglin paratrooper: long elytra jump and rocket shot
            if (now - lastAction >= 5000L) {
               this.lastActionMap.put(le.getUniqueId(), now);
               Vector toPlayer = nearest.getLocation().toVector().subtract(le.getLocation().toVector()).normalize();
               le.setVelocity(new Vector(toPlayer.getX() * 0.7, 1.15, toPlayer.getZ() * 0.7));
               le.setGliding(true);
               w.playSound(le.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.0F, 0.9F);
               w.spawnParticle(Particle.FIREWORK, le.getLocation(), 12, 0.3, 0.3, 0.3, 0.08);

               final Player targetPlayer = nearest;
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                  if (le.isValid() && !le.isDead()) {
                     Player target = (le instanceof Mob mob && mob.getTarget() instanceof Player pl) ? pl : targetPlayer;
                     if (target != null && target.isValid()) {
                        launchFireworkProjectile(le, target.getLocation().add(0, 0.8, 0));
                     }
                  }
               }, 18L);
            }
         } else if ("pyromancer".equalsIgnoreCase(incType)) {
            // Piglin pyromancer: fan of fire projectiles
            if (now - lastAction >= 4000L && nearestDistSq <= 400.0) {
               this.lastActionMap.put(le.getUniqueId(), now);
               w.playSound(le.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.8F);
               w.spawnParticle(Particle.FLAME, le.getLocation().add(0, 1.2, 0), 16, 0.4, 0.4, 0.4, 0.05);

               Location eyeLoc = le.getEyeLocation();
               Vector dir = nearest.getLocation().add(0, 0.8, 0).toVector().subtract(eyeLoc.toVector()).normalize();
               for (double offsetAngle : new double[]{-0.15, 0.0, 0.15}) {
                  Vector shotDir = dir.clone().rotateAroundY(offsetAngle).multiply(0.75);
                  launchFireworkWithVector(eyeLoc, shotDir, Color.ORANGE, Color.RED);
               }
            }
         }
      }
   }

   private void launchFireworkProjectile(LivingEntity shooter, Location targetLoc) {
      Location origin = shooter.getEyeLocation();
      Vector dir = targetLoc.toVector().subtract(origin.toVector()).normalize().multiply(0.8);
      launchFireworkWithVector(origin, dir, Color.RED, Color.YELLOW);
   }

   private void launchFireworkWithVector(Location origin, Vector vel, Color color1, Color color2) {
      if (origin.getWorld() == null) return;
      Firework fw = (Firework) origin.getWorld().spawnEntity(origin, EntityType.FIREWORK_ROCKET);
      FireworkMeta fm = fw.getFireworkMeta();
      fm.addEffect(FireworkEffect.builder()
         .with(FireworkEffect.Type.BALL)
         .withColor(color1, color2)
         .withFade(Color.BLACK)
         .build());
      fm.setPower(1);
      fw.setFireworkMeta(fm);
      fw.setVelocity(vel);
      fw.setShotAtAngle(true);
   }

   public LivingEntity spawnIncendiumMob(World world, Location loc, String incType, double diff) {
      if (world == null || loc == null) return null;
      LivingEntity ent = null;

      if ("paratrooper".equalsIgnoreCase(incType)) {
         PiglinBrute pb = (PiglinBrute) world.spawnEntity(loc, EntityType.PIGLIN_BRUTE);
         pb.setImmuneToZombification(true);
         pb.setAdult();
         EntityEquipment eq = pb.getEquipment();
         if (eq != null) {
            eq.setChestplate(new ItemStack(Material.ELYTRA));
            eq.setItemInMainHand(new ItemStack(Material.CROSSBOW));
            eq.setChestplateDropChance(0.0F);
            eq.setItemInMainHandDropChance(0.0F);
         }
         pb.addScoreboardTag("bm.paratrooper");
         pb.addScoreboardTag("bm.incendium");
         ent = pb;
      } else if ("pyromancer".equalsIgnoreCase(incType)) {
         PiglinBrute pb = (PiglinBrute) world.spawnEntity(loc, EntityType.PIGLIN_BRUTE);
         pb.setImmuneToZombification(true);
         pb.setAdult();
         EntityEquipment eq = pb.getEquipment();
         if (eq != null) {
            eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
            eq.setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
            eq.setBoots(new ItemStack(Material.GOLDEN_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.BLAZE_ROD));
            eq.setHelmetDropChance(0.0F);
            eq.setChestplateDropChance(0.0F);
            eq.setItemInMainHandDropChance(0.0F);
         }
         pb.addScoreboardTag("bm.pyro");
         pb.addScoreboardTag("bm.incendium");
         ent = pb;
      } else { // "crossbowman"
         Piglin p = (Piglin) world.spawnEntity(loc, EntityType.PIGLIN);
         p.setImmuneToZombification(true);
         p.setAdult();
         EntityEquipment eq = p.getEquipment();
         if (eq != null) {
            ItemStack cb = new ItemStack(Material.CROSSBOW);
            if (cb.getItemMeta() instanceof CrossbowMeta cm) {
               ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
               cm.addChargedProjectile(rocket);
               cb.setItemMeta(cm);
            }
            eq.setItemInMainHand(cb);
            eq.setItemInOffHand(new ItemStack(Material.FIREWORK_ROCKET, 64));
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setItemInMainHandDropChance(0.0F);
            eq.setHelmetDropChance(0.0F);
         }
         p.addScoreboardTag("bm.crossbowman");
         p.addScoreboardTag("bm.incendium");
         ent = p;
      }

      if (ent != null) {
         PersistentDataContainer pdc = ent.getPersistentDataContainer();
         pdc.set(Keys.MOB, PersistentDataType.BYTE, (byte)1);
         pdc.set(Keys.DIFF, PersistentDataType.DOUBLE, diff);
         pdc.set(this.KEY_INCENDIUM_TYPE, PersistentDataType.STRING, incType);

         int mobLevel = Math.max(1, (int)Math.round(diff));
         if (Keys.LEVEL != null) {
            pdc.set(Keys.LEVEL, PersistentDataType.INTEGER, mobLevel);
         }
         try {
            pdc.set(new NamespacedKey("levelledmobs", "level"), PersistentDataType.INTEGER, mobLevel);
         } catch (Exception ignored) {
         }

         AttributeInstance hp = ent.getAttribute(Attribute.MAX_HEALTH);
         if (hp != null) {
            double baseHp = "paratrooper".equalsIgnoreCase(incType) ? 60.0 : ("pyromancer".equalsIgnoreCase(incType) ? 75.0 : 45.0);
            double scaledHp = baseHp * this.plugin.scaled(diff, "health");
            double cap = this.plugin.getMaxHealthCap();
            double v = cap > 0.0 ? Math.min(cap, scaledHp) : scaledHp;
            hp.setBaseValue(v);
            ent.setHealth(Math.min(v, hp.getValue()));
         }

         AttributeInstance sp = ent.getAttribute(Attribute.MOVEMENT_SPEED);
         if (sp != null) {
            double speedMult = this.plugin.scaled(diff, "speed");
            double speedCap = this.plugin.getSpeedCap();
            if (speedCap > 0.0) {
               speedMult = Math.min(speedCap, speedMult);
            }
            sp.setBaseValue(sp.getBaseValue() * speedMult);
         }

         String rawName = "paratrooper".equalsIgnoreCase(incType)
            ? this.plugin.locales().get("mob.paratrooper")
            : ("pyromancer".equalsIgnoreCase(incType)
               ? this.plugin.locales().get("mob.pyromancer")
               : this.plugin.locales().get("mob.crossbowman"));

         String formattedName = rawName;
         if (this.plugin.isLevelEnabled()) {
            String lvlFmt = this.plugin.getLevelFormat();
            formattedName = lvlFmt.replace("{level}", String.valueOf(mobLevel)).replace("{name}", rawName);
         }
         ent.customName(Util.mini(formattedName));
         ent.setCustomNameVisible(true);

         ent.setRemoveWhenFarAway(false);
         ent.setPersistent(false);
      }

      return ent;
   }

   public void onSessionStop(World world) {
      this.cleanUpFires(world);
      this.clearWorldBorders(world);
      if (world != null) {
         world.setStorm(false);
         world.setThundering(false);
      }
   }

   public void cleanUpFires(World world) {
      for (Location loc : new ArrayList<>(this.bloodMoonFires)) {
         if (world == null || loc.getWorld().equals(world)) {
            Block b = loc.getBlock();
            if (b.getType() == Material.FIRE || b.getType() == Material.SOUL_FIRE) {
               b.setType(Material.AIR);
            }
            this.bloodMoonFires.remove(loc);
         }
      }
      if (world != null) {
         Location spawn = world.getSpawnLocation();
         int sx = spawn.getBlockX();
         int sz = spawn.getBlockZ();
         for (int x = sx - 60; x <= sx + 60; x++) {
            for (int z = sz - 60; z <= sz + 60; z++) {
               int hy = world.getHighestBlockYAt(x, z);
               for (int y = Math.max(world.getMinHeight(), hy - 4); y <= Math.min(world.getMaxHeight(), hy + 4); y++) {
                  Block b = world.getBlockAt(x, y, z);
                  if (b.getType() == Material.FIRE || b.getType() == Material.SOUL_FIRE) {
                     b.setType(Material.AIR);
                  }
               }
            }
         }
      }
   }

   public void clearWorldBorders(World world) {
      if (world == null) {
         for (Player p : Bukkit.getOnlinePlayers()) {
            p.setWorldBorder(null);
            p.removePotionEffect(PotionEffectType.DARKNESS);
         }
      } else {
         for (Player p : world.getPlayers()) {
            p.setWorldBorder(null);
            p.removePotionEffect(PotionEffectType.DARKNESS);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onEntityChangeBlock(EntityChangeBlockEvent e) {
      if (e.getTo() == Material.FIRE || e.getTo() == Material.SOUL_FIRE) {
         if (isEpic(e.getBlock().getWorld())) {
            this.bloodMoonFires.add(e.getBlock().getLocation());
            if (this.plugin.getConfig().getBoolean("difficulty.incendium.pyromancer.temp-fire", true)) {
               Location loc = e.getBlock().getLocation();
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                  Block b = loc.getBlock();
                  if (b.getType() == Material.FIRE || b.getType() == Material.SOUL_FIRE) {
                     b.setType(Material.AIR);
                     this.bloodMoonFires.remove(loc);
                  }
               }, 120L); // put out automatically after 6 seconds
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockIgnite(BlockIgniteEvent e) {
      if (isEpic(e.getBlock().getWorld())) {
         this.bloodMoonFires.add(e.getBlock().getLocation());
         if (this.plugin.getConfig().getBoolean("difficulty.incendium.pyromancer.temp-fire", true)) {
            Location loc = e.getBlock().getLocation();
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               Block b = loc.getBlock();
               if (b.getType() == Material.FIRE || b.getType() == Material.SOUL_FIRE) {
                  b.setType(Material.AIR);
                  this.bloodMoonFires.remove(loc);
               }
            }, 120L);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockSpread(BlockSpreadEvent e) {
      if (isEpic(e.getBlock().getWorld())) {
         if (e.getNewState().getType() == Material.FIRE || e.getNewState().getType() == Material.SOUL_FIRE) {
            this.bloodMoonFires.add(e.getBlock().getLocation());
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onBlockBurn(BlockBurnEvent e) {
      if (isEpic(e.getBlock().getWorld())) {
         e.setCancelled(true); // fire never destroys blocks
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent e) {
      e.getPlayer().setWorldBorder(null);
      e.getPlayer().removePotionEffect(PotionEffectType.DARKNESS);
   }

   @EventHandler
   public void onPlayerChangeWorld(PlayerChangedWorldEvent e) {
      e.getPlayer().setWorldBorder(null);
      e.getPlayer().removePotionEffect(PotionEffectType.DARKNESS);
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onEntityDeath(EntityDeathEvent e) {
      LivingEntity ent = e.getEntity();
      if (EventManager.isTagged(ent)) {
         PersistentDataContainer pdc = ent.getPersistentDataContainer();
         String incType = pdc.get(this.KEY_INCENDIUM_TYPE, PersistentDataType.STRING);
         if ("pyromancer".equalsIgnoreCase(incType)) {
            // Pyromancer died: put out fire within 15 blocks
            Location loc = ent.getLocation();
            World w = loc.getWorld();
            if (w != null) {
               int cx = loc.getBlockX();
               int cy = loc.getBlockY();
               int cz = loc.getBlockZ();
               for (int x = cx - 15; x <= cx + 15; x++) {
                  for (int y = Math.max(w.getMinHeight(), cy - 6); y <= Math.min(w.getMaxHeight(), cy + 6); y++) {
                     for (int z = cz - 15; z <= cz + 15; z++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (b.getType() == Material.FIRE || b.getType() == Material.SOUL_FIRE) {
                           b.setType(Material.AIR);
                           this.bloodMoonFires.remove(b.getLocation());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onEntityTarget(EntityTargetLivingEntityEvent e) {
      if (!(e.getEntity() instanceof Mob mob)) return;
      World w = mob.getWorld();
      if (!isBloodMoon(w)) return;
      if (!this.plugin.getConfig().getBoolean("difficulty.epic-effects.prevent-mob-infighting", true)) return;

      LivingEntity target = e.getTarget();
      if (target == null) return;

      // Target is already a player, nothing to do
      if (target instanceof Player) return;

      // Mob tries to target another mob or ally:
      if (target instanceof Mob) {
         Player nearest = getNearestPlayer(mob.getLocation(), 48.0);
         if (nearest != null) {
            e.setTarget(nearest);
         } else {
            e.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCombatDamage(EntityDamageByEntityEvent e) {
      Entity victim = e.getEntity();
      World w = victim.getWorld();
      if (!isBloodMoon(w)) return;

      Entity damager = e.getDamager();
      Entity shooter = null;
      if (damager instanceof Projectile proj) {
         ProjectileSource ps = proj.getShooter();
         if (ps instanceof Entity src) {
            shooter = src;
         }
      }
      Entity directOrShooter = shooter != null ? shooter : damager;

      // Prevent mob infighting (friendly fire) during the moon:
      // Skeletons, zombies and piglins must not hit each other or lose focus on players
      if (victim instanceof Mob victimMob && directOrShooter instanceof Mob) {
         if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.prevent-mob-infighting", true)) {
            e.setCancelled(true);
            if (damager instanceof Projectile proj) {
               proj.remove();
            }
            if (!(victimMob.getTarget() instanceof Player)) {
               Player nearest = getNearestPlayer(victimMob.getLocation(), 48.0);
               if (nearest != null) {
                  victimMob.setTarget(nearest);
               }
            }
            return;
         }
      }

      if (!(victim instanceof Player target)) return;
      if (!isEpic(target.getWorld())) return;

      // 1. Levitation from skeleton arrows for 2 seconds (40 ticks)
      if (shooter instanceof AbstractSkeleton skeleton && EventManager.isTagged(skeleton)) {
         if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.mob-abilities.skeleton-levitation.enabled", true)) {
            double chance = this.plugin.getConfig().getDouble("difficulty.epic-effects.mob-abilities.skeleton-levitation.chance", 0.20);
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
               int ticks = this.plugin.getConfig().getInt("difficulty.epic-effects.mob-abilities.skeleton-levitation.duration-ticks", 40);
               int amp = this.plugin.getConfig().getInt("difficulty.epic-effects.mob-abilities.skeleton-levitation.amplifier", 0);
               target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, ticks, amp));
               target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1.0, 0), 20, 0.4, 0.6, 0.4, 0.1);
               target.getWorld().playSound(target.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.0F, 1.2F);
            }
         }
      }

      // 2. Zombie shock hit (lightning and bonus damage)
      if (directOrShooter instanceof Zombie zombie && EventManager.isTagged(zombie)) {
         if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.mob-abilities.zombie-lightning.enabled", true)) {
            double chance = this.plugin.getConfig().getDouble("difficulty.epic-effects.mob-abilities.zombie-lightning.chance", 0.15);
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
               target.getWorld().strikeLightningEffect(target.getLocation());
               target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7F, 1.4F);
               e.setDamage(e.getDamage() + this.plugin.getConfig().getDouble("difficulty.epic-effects.mob-abilities.zombie-lightning.bonus-damage", 2.5));
            }
         }
      }

      // 3. Slowness and darkness from spiders
      if (directOrShooter instanceof Spider spider && EventManager.isTagged(spider)) {
         if (this.plugin.getConfig().getBoolean("difficulty.epic-effects.mob-abilities.spider-slowness.enabled", true)) {
            double chance = this.plugin.getConfig().getDouble("difficulty.epic-effects.mob-abilities.spider-slowness.chance", 0.20);
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
               int ticks = this.plugin.getConfig().getInt("difficulty.epic-effects.mob-abilities.spider-slowness.duration-ticks", 60);
               target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1));
               target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0));
               target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.05);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCreeperExplode(EntityExplodeEvent e) {
      if (e.getEntity() instanceof Creeper creeper && EventManager.isTagged(creeper)) {
         // Block protection
         e.blockList().clear();
         e.setYield(0.0F);

         if (isEpic(creeper.getWorld()) && this.plugin.getConfig().getBoolean("difficulty.epic-effects.mob-abilities.creeper-fireworks.enabled", true)) {
            Location loc = creeper.getLocation();
            int count = this.plugin.getConfig().getInt("difficulty.epic-effects.mob-abilities.creeper-fireworks.rocket-count", 3);
            for (int i = 0; i < count; i++) {
               Vector vel = new Vector(
                  ThreadLocalRandom.current().nextDouble(-0.35, 0.35),
                  ThreadLocalRandom.current().nextDouble(0.7, 1.1),
                  ThreadLocalRandom.current().nextDouble(-0.35, 0.35)
               );
               launchFireworkWithVector(loc, vel, Color.RED, Color.MAROON);
            }
         }
      }
   }
}
