package dev.bloodmoonplus;

import dev.bloodmoonplus.config.WorldConfig;
import dev.bloodmoonplus.placeholder.BossPlaceholderSnapshot;
import dev.bloodmoonplus.placeholder.BossPlaceholderState;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class BossManager {
   private final BloodMoonPlus plugin;
   private final Map<String, BossManager.BossDef> defs = new LinkedHashMap<>();
   private final List<BossManager.Spawn> spawns = new ArrayList<>();
   private final Map<UUID, BossManager.Instance> alive = new HashMap<>();
   private long defeatedCount = 0L;
   private static volatile boolean spawningBoss = false;
   private static boolean abilityHit = false;

   static boolean isAbilityHit() {
      return abilityHit;
   }

   static boolean isSpawningBoss() {
      return spawningBoss;
   }

   BossManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   void reload() {
      this.defs.clear();
      this.spawns.clear();
      YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "bosses.yml"));
      ConfigurationSection bs = y.getConfigurationSection("bosses");
      if (bs != null) {
         Iterator var3 = bs.getKeys(false).iterator();

         label66:
         while (true) {
            String id;
            ConfigurationSection s;
            EntityType type;
            Color color;
            Overlay overlay;
            while (true) {
               if (!var3.hasNext()) {
                  break label66;
               }

               id = (String)var3.next();
               s = bs.getConfigurationSection(id);
               if (s != null) {
                  try {
                     type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
                     color = Color.valueOf(s.getString("bar-color", "RED").toUpperCase(Locale.ROOT));
                     overlay = Overlay.valueOf(s.getString("bar-overlay", "NOTCHED_10").toUpperCase(Locale.ROOT));
                     break;
                  } catch (IllegalArgumentException var15) {
                     this.plugin.getLogger().warning("bosses.yml: invalid value in '" + id + "': " + var15.getMessage());
                  }
               }
            }

            List<BossManager.Ability> abilities = new ArrayList<>();

            for (String a : s.getStringList("abilities")) {
               String[] p = a.split(":");
               if (p.length >= 2) {
                  try {
                     abilities.add(
                        new BossManager.Ability(p[0].trim().toUpperCase(Locale.ROOT), Double.parseDouble(p[1].trim()), Arrays.copyOfRange(p, 2, p.length))
                     );
                  } catch (NumberFormatException var14) {
                     this.plugin.getLogger().warning("bosses.yml: invalid ability '" + a + "' in " + id);
                  }
               }
            }

            ConfigurationSection eqSec = s.getConfigurationSection("equipment");
            this.defs
               .put(
                  id,
                  new BossManager.BossDef(
                     id,
                     s.getString("name", id),
                     type,
                     s.getDouble("health", 200.0),
                     s.getDouble("damage", 1.0),
                     s.getDouble("speed", 1.0),
                     s.getDouble("scale", 0.0),
                     s.getDouble("knockback-resistance", 0.0),
                     color,
                     overlay,
                     EventManager.parseEquipment(eqSec == null ? null : eqSec.getValues(false)),
                     abilities,
                     s.getString("loot-table"),
                     s.getString("top-damage-table"),
                     s.getBoolean("announce", true),
                     s.getInt("exp", 0),
                     s.getDouble("enrage-at", 0.0),
                     s.getDouble("enrage-cooldown", 0.6),
                     s.getDouble("enrage-damage", 1.5),
                     s.getDouble("ability-damage", 1.0),
                     s.getDouble("max-hit", -1.0),
                     s.getDouble("max-damage", -1.0),
                     s.getDouble("bar-radius", -1.0),
                     s.getString("skin")
                  )
               );
         }
      }

      for (Map<?, ?> m : y.getMapList("spawns")) {
         Object sc = m.get("scale-count");
         this.spawns
            .add(
               new BossManager.Spawn(
                  Util.str(m, "boss", ""),
                  (int)Util.num(m, "delay-seconds", 60.0),
                  Util.num(m, "chance", 1.0),
                  Util.num(m, "min-difficulty", 1.0),
                  (int)Util.num(m, "count", 1.0),
                  sc != null && Boolean.parseBoolean(sc.toString())
               )
            );
      }

      this.plugin.getLogger().info("Bosses loaded: " + this.defs.size());
   }

   public Set<String> ids() {
      return this.defs.keySet();
   }

   public boolean isBoss(Entity e) {
      return this.alive.containsKey(e.getUniqueId());
   }

   void plan(EventManager.Session s, boolean resumed) {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();

      for (BossManager.Spawn sp : this.spawns) {
         if (this.defs.containsKey(sp.boss()) && !(s.difficulty < sp.minDifficulty()) && !(rnd.nextDouble() > sp.chance())) {
            int n = sp.count();
            if (sp.scaleCount()) {
               int maxBossCount = this.plugin.getConfig().getInt("difficulty.bosses.scale-count-max", 1);
               int scaledCount = Math.max(1, (int)Math.round((double)n * Math.sqrt(s.difficulty)));
               n = maxBossCount > 0 ? Math.min(maxBossCount, scaledCount) : scaledCount;
            }

            long at = s.startMs + (long)sp.delay() * 1000L;
            if (!resumed || at >= System.currentTimeMillis()) {
               for (int i = 0; i < n; i++) {
                  s.pendingBosses.add(new BossManager.Pending(sp.boss(), at + (long)i * 2000L));
               }
            }
         }
      }
   }

   void tickSpawns(EventManager.Session s, long now) {
      if (!s.pendingBosses.isEmpty()) {
         List<BossManager.Pending> retry = new ArrayList<>();
         Iterator<BossManager.Pending> it = s.pendingBosses.iterator();

         while (it.hasNext()) {
            BossManager.Pending p = it.next();
            if (now >= p.atMs()) {
               it.remove();
               if (!this.spawn(s, p.bossId())) {
                  retry.add(new BossManager.Pending(p.bossId(), now + 10000L));
               }
            }
         }

         s.pendingBosses.addAll(retry);
      }
   }

   public boolean spawn(EventManager.Session s, String bossId) {
      BossManager.BossDef d = this.defs.get(bossId);
      if (d == null) {
         return false;
      } else {
         List<Player> candidates = new ArrayList<>();

         for (Player p : s.world.getPlayers()) {
            if (this.plugin.events().eligible(p, s)) {
               candidates.add(p);
            }
         }

         if (candidates.isEmpty()) {
            return false;
         } else {
            Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            Location loc = this.plugin.events().findSpawn(target.getLocation(), 10.0, 18.0);
            if (loc == null) {
               loc = target.getLocation();
            }

            return this.spawnAt(loc, bossId, s.difficulty, target);
         }
      }
   }

   public boolean spawnAt(Location loc, String bossId, double difficulty, Player target) {
      BossManager.BossDef d = this.defs.get(bossId);
      if (d == null || loc == null || loc.getWorld() == null) {
         return false;
      }

      World w = loc.getWorld();
      WorldConfig wc = this.plugin.worldConfigs().get(w.getName());
      WorldConfig.BossConfig bossCfg = wc != null ? wc.boss() : null;

      LivingEntity le = null;
      if (bossCfg != null && "MYTHICMOBS".equals(bossCfg.mode()) && bossCfg.mythicEnabled() && this.plugin.mythic().isAvailable()) {
         String mythicName = bossCfg.mythicInternalName();
         if (!this.plugin.mythic().mobExists(mythicName)) {
            if (this.plugin.messages().announce("bosses", true)) {
               this.plugin.messages().world(w, "boss.mythic-not-found", "name", mythicName, "world", w.getName());
            }
         } else {
            spawningBoss = true;

            try {
               le = this.plugin.mythic().spawn(mythicName, loc, difficulty);
            } finally {
               spawningBoss = false;
            }

            if (le != null) {
               String display = this.plugin.mythic().getDisplayName(mythicName);
               le.customName(Util.mini(display));
               w.strikeLightningEffect(loc);
               if (this.plugin.messages().announce("bosses", true)) {
                  this.plugin.messages().all("boss.spawn", "boss", display);
               }

               for (Player px : w.getPlayers()) {
                  px.playSound(px.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.7F);
               }

               this.plugin.stats().recordBossSpawned();
            }
         }

         if (le == null && bossCfg.mythicFallback()) {
            if (this.plugin.messages().announce("bosses", true)) {
               this.plugin.messages().world(w, "boss.mythic-fallback", "world", w.getName());
            }
         } else if (le == null) {
            return false;
         }
      }

      if (bossCfg != null && "NONE".equals(bossCfg.mode()) && le == null) {
         if (this.plugin.messages().announce("bosses", true)) {
            this.plugin.messages().world(w, "boss.disabled");
         }

         return false;
      }

      if (le == null) {
         spawningBoss = true;

         try {
            le = this.plugin
               .events()
               .spawn(loc, d.type(), d.health(), d.damage(), d.speed(), d.name(), d.equipment(), 1.0, difficulty, null, target);
         } finally {
            spawningBoss = false;
         }

         if (le == null) {
            return false;
         }

         le.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.STRING, d.id());
         le.getPersistentDataContainer().set(Keys.SCALE, PersistentDataType.DOUBLE, d.damage() * this.damageScale(w, difficulty));
         le.setCustomNameVisible(true);
         if (d.skin() != null) {
            this.plugin.skins().apply(le, d.skin());
         }
         if (d.scale() > 0.0) {
            AttributeInstance sc = le.getAttribute(Attribute.SCALE);
            if (sc != null) {
               sc.setBaseValue(d.scale());
            }
         }

         if (d.knockbackRes() > 0.0) {
            AttributeInstance kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
               kb.setBaseValue(Math.min(1.0, d.knockbackRes()));
            }
         }

         if (d.announce() && this.plugin.messages().announce("bosses", true)) {
            w.strikeLightningEffect(loc);
            this.plugin.messages().all("boss.spawn", "boss", d.name());

            for (Player px : w.getPlayers()) {
               px.playSound(px.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.7F);
            }

            this.plugin.stats().recordBossSpawned();
         }
      } else {
         le.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.STRING, d.id());
         le.setCustomNameVisible(true);
      }

      if (le instanceof org.bukkit.entity.EnderDragon dragon) {
         dragon.setPodium(loc);
         dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.CHARGE_PLAYER);
         if (target != null) {
            dragon.setTarget(target);
            dragon.setPodium(target.getLocation());
         }
      } else if (le instanceof org.bukkit.entity.Wither wither) {
         wither.setInvulnerableTicks(20);
         if (target != null) {
            wither.setTarget(target);
            for (org.bukkit.entity.Wither.Head head : org.bukkit.entity.Wither.Head.values()) {
               wither.setTarget(head, target);
            }
         }
      }

      if (le instanceof org.bukkit.entity.Boss vb && vb.getBossBar() != null) {
         vb.getBossBar().setVisible(false);
      }
      le.addScoreboardTag("bloodmoon_boss");
      BossManager.Instance inst = new BossManager.Instance(le.getUniqueId(), d, difficulty, w);
      inst.abilityScale = d.abilityDamage() * this.damageScale(w, difficulty);
      inst.lastLoc = le.getLocation();
      this.alive.put(le.getUniqueId(), inst);
      return true;
   }

   void tick() {
      long now = System.currentTimeMillis();
      Iterator<BossManager.Instance> it = this.alive.values().iterator();

      while (it.hasNext()) {
         BossManager.Instance bi = it.next();
         Entity raw = Bukkit.getEntity(bi.id);
         if (raw == null && bi.lastLoc != null && bi.lastLoc.getWorld() != null && !bi.lastLoc.isChunkLoaded()) {
            this.hideBar(bi);
            continue;
         }

         if (raw instanceof LivingEntity le && !le.isDead() && le.isValid()) {
            bi.lastLoc = le.getLocation();
            double frac = Math.max(0.0, Math.min(1.0, le.getHealth() / Util.maxHealth(le)));
            bi.bar.progress((float)frac);

            double barRadius = bi.def.barRadius() >= 0.0 ? bi.def.barRadius() : this.plugin.getConfig().getDouble("difficulty.bosses.bar-radius", 50.0);
            Location bossLoc = le.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
               if (p.getWorld().equals(le.getWorld()) && (barRadius <= 0.0 || p.getLocation().distanceSquared(bossLoc) <= barRadius * barRadius)) {
                  p.showBossBar(bi.bar);
               } else {
                  p.hideBossBar(bi.bar);
               }
            }

            if (le instanceof org.bukkit.entity.Boss vb && vb.getBossBar() != null) {
               vb.getBossBar().setVisible(false);
               vb.getBossBar().removeAll();
            }

            if (!bi.enraged && bi.def.enrageAt() > 0.0 && frac <= bi.def.enrageAt()) {
               this.enrage(bi, le);
            }

            if (le instanceof org.bukkit.entity.EnderDragon dragon) {
               Player victim = this.findNearestVictim(le, 128.0);
               if (victim != null) {
                  dragon.setPodium(victim.getLocation());
                  dragon.setTarget(victim);
                  if (dragon.getPhase() != org.bukkit.entity.EnderDragon.Phase.CHARGE_PLAYER
                      && dragon.getPhase() != org.bukkit.entity.EnderDragon.Phase.STRAFING) {
                     dragon.setPhase(org.bukkit.entity.EnderDragon.Phase.CHARGE_PLAYER);
                  }
               }
            } else if (le instanceof org.bukkit.entity.Wither wither) {
               Player victim = this.findNearestVictim(le, 64.0);
               if (victim != null) {
                  wither.setTarget(victim);
                  for (org.bukkit.entity.Wither.Head head : org.bukkit.entity.Wither.Head.values()) {
                     wither.setTarget(head, victim);
                  }
                  Location bLoc = wither.getLocation();
                  Location vLoc = victim.getLocation();
                  double distSq = bLoc.distanceSquared(vLoc);
                  if (distSq > 16.0) {
                     Location targetHover = vLoc.clone().add(0.0, 3.5, 0.0);
                     Vector toPlayer = targetHover.toVector().subtract(bLoc.toVector());
                     if (toPlayer.lengthSquared() > 0.01) {
                        Vector flyVel = toPlayer.normalize().multiply(0.45);
                        wither.setVelocity(flyVel);
                     }
                  }
               }
            }

            for (int i = 0; i < bi.def.abilities().size(); i++) {
               if (now >= bi.nextUse[i]) {
                  BossManager.Ability a = bi.def.abilities().get(i);
                  if (this.use(bi, le, a)) {
                     double cd = a.cooldown() * (bi.enraged ? bi.def.enrageCooldown() : 1.0);
                     bi.nextUse[i] = now + (long)(cd * 1000.0);
                  }
               }
            }
            continue;
         }

         this.hideBar(bi);
         it.remove();
      }
   }

   private void enrage(BossManager.Instance bi, LivingEntity boss) {
      bi.enraged = true;
      World w = boss.getWorld();
      int dur = 72000;
      boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1));
      boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 1));
      Location loc = boss.getLocation().add(0.0, 1.0, 0.0);
      w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 0.4F);
      w.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.5F, 0.5F);
      w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 120, 1.4, 1.4, 1.4, 0.05);
      w.spawnParticle(Particle.FLAME, loc, 120, 1.4, 1.4, 1.4, 0.08);
      w.spawnParticle(Particle.DUST, loc, 90, 1.4, 1.4, 1.4, 0.05, new DustOptions(org.bukkit.Color.fromRGB(255, 0, 0), 2.2F));
      long now = System.currentTimeMillis();

      for (int i = 0; i < bi.nextUse.length; i++) {
         bi.nextUse[i] = now;
      }

      for (Player p : w.getPlayers()) {
         p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.5F);
         p.sendMessage(this.plugin.messages().prefixed("boss.enraged", "boss", bi.def.name()));
      }
   }

   private List<Player> nearbyPlayers(LivingEntity boss, double radius) {
      List<Player> out = new ArrayList<>();

      for (Player p : boss.getWorld().getPlayers()) {
         if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
            && !p.isDead()
            && p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) {
            out.add(p);
         }
      }

      return out;
   }

   private Player findNearestVictim(LivingEntity boss, double radius) {
      Player nearest = null;
      double minD2 = radius * radius;
      Location loc = boss.getLocation();
      for (Player p : boss.getWorld().getPlayers()) {
         if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
            && !p.isDead()) {
            double d2 = p.getLocation().distanceSquared(loc);
            if (d2 <= minD2) {
               minD2 = d2;
               nearest = p;
            }
         }
      }
      return nearest;
   }

   private static double arg(BossManager.Ability a, int i, double def) {
      if (i >= a.args().length) {
         return def;
      } else {
         try {
            return Double.parseDouble(a.args()[i].trim());
         } catch (NumberFormatException var5) {
            return def;
         }
      }
   }

   private boolean use(BossManager.Instance bi, LivingEntity boss, BossManager.Ability a) {
      List<Player> near = this.nearbyPlayers(boss, 40.0);
      if (near.isEmpty()) {
         return false;
      } else {
         World w = boss.getWorld();
         ThreadLocalRandom rnd = ThreadLocalRandom.current();
         double em = bi.enraged ? bi.def.enrageDamage() : 1.0;
         String var9 = a.type();
                  switch (var9) {
            case "SUMMON":
               int countSummon = (int)arg(a, 0, 3.0);
               String typeSummon = (a.args().length > 1 ? a.args()[1] : "ZOMBIE").trim();
               if (this.plugin.events().mobById(typeSummon) == null) {
                  try {
                     EntityType.valueOf(typeSummon.toUpperCase(Locale.ROOT));
                  } catch (IllegalArgumentException var21) {
                     return false;
                  }
               }

               int nSummon = Math.max(1, (int)Math.round((double)countSummon * this.plugin.scaled(bi.diff, "horde-size")));

               for (int ix = 0; ix < nSummon; ix++) {
                  Location loc = this.plugin.events().findSpawn(boss.getLocation(), 2.0, 6.0);
                  if (loc != null) {
                     LivingEntity minion = this.plugin.events().spawnMinion(loc, typeSummon, bi.diff, near.get(rnd.nextInt(near.size())));
                     if (minion != null) {
                        minion.getPersistentDataContainer().set(Keys.MINION_OF, PersistentDataType.STRING, bi.def.id());
                     }
                  }
               }

               w.playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5F, 0.8F);
               w.spawnParticle(Particle.SOUL, boss.getLocation().add(0.0, 1.0, 0.0), 40, 1.0, 1.0, 1.0, 0.05);
               return true;
            case "SLAM":
               double radiusSlam = arg(a, 0, 7.0);
               double dmgSlam = arg(a, 1, 6.0);
               List<Player> hitSlam = this.nearbyPlayers(boss, radiusSlam);
               if (hitSlam.isEmpty()) {
                  return false;
               }

               for (Player px : hitSlam) {
                  this.hurt(bi, px, dmgSlam * em, boss);
                  Vector dirSlam = px.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                  if (dirSlam.lengthSquared() < 0.01) {
                     dirSlam = new Vector(rnd.nextDouble() - 0.5, 0.0, rnd.nextDouble() - 0.5);
                  }

                  px.setVelocity(dirSlam.normalize().multiply(1.1).setY(0.55));
               }

               w.playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.7F);
               w.spawnParticle(Particle.CLOUD, boss.getLocation(), 70, radiusSlam / 2.0, 0.3, radiusSlam / 2.0, 0.08);
               return true;
            case "LIGHTNING":
               int countLightning = (int)arg(a, 0, 3.0);
               double dmgLightning = arg(a, 1, 8.0);
               Collections.shuffle(near);

               for (int i = 0; i < Math.min(countLightning, near.size()); i++) {
                  Player p = near.get(i);
                  w.strikeLightningEffect(p.getLocation());
                  this.hurt(bi, p, dmgLightning * em, boss);
               }

               return true;
            case "AURA":
               double radiusAura = arg(a, 0, 12.0);
               String effAura = a.args().length > 1 ? a.args()[1] : "wither";
               int secsAura = (int)arg(a, 2, 6.0);
               int ampAura = (int)arg(a, 3, 0.0);
               PotionEffectType typeAura = Reg.effect(effAura);
               if (typeAura == null) {
                  return false;
               } else {
                  List<Player> hitAura = this.nearbyPlayers(boss, radiusAura);
                  if (hitAura.isEmpty()) {
                     return false;
                  }

                  for (Player px : hitAura) {
                     px.addPotionEffect(new PotionEffect(typeAura, secsAura * 20, ampAura));
                  }

                  w.spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 60, radiusAura / 3.0, 1.0, radiusAura / 3.0, 0.02);
                  return true;
               }
            case "SONIC_BOOM":
               double radiusSonic = arg(a, 0, 10.0);
               double dmgSonic = arg(a, 1, 10.0);
               List<Player> hitSonic = this.nearbyPlayers(boss, radiusSonic);
               if (hitSonic.isEmpty()) {
                  return false;
               }

               for (Player px : hitSonic) {
                  this.hurt(bi, px, dmgSonic * em, boss);
                  Vector dirSonic = px.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                  if (dirSonic.lengthSquared() < 0.01) {
                     dirSonic = new Vector(rnd.nextDouble() - 0.5, 0.0, rnd.nextDouble() - 0.5);
                  }

                  px.setVelocity(dirSonic.normalize().multiply(1.8).setY(0.7));
               }

               w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0F, 1.0F);
               w.spawnParticle(Particle.SONIC_BOOM, boss.getLocation().add(0.0, 1.0, 0.0), 1);
               w.spawnParticle(Particle.SCULK_SOUL, boss.getLocation().add(0.0, 1.0, 0.0), 40, radiusSonic / 3.0, 1.0, radiusSonic / 3.0, 0.02);
               return true;
            case "PULL":
               double radiusPull = arg(a, 0, 14.0);
               double powerPull = arg(a, 1, 1.2);
               List<Player> hitPull = this.nearbyPlayers(boss, radiusPull);
               if (hitPull.isEmpty()) {
                  return false;
               }

               for (Player p : hitPull) {
                  Vector dirPull = boss.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                  if (!(dirPull.lengthSquared() < 0.01)) {
                     p.setVelocity(dirPull.normalize().multiply(powerPull).setY(0.35));
                  }
               }

               w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5F, 0.7F);
               w.spawnParticle(Particle.SQUID_INK, boss.getLocation().add(0.0, 1.0, 0.0), 50, radiusPull / 3.0, 1.0, radiusPull / 3.0, 0.05);
               return true;
            case "SKULL":
               int countSkull = (int)arg(a, 0, 3.0);
               int firedSkull = 0;

               for (int i = 0; i < countSkull; i++) {
                  Player p = near.get(i % near.size());
                  Location targetPos = p.getLocation().add(0.0, 1.0, 0.0);
                  if (i > 0) {
                     targetPos.add((rnd.nextDouble() - 0.5) * 1.5, (rnd.nextDouble() - 0.5) * 0.8, (rnd.nextDouble() - 0.5) * 1.5);
                  }
                  Vector dir = targetPos.toVector().subtract(boss.getLocation().add(0.0, 1.5, 0.0).toVector());
                  if (!(dir.lengthSquared() < 0.01)) {
                     WitherSkull ws = (WitherSkull)w.spawn(boss.getLocation().add(0.0, 1.5, 0.0), WitherSkull.class);
                     ws.setShooter(boss);
                     ws.setDirection(dir.normalize().multiply(1.2));
                     ws.setCharged(i == 0 || rnd.nextBoolean());
                     ws.setYield(0.0F);
                     firedSkull++;
                  }
               }

               w.playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.5F, 0.8F);
               return firedSkull > 0;
            case "EXPLODE":
               double radiusExp = arg(a, 0, 8.0);
               double dmgExp = arg(a, 1, 10.0);
               Location expLoc = boss.getLocation().add(0.0, 1.0, 0.0);
               w.spawnParticle(Particle.EXPLOSION_EMITTER, expLoc, 2);
               w.spawnParticle(Particle.SOUL_FIRE_FLAME, expLoc, 80, 1.5, 1.5, 1.5, 0.1);
               w.playSound(expLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.7F);
               w.playSound(expLoc, Sound.ENTITY_WITHER_DEATH, 1.5F, 0.8F);
               for (Player px : this.nearbyPlayers(boss, radiusExp)) {
                  this.hurt(bi, px, dmgExp * em, boss);
                  Vector away = px.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0.4);
                  if (away.lengthSquared() > 0.01) {
                     px.setVelocity(away.normalize().multiply(1.5));
                  }
               }
               return true;
            case "CHARGE":
               double powerCharge = arg(a, 0, 1.4);
               double dmgCharge = arg(a, 1, 9.0);
               Player tp = near.get(rnd.nextInt(near.size()));
               Vector dirCharge = tp.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
               if (dirCharge.lengthSquared() < 0.01) {
                  return false;
               }

               dirCharge.normalize();
               boss.setVelocity(dirCharge.clone().multiply(powerCharge).setY(0.2));
               w.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.6F, 0.6F);
               int[] ticksCharge = new int[]{20};
               Bukkit.getScheduler()
                  .runTaskTimer(
                     this.plugin,
                     task -> {
                        if (ticksCharge[0]-- > 0 && !boss.isDead() && boss.isValid()) {
                           for (Player px : boss.getWorld().getPlayers()) {
                              if (!px.isDead()
                                 && (px.getGameMode() == GameMode.SURVIVAL || px.getGameMode() == GameMode.ADVENTURE)
                                 && px.getLocation().distanceSquared(boss.getLocation()) <= 9.0) {
                                 this.hurt(bi, px, dmgCharge * em, boss);
                                 Vector away = px.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                                 if (away.lengthSquared() > 0.01) {
                                    px.setVelocity(away.normalize().multiply(1.2).setY(0.5));
                                 }
                              }
                           }

                           boss.getWorld().spawnParticle(Particle.CLOUD, boss.getLocation(), 10, 0.4, 0.2, 0.4, 0.05);
                        } else {
                           task.cancel();
                        }
                     },
                     0L,
                     2L
                  );
               return true;
            case "ROAR":
               double radiusRoar = arg(a, 0, 16.0);
               String effRoar = a.args().length > 1 ? a.args()[1] : "darkness";
               int secsRoar = (int)arg(a, 2, 6.0);
               int ampRoar = (int)arg(a, 3, 0.0);
               PotionEffectType typeRoar = Reg.effect(effRoar);
               if (typeRoar == null) {
                  return false;
               } else {
                  List<Player> hitRoar = this.nearbyPlayers(boss, radiusRoar);
                  if (hitRoar.isEmpty()) {
                     return false;
                  }

                  for (Player px : hitRoar) {
                     px.addPotionEffect(new PotionEffect(typeRoar, secsRoar * 20, ampRoar));
                  }

                  w.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2.0F, 0.5F);
                  w.spawnParticle(Particle.SONIC_BOOM, boss.getLocation().add(0.0, 1.0, 0.0), 1);
                  w.spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 50, radiusRoar / 4.0, 1.0, radiusRoar / 4.0, 0.03);
                  return true;
               }
            case "GUARD":
               int secsGuard = (int)arg(a, 0, 5.0);
               boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secsGuard * 20, 2));
               boss.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, secsGuard * 20, 3));
               w.playSound(boss.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5F, 1.0F);
               w.spawnParticle(Particle.ENCHANT, boss.getLocation().add(0.0, 1.0, 0.0), 40, 0.6, 0.8, 0.6, 0.4);
               return true;
            case "SELF_HEAL":
               double amountHeal = arg(a, 0, 60.0);
               double maxHeal = Util.maxHealth(boss);
               if (boss.getHealth() >= maxHeal - 0.5) {
                  return false;
               }

               boss.setHealth(Math.min(maxHeal, boss.getHealth() + amountHeal));
               w.playSound(boss.getLocation(), Sound.ENTITY_WITHER_HURT, 1.4F, 0.5F);
               w.spawnParticle(Particle.HEART, boss.getLocation().add(0.0, 1.5, 0.0), 24, 0.7, 0.8, 0.7, 0.05);
               w.spawnParticle(Particle.SOUL, boss.getLocation().add(0.0, 1.0, 0.0), 30, 0.6, 0.8, 0.6, 0.03);
               return true;
            default:
               return false;
         }
      }
   }

   private void hurt(BossManager.Instance bi, Player p, double amount, LivingEntity boss) {
      abilityHit = true;
      try {
         p.damage(amount * bi.abilityScale, boss);
      } finally {
         abilityHit = false;
      }
   }

   double damageScale(World w, double difficulty) {
      return this.plugin.scaled(difficulty, "boss-damage") * this.plugin.events().moonDamageMultiplier(w);
   }

   BossManager.BossDef defOf(Entity e) {
      if (e == null) {
         return null;
      }
      BossManager.Instance bi = this.alive.get(e.getUniqueId());
      if (bi != null) {
         return bi.def;
      }
      String id = e.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING);
      if (id == null) {
         id = e.getPersistentDataContainer().get(Keys.MINION_OF, PersistentDataType.STRING);
      }
      return id == null ? null : this.defs.get(id);
   }

   double hitCap(BossManager.BossDef d, Player victim) {
      double abs = d.maxHit() >= 0.0 ? d.maxHit() : this.plugin.getConfig().getDouble("difficulty.bosses.max-hit", 0.0);
      return finalCap(abs, this.plugin.getConfig().getDouble("difficulty.bosses.max-hit-fraction", 0.75), victim);
   }

   double rawCap(BossManager.BossDef d) {
      return d.maxDamage() >= 0.0 ? d.maxDamage() : this.plugin.getConfig().getDouble("difficulty.bosses.max-damage", 0.0);
   }

   static double finalCap(double abs, double frac, Player victim) {
      double cap = Double.MAX_VALUE;
      if (abs > 0.0) {
         cap = abs;
      }
      if (frac > 0.0) {
         cap = Math.min(cap, frac * Util.maxHealth(victim));
      }
      return cap;
   }

   public void addDamage(Entity boss, Player attacker, double amount) {
      BossManager.Instance bi = this.alive.get(boss.getUniqueId());
      if (bi != null) {
         bi.damage.merge(attacker.getUniqueId(), amount, Double::sum);
      }
   }

   public void handleDeath(LivingEntity ent, EntityDeathEvent e) {
      BossManager.Instance bi = this.alive.remove(ent.getUniqueId());
      if (bi != null) {
         this.hideBar(bi);
         this.defeatedCount++;
         this.plugin.stats().recordBossDefeated();
         EventManager.Session ses = this.plugin.events().session(bi.world);
         if (ses != null) {
            ses.lastBossName = bi.def.name();
            ses.lastBossType = bi.def.type().name();
         }

         e.getDrops().clear();
         e.setDroppedExp(bi.def.exp());
         List<Entry<UUID, Double>> top = new ArrayList<>(bi.damage.entrySet());
         top.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
         Player topPlayer = top.isEmpty() ? null : Bukkit.getPlayer(top.get(0).getKey());
         Player killer = ent.getKiller() != null ? ent.getKiller() : topPlayer;
         Location loc = ent.getLocation();
         if (bi.def.lootTable() != null) {
            boolean forceAll = this.plugin.getConfig().getBoolean("loot.direct-to-inventory", false);

            for (ItemStack it : this.plugin.loot().roll(bi.def.lootTable(), killer, bi.diff, false)) {
               boolean direct = killer != null && (forceAll || this.plugin.loot().directToInventory(this.plugin.loot().rarityOf(it)));
               if (direct) {
                  this.plugin.loot().giveOrDrop(killer, it);
               } else {
                  this.plugin.loot().dropAt(loc, it);
               }
            }
         }

         if (this.plugin.messages().announce("bosses", true)) {
            this.plugin.messages().world(ent.getWorld(), "boss.killed", "boss", bi.def.name(), "player", killer != null ? killer.getName() : "?");
            if (!top.isEmpty()) {
               this.plugin.messages().world(ent.getWorld(), "boss.top-header");

               for (int i = 0; i < Math.min(3, top.size()); i++) {
                  String nm = Bukkit.getOfflinePlayer(top.get(i).getKey()).getName();
                  this.plugin
                     .messages()
                     .world(
                        ent.getWorld(),
                        "boss.top-line",
                        "pos",
                        String.valueOf(i + 1),
                        "player",
                        nm == null ? "?" : nm,
                        "damage",
                        String.valueOf(Math.round(top.get(i).getValue()))
                     );
               }
            }
         }

         if (topPlayer != null && bi.def.topTable() != null) {
            for (ItemStack itx : this.plugin.loot().roll(bi.def.topTable(), topPlayer, bi.diff, false)) {
               this.plugin.loot().giveOrDrop(topPlayer, itx);
            }

            topPlayer.sendMessage(this.plugin.messages().prefixed("boss.top-reward"));
         }
      }
   }

   private void hideBar(BossManager.Instance bi) {
      for (Player p : Bukkit.getOnlinePlayers()) {
         p.hideBossBar(bi.bar);
      }
   }

   public int removeAll(World w) {
      int n = 0;
      Iterator<BossManager.Instance> it = this.alive.values().iterator();

      while (it.hasNext()) {
         BossManager.Instance bi = it.next();
         if (bi.world.equals(w)) {
            Entity ent = Bukkit.getEntity(bi.id);
            if (ent != null && ent.isValid()) {
               ent.remove();
               n++;
            }

            this.hideBar(bi);
            it.remove();
         }
      }

      return n;
   }

   public void dropOrphan(Entity ent) {
      if (ent instanceof LivingEntity
         && ent.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)
         && !this.alive.containsKey(ent.getUniqueId())) {
         ent.remove();
      }
   }

   public long defeatedCount() {
      return this.defeatedCount;
   }

   public BossPlaceholderSnapshot aliveSnapshot() {
      for (BossManager.Instance bi : this.alive.values()) {
         if (Bukkit.getEntity(bi.id) instanceof LivingEntity le && !le.isDead() && le.isValid()) {
            double maxHp = Util.maxHealth(le);
            String name = le.customName() != null ? PlainTextComponentSerializer.plainText().serialize(le.customName()) : bi.def.name();
            return BossPlaceholderSnapshot.fromEntity(le, maxHp, name, "VANILLA", BossPlaceholderState.ALIVE);
         }
      }

      return null;
   }

   static record Ability(String type, double cooldown, String[] args) {
   }

   static record BossDef(
      String id,
      String name,
      EntityType type,
      double health,
      double damage,
      double speed,
      double scale,
      double knockbackRes,
      Color color,
      Overlay overlay,
      Map<EquipmentSlot, Material> equipment,
      List<BossManager.Ability> abilities,
      String lootTable,
      String topTable,
      boolean announce,
      int exp,
      double enrageAt,
      double enrageCooldown,
      double enrageDamage,
      double abilityDamage,
      double maxHit,
      double maxDamage,
      double barRadius,
      String skin
   ) {
   }

   static final class Instance {
      final UUID id;
      final BossManager.BossDef def;
      final BossBar bar;
      final double diff;
      final World world;
      final Map<UUID, Double> damage = new HashMap<>();
      final long[] nextUse;
      boolean enraged = false;
      double abilityScale = 1.0;
      Location lastLoc;

      Instance(UUID id, BossManager.BossDef def, double diff, World world) {
         this.id = id;
         this.def = def;
         this.diff = diff;
         this.world = world;
         this.bar = BossBar.bossBar(Util.mini(def.name()), 1.0F, def.color(), def.overlay());
         this.nextUse = new long[def.abilities().size()];
         long now = System.currentTimeMillis();

         for (int i = 0; i < this.nextUse.length; i++) {
            this.nextUse[i] = now + (long)(def.abilities().get(i).cooldown() * 1000.0);
         }
      }
   }

   public static record Pending(String bossId, long atMs) {
   }

   static record Spawn(String boss, int delay, double chance, double minDifficulty, int count, boolean scaleCount) {
   }
}
