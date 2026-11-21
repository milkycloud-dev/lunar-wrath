package dev.bloodmoonplus;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class LootManager {
   private final BloodMoonPlus plugin;
   private final Map<String, LootManager.Rarity> rarities = new LinkedHashMap<>();
   private final Map<String, LootManager.Table> tables = new LinkedHashMap<>();
   private final Set<String> unknownReported = new HashSet<>();

   LootManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   void reload() {
      this.rarities.clear();
      this.tables.clear();
      this.unknownReported.clear();
      YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "loot.yml"));
      ConfigurationSection rs = y.getConfigurationSection("rarities");
      if (rs != null) {
         for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s != null) {
               String key = Util.key(id);
               this.rarities
                  .put(
                     key,
                     new LootManager.Rarity(
                        key,
                        s.getString("display", id),
                        s.getDouble("weight", 1.0),
                        s.getBoolean("luck-affected"),
                        s.getBoolean("broadcast"),
                        s.getDouble("pity-per-roll", 0.0),
                        s.getInt("pity-max-stacks", 200)
                     )
                  );
            }
         }
      }

      ConfigurationSection ts = y.getConfigurationSection("tables");
      if (ts != null) {
         for (String idx : ts.getKeys(false)) {
            ConfigurationSection s = ts.getConfigurationSection(idx);
            if (s != null) {
               LootManager.Table t = new LootManager.Table();
               t.id = idx;
               t.dropChance = s.getDouble("drop-chance", 1.0);
               t.rolls = Util.range(s.get("rolls"), 1, 1);
               t.inherits = s.getString("inherits");
               ConfigurationSection rw = s.getConfigurationSection("rarity-weights");
               if (rw != null) {
                  for (String k : rw.getKeys(false)) {
                     t.rarityWeights.put(Util.key(k), rw.getDouble(k));
                  }
               }

               t.entries = this.parseEntries(s.getMapList("entries"));
               t.guaranteed = this.parseEntries(s.getMapList("guaranteed"));
               this.tables.put(idx, t);
            }
         }

         for (LootManager.Table t : this.tables.values()) {
            t.effective = this.resolved(t, new HashSet<>());
         }
      }

      this.plugin.getLogger().info("Loot: " + this.rarities.size() + " rarities, " + this.tables.size() + " tables.");
   }

   private List<LootManager.Entry> parseEntries(List<Map<?, ?>> list) {
      List<LootManager.Entry> out = new ArrayList<>();

      for (Map<?, ?> m : list) {
         String item = Util.str(m, "item", null);
         if (item != null) {
            double dropChance = Util.num(m, "drop-chance", 1.0);
            out.add(new LootManager.Entry(item, Util.num(m, "weight", 1.0), Util.range(m.get("amount"), 1, 1), Math.max(0.0, Math.min(1.0, dropChance))));
         }
      }

      return out;
   }

   private List<LootManager.Entry> resolved(LootManager.Table t, Set<String> seen) {
      List<LootManager.Entry> out = new ArrayList<>();
      if (t.inherits != null && seen.add(t.id)) {
         LootManager.Table p = this.tables.get(t.inherits);
         if (p != null) {
            out.addAll(this.resolved(p, seen));
         }
      }

      out.addAll(t.entries);
      return out;
   }

   public String rarityDisplay(String id) {
      LootManager.Rarity r = this.rarities.get(id);
      return r == null ? id : r.display();
   }

   public boolean hasRarity(String id) {
      return id != null && this.rarities.containsKey(id);
   }

   public Set<String> tableIds() {
      return this.tables.keySet();
   }

   public boolean hasTable(String id) {
      return this.tables.containsKey(id);
   }

   public Collection<LootManager.Table> allTables() {
      return this.tables.values();
   }

   public List<ItemStack> roll(String tableId, Player player, double diff, boolean applyDropChance) {
      List<ItemStack> out = new ArrayList<>();
      LootManager.Table t = this.tables.get(tableId);
      if (t == null) {
         if (this.unknownReported.add(tableId)) {
            this.plugin.getLogger().warning("Unknown loot table '" + tableId + "' (check loot.yml); this drop gives nothing.");
         }

         return out;
      } else {
         String owner = player != null ? player.getName() : "?";

         for (LootManager.Entry g : t.guaranteed) {
            ItemFactory.ItemDef d = this.plugin.items().get(g.item());
            if (d != null) {
               ItemStack s = this.plugin.items().create(d, Util.between(g.amount()), owner);
               if (s != null) {
                  out.add(s);
               }
            }
         }

         if (applyDropChance) {
            double c = Math.min(1.0, t.dropChance * this.plugin.scaled(diff, "loot-chance"));
            if (ThreadLocalRandom.current().nextDouble() >= c) {
               return out;
            }
         }

         int rolls = Util.between(t.rolls);

         for (int i = 0; i < rolls; i++) {
            ItemStack s = this.rollOnce(t, player, diff, owner);
            if (s != null) {
               out.add(s);
            }
         }

         return out;
      }
   }

   private ItemStack rollOnce(LootManager.Table t, Player player, double diff, String owner) {
      Map<String, List<LootManager.Entry>> byRarity = new LinkedHashMap<>();

      for (LootManager.Entry e : t.effective) {
         ItemFactory.ItemDef d = this.plugin.items().get(e.item());
         if (d != null && !(e.weight() <= 0.0) && this.plugin.items().available(d)) {
            byRarity.computeIfAbsent(d.rarity(), k -> new ArrayList<>()).add(e);
         }
      }

      if (byRarity.isEmpty()) {
         return null;
      } else {
         double luckMult = this.plugin.scaled(diff, "rarity-luck");
         UUID uid = player != null ? player.getUniqueId() : null;
         Map<String, Double> weights = new LinkedHashMap<>();
         double total = 0.0;

         for (String rid : byRarity.keySet()) {
            LootManager.Rarity ra = this.rarities.get(rid);
            if (ra != null) {
               double w = t.rarityWeights.getOrDefault(rid, ra.weight());
               if (!(w <= 0.0)) {
                  if (ra.luck()) {
                     w *= luckMult;
                  }

                  if (uid != null && ra.pityPerRoll() > 0.0) {
                     w *= 1.0 + (double)this.plugin.data().pity(uid, rid) * ra.pityPerRoll();
                  }

                  weights.put(rid, w);
                  total += w;
               }
            }
         }

         if (total <= 0.0) {
            return null;
         } else {
            double r = ThreadLocalRandom.current().nextDouble() * total;
            String chosen = null;
            String last = null;

            for (Map.Entry<String, Double> en : weights.entrySet()) {
               last = en.getKey();
               r -= en.getValue();
               if (r <= 0.0) {
                  chosen = en.getKey();
                  break;
               }
            }

            if (chosen == null) {
               chosen = last;
            }

            if (uid != null) {
               for (String ridx : byRarity.keySet()) {
                  LootManager.Rarity ra = this.rarities.get(ridx);
                  if (ra != null && !(ra.pityPerRoll() <= 0.0)) {
                     if (ridx.equals(chosen)) {
                        this.plugin.data().setPity(uid, ridx, 0);
                     } else {
                        this.plugin.data().setPity(uid, ridx, Math.min(ra.pityMax(), this.plugin.data().pity(uid, ridx) + 1));
                     }
                  }
               }
            }

            LootManager.Entry ex = Util.pick(byRarity.get(chosen), LootManager.Entry::weight);
            if (ex == null) {
               return null;
            } else if (ex.dropChance() < 1.0 && ThreadLocalRandom.current().nextDouble() >= ex.dropChance()) {
               return null;
            } else {
               ItemFactory.ItemDef d = this.plugin.items().get(ex.item());
               ItemStack stack = this.plugin.items().create(d, Util.between(ex.amount()), owner);
               if (stack == null) {
                  return null;
               } else {
                  LootManager.Rarity ra = this.rarities.get(chosen);
                  this.broadcastLoot(player, d, ra, chosen);

                  return stack;
               }
            }
         }
      }
   }

   private void broadcastLoot(Player player, ItemFactory.ItemDef d, LootManager.Rarity ra, String rarityId) {
      if (player == null || ra == null || !ra.broadcast() || !this.plugin.messages().announce("loot", true)) {
         return;
      }
      String itemName = d.name() != null ? d.name() : "<lang:" + d.material().translationKey() + ">";
      String rid = rarityId != null ? rarityId.toUpperCase(Locale.ROOT) : "";
      Location loc = player.getLocation();
      World w = player.getWorld();

      if (rid.equals("MITICO") || rid.equals("MYTHIC")) {
         if (this.plugin.locales().has("loot.broadcast-mythic")) {
            this.plugin.messages().all("loot.broadcast-mythic", "player", player.getName(), "item", itemName, "rarity", ra.display());
         } else {
            this.plugin.messages().allDirect("<gradient:#ff0055:#aa00ff><bold>⚡ MYTHIC ARTIFACT ⚡</bold></gradient> <white><bold>" + player.getName() + "</bold></white> <light_purple>awakened an ancient relic:</light_purple> " + itemName + "<light_purple>!</light_purple>");
         }

         w.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5F, 0.8F);
         w.playSound(loc, Sound.BLOCK_BELL_RESONATE, 1.2F, 0.6F);

         player.showTitle(Title.title(
            Util.mini(this.plugin.locales().get("text.loot.m001")),
            Util.mini(itemName),
            Times.times(Duration.ofMillis(300L), Duration.ofMillis(2500L), Duration.ofMillis(500L))
         ));
         player.sendActionBar(Util.mini(this.plugin.locales().get("text.loot.m002")));

         w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 60, 0.8, 1.0, 0.8, 0.1);
         w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.05);

      } else if (rid.equals("LEGENDARIO") || rid.equals("LEGENDARY")) {
         if (this.plugin.locales().has("loot.broadcast-legendary")) {
            this.plugin.messages().all("loot.broadcast-legendary", "player", player.getName(), "item", itemName, "rarity", ra.display());
         } else {
            this.plugin.messages().allDirect("<gold>★ <bold>LEGENDARY DROP:</bold></gold> <white><bold>" + player.getName() + "</bold></white> <yellow>found the relic</yellow> " + itemName + "<yellow>!</yellow>");
         }

         player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2F, 1.0F);
         player.sendActionBar(Util.mini(this.plugin.locales().get("text.loot.m003", "item-name", String.valueOf(itemName))));
         w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 35, 0.5, 0.8, 0.5, 0.3);

      } else if (rid.equals("EPICO") || rid.equals("EPIC")) {
         if (this.plugin.locales().has("loot.broadcast-epic")) {
            this.plugin.messages().all("loot.broadcast-epic", "player", player.getName(), "item", itemName, "rarity", ra.display());
         } else {
            this.plugin.messages().allDirect("<light_purple>✦</light_purple> <white>" + player.getName() + "</white> <gray>got an epic item:</gray> " + itemName);
         }
         player.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0F, 0.8F);

      } else {
         this.plugin.messages().all("loot.broadcast", "player", player.getName(), "item", itemName, "rarity", ra.display());
         player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
      }
   }

   public void giveOrDrop(Player p, ItemStack it) {
      Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack[]{it});

      for (ItemStack l : left.values()) {
         this.dropAt(p.getLocation(), l);
      }
   }

   public String rarityOf(ItemStack it) {
      return it != null && it.getItemMeta() != null
         ? (String)it.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_RARITY, PersistentDataType.STRING)
         : null;
   }

   public boolean directToInventory(String rarity) {
      if (rarity != null && !rarity.isEmpty()) {
         String k = Util.key(rarity);
         return k.equals(Util.key("RARO")) || k.equals(Util.key("EPICO")) || k.equals(Util.key("LEGENDARIO")) || k.equals(Util.key("MITICO"));
      } else {
         return false;
      }
   }

   public void dropAt(Location loc, ItemStack it) {
      Item drop = loc.getWorld().dropItemNaturally(loc, it);
      if (it.hasItemMeta()) {
         String rarity = (String)it.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_RARITY, PersistentDataType.STRING);
         if (rarity != null && this.plugin.getConfig().getStringList("loot.glow-rarities").stream().anyMatch(s -> Util.key(s).equals(rarity))) {
            drop.setGlowing(true);
            drop.setInvulnerable(true);
         }
      }
   }

   public List<String> describe(String id) {
      LootManager.Table t = this.tables.get(id);
      if (t == null) {
         return null;
      } else {
         List<String> lines = new ArrayList<>();
         lines.add(
            "<gold>" + this.plugin.locales().get("text.loot.table") + " <white>"
               + id
               + " <gray>· " + this.plugin.locales().get("text.loot.base-chance") + ": <white>"
               + String.format(Locale.ROOT, "%.1f%%", t.dropChance * 100.0)
               + " <gray>· " + this.plugin.locales().get("text.loot.rolls") + ": <white>"
               + t.rolls[0]
               + "-"
               + t.rolls[1]
         );
         Map<String, List<LootManager.Entry>> byRarity = new LinkedHashMap<>();

         for (LootManager.Entry e : t.effective) {
            ItemFactory.ItemDef d = this.plugin.items().get(e.item());
            if (d != null && !(e.weight() <= 0.0)) {
               byRarity.computeIfAbsent(d.rarity(), k -> new ArrayList<>()).add(e);
            }
         }

         double total = 0.0;
         Map<String, Double> w = new LinkedHashMap<>();

         for (String rid : byRarity.keySet()) {
            LootManager.Rarity ra = this.rarities.get(rid);
            if (ra != null) {
               double v = t.rarityWeights.getOrDefault(rid, ra.weight());
               if (!(v <= 0.0)) {
                  w.put(rid, v);
                  total += v;
               }
            }
         }

         for (Map.Entry<String, Double> en : w.entrySet()) {
            double pct = en.getValue() / total * 100.0;
            lines.add(" " + this.rarityDisplay(en.getKey()) + " <white>" + String.format(Locale.ROOT, "%.2f%%", pct));
            List<LootManager.Entry> es = byRarity.get(en.getKey());
            double sum = 0.0;

            for (LootManager.Entry ex : es) {
               sum += ex.weight();
            }

            for (LootManager.Entry ex : es) {
               ItemFactory.ItemDef d = this.plugin.items().get(ex.item());
               String nm = d.name() != null ? d.name() : "<lang:" + d.material().translationKey() + ">";
               String dropInfo = ex.dropChance() < 1.0 ? " <gray>· drop: <white>" + String.format(Locale.ROOT, "%.0f%%", ex.dropChance() * 100.0) : "";
               lines.add(
                  "   <dark_gray>- <reset>"
                     + nm
                     + " <gray>"
                     + String.format(Locale.ROOT, "%.2f%%", pct * ex.weight() / sum)
                     + dropInfo
                     + (d.limit() > 0 ? " <red>" + this.plugin.locales().get("text.loot.issued", "created", String.valueOf(this.plugin.data().created(d.id())), "limit", String.valueOf(d.limit())) : "")
               );
            }
         }

         return lines;
      }
   }

   static record Entry(String item, double weight, int[] amount, double dropChance) {
   }

   public static record Rarity(String id, String display, double weight, boolean luck, boolean broadcast, double pityPerRoll, int pityMax) {
   }

   static final class Table {
      String id;
      double dropChance = 1.0;
      int[] rolls = new int[]{1, 1};
      final Map<String, Double> rarityWeights = new HashMap<>();
      List<LootManager.Entry> entries = new ArrayList<>();
      List<LootManager.Entry> guaranteed = new ArrayList<>();
      List<LootManager.Entry> effective = new ArrayList<>();
      String inherits;
   }
}
