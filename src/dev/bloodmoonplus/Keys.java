package dev.bloodmoonplus;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

final class Keys {
   static NamespacedKey MOB;
   static NamespacedKey BOSS;
   static NamespacedKey SCALE;
   static NamespacedKey DIFF;
   static NamespacedKey TABLE;
   static NamespacedKey ITEM_ID;
   static NamespacedKey ITEM_ABILITIES;
   static NamespacedKey ITEM_PASSIVES;
   static NamespacedKey ITEM_RARITY;
   static NamespacedKey ITEM_SERIAL;
   static NamespacedKey BLEED;
   static NamespacedKey BLEED_DMG;
   static NamespacedKey HEAL_AURA;
   static NamespacedKey LEVEL;
   static NamespacedKey QUIT_PENALIZED;
   static NamespacedKey MOON_TYPE;
   static NamespacedKey SKIN;
   static NamespacedKey MINION_OF;
   private static Keys instance;

   private Keys() {
   }

   static Keys instance() {
      return instance;
   }

   static void init(Plugin p) {
      instance = new Keys();
      MOB = new NamespacedKey(p, "mob");
      BOSS = new NamespacedKey(p, "boss");
      SCALE = new NamespacedKey(p, "dmg_scale");
      DIFF = new NamespacedKey(p, "difficulty");
      TABLE = new NamespacedKey(p, "table");
      ITEM_ID = new NamespacedKey(p, "item_id");
      ITEM_ABILITIES = new NamespacedKey(p, "abilities");
      ITEM_PASSIVES = new NamespacedKey(p, "passives");
      ITEM_RARITY = new NamespacedKey(p, "rarity");
      ITEM_SERIAL = new NamespacedKey(p, "serial");
      BLEED = new NamespacedKey(p, "bleed_ticks");
      BLEED_DMG = new NamespacedKey(p, "bleed_damage");
      HEAL_AURA = new NamespacedKey(p, "heal_aura");
      LEVEL = new NamespacedKey(p, "level");
      QUIT_PENALIZED = new NamespacedKey(p, "quit_penalized");
      MOON_TYPE = new NamespacedKey(p, "moon_type");
      SKIN = new NamespacedKey(p, "skin");
      MINION_OF = new NamespacedKey(p, "minion_of");
   }
}
