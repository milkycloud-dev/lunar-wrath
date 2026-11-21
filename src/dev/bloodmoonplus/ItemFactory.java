package dev.bloodmoonplus;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemFactory {
   private final BloodMoonPlus plugin;
   private final Map<String, ItemFactory.ItemDef> defs = new LinkedHashMap<>();
   private boolean showRarityLine = true;
   private int maxEnchantLevel = 7;

   ItemFactory(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   void reload() {
      this.defs.clear();
      YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(this.plugin.getDataFolder(), "items.yml"));
      this.showRarityLine = y.getBoolean("settings.show-rarity-line", true);
      this.maxEnchantLevel = Math.max(1, y.getInt("settings.max-enchant-level", 7));
      ConfigurationSection sec = y.getConfigurationSection("items");
      if (sec != null) {
         for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s != null) {
               Material m = Material.matchMaterial(s.getString("material", "STONE"));
               if (m != null && !m.isAir() && m.isItem()) {
                  Map<String, Integer> ench = new LinkedHashMap<>();
                  ConfigurationSection es = s.getConfigurationSection("enchants");
                  if (es != null) {
                     for (String k : es.getKeys(false)) {
                        ench.put(k, es.getInt(k));
                     }
                  }

                  String rarity = Util.key(s.getString("rarity", "COMUN"));
                  if (!this.plugin.loot().hasRarity(rarity)) {
                     this.plugin
                        .getLogger()
                        .warning("items.yml: rarity '" + rarity + "' of '" + id + "' is missing in loot.yml (rarities); using COMUN. Add it to loot.yml.");
                  }

                  this.defs
                     .put(
                        id,
                        new ItemFactory.ItemDef(
                           id,
                           m,
                           s.getString("name"),
                           s.getStringList("lore"),
                           rarity,
                           ench,
                           s.getBoolean("unbreakable"),
                           s.getBoolean("glow"),
                           s.getString("item-model"),
                           s.getInt("limit", 0),
                           s.getStringList("abilities"),
                           s.getStringList("passives")
                        )
                     );
               } else {
                  this.plugin.getLogger().warning("items.yml: invalid material in '" + id + "'");
               }
            }
         }

         this.plugin.getLogger().info("Items loaded: " + this.defs.size());
      }
   }

   public ItemFactory.ItemDef get(String id) {
      return id == null ? null : this.defs.get(id);
   }

   public Set<String> ids() {
      return this.defs.keySet();
   }

   public boolean available(ItemFactory.ItemDef d) {
      return d.limit() <= 0 || this.plugin.data().created(d.id()) < d.limit();
   }

   public ItemStack create(ItemFactory.ItemDef d, int amount, String owner) {
      int serial = 0;
      if (d.limit() > 0) {
         if (this.plugin.data().created(d.id()) >= d.limit()) {
            return null;
         }

         serial = this.plugin.data().nextSerial(d.id());
      }

      return this.build(d, amount, owner, serial);
   }

   public ItemStack build(ItemFactory.ItemDef d, int amount, String owner, int serial) {
      ItemStack stack = new ItemStack(d.material(), Math.max(1, amount));
      ItemMeta meta = stack.getItemMeta();
      if (meta == null) {
         return stack;
      } else {
         String rarityDisp = this.plugin.loot().rarityDisplay(d.rarity());
         Messages msg = this.plugin.messages();
         if (d.name() != null) {
            meta.displayName(Util.item(d.name()));
         }

         List<Component> lore = new ArrayList<>();

         for (String line : d.lore()) {
            lore.add(Util.item(line.replace("{owner}", owner == null ? "?" : owner).replace("{rarity}", rarityDisp)));
         }

         if (this.showRarityLine && d.name() != null) {
            lore.add(Component.empty());
            lore.add(Util.item(msg.fill(msg.raw("item.rarity-line"), "rarity", rarityDisp)));
         }

         if (d.limit() > 0 && serial > 0) {
            lore.add(Util.item(msg.fill(msg.raw("item.serial-line"), "serial", String.valueOf(serial), "limit", String.valueOf(d.limit()))));
         }

         if (!lore.isEmpty()) {
            meta.lore(lore);
         }

         for (Entry<String, Integer> e : d.enchants().entrySet()) {
            Enchantment en = Reg.enchantment(e.getKey());
            if (en == null) {
               this.plugin.getLogger().warning("items.yml: unknown enchantment '" + e.getKey() + "' in " + d.id());
            } else {
               meta.addEnchant(en, Math.min(this.maxEnchantLevel, Math.max(1, e.getValue())), true);
            }
         }

         if (d.unbreakable()) {
            meta.setUnbreakable(true);
         }

         if (d.glow()) {
            meta.setEnchantmentGlintOverride(true);
         }

         if (d.itemModel() != null) {
            NamespacedKey k = NamespacedKey.fromString(d.itemModel());
            if (k != null) {
               meta.setItemModel(k);
            }
         }

         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, d.id());
         pdc.set(Keys.ITEM_RARITY, PersistentDataType.STRING, d.rarity());
         if (serial > 0) {
            pdc.set(Keys.ITEM_SERIAL, PersistentDataType.INTEGER, serial);
         }

         if (!d.abilities().isEmpty()) {
            pdc.set(Keys.ITEM_ABILITIES, PersistentDataType.STRING, String.join(";", d.abilities()));
         }

         if (!d.passives().isEmpty()) {
            pdc.set(Keys.ITEM_PASSIVES, PersistentDataType.STRING, String.join(";", d.passives()));
         }

         stack.setItemMeta(meta);
         return stack;
      }
   }

   public static record ItemDef(
      String id,
      Material material,
      String name,
      List<String> lore,
      String rarity,
      Map<String, Integer> enchants,
      boolean unbreakable,
      boolean glow,
      String itemModel,
      int limit,
      List<String> abilities,
      List<String> passives
   ) {
   }
}
