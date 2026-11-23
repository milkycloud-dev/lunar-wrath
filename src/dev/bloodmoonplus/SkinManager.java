package dev.bloodmoonplus;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkinManager {
   private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32,}");
   private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
   private final BloodMoonPlus plugin;
   private final Map<String, SkinManager.Skin> skins = new ConcurrentHashMap<>();
   private final Set<String> warned = ConcurrentHashMap.newKeySet();
   private volatile int generation = 0;

   SkinManager(BloodMoonPlus plugin) {
      this.plugin = plugin;
   }

   void reload() {
      int gen = ++this.generation;
      this.skins.clear();
      this.warned.clear();
      ConfigurationSection root = this.plugin.getConfig().getConfigurationSection("skins");
      if (root == null) {
         return;
      }
      for (String id : root.getKeys(false)) {
         ConfigurationSection s = root.getConfigurationSection(id);
         if (s == null) {
            continue;
         }
         Map<EquipmentSlot, Color> armor = new EnumMap<>(EquipmentSlot.class);
         ConfigurationSection a = s.getConfigurationSection("armor");
         if (a != null) {
            for (String slotName : a.getKeys(false)) {
               EquipmentSlot slot = switch (slotName.toLowerCase(Locale.ROOT)) {
                  case "chest", "chestplate" -> EquipmentSlot.CHEST;
                  case "legs", "leggings" -> EquipmentSlot.LEGS;
                  case "feet", "boots" -> EquipmentSlot.FEET;
                  default -> null;
               };
               Color c = parseColor(a.getString(slotName));
               if (slot == null || c == null) {
                  this.plugin.getLogger().warning("skins." + id + ".armor." + slotName + ": unknown slot or bad color (use chest, legs, feet and #RRGGBB)");
               } else {
                  armor.put(slot, c);
               }
            }
         }
         String key = id.toLowerCase(Locale.ROOT);
         String value = encodeTexture(s.getString("texture", ""));
         String player = s.getString("player", "").trim();
         if (value != null) {
            this.skins.put(key, new SkinManager.Skin(id, head(id, value), armor));
         } else if (!s.getString("texture", "").isBlank()) {
            this.plugin.getLogger().warning("skins." + id + ".texture: not a texture hash, textures.minecraft.net URL or base64 value");
            this.skins.put(key, new SkinManager.Skin(id, null, armor));
         } else if (NAME.matcher(player).matches()) {
            this.skins.put(key, new SkinManager.Skin(id, null, armor));
            this.resolveAsync(gen, key, id, player, armor);
         } else {
            this.plugin.getLogger().warning("skins." + id + ": set texture or a valid player name");
            this.skins.put(key, new SkinManager.Skin(id, null, armor));
         }
      }
      this.plugin.getLogger().info("Skins loaded: " + this.skins.size());
   }

   private void resolveAsync(int gen, String key, String id, String player, Map<EquipmentSlot, Color> armor) {
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         String value = null;
         try {
            value = fetchTexture(player);
         } catch (Exception e) {
            this.plugin.getLogger().warning("skins." + id + ": could not fetch skin of " + player + ": " + e.getMessage());
         }
         String resolved = value;
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (gen != this.generation) {
               return;
            }
            if (resolved == null) {
               this.plugin.getLogger().warning("skins." + id + ": skin of " + player + " not found, mobs spawn without the head");
               return;
            }
            this.skins.put(key, new SkinManager.Skin(id, head(id, resolved), armor));
            this.plugin.getLogger().info("skins." + id + ": skin of " + player + " fetched");
         });
      });
   }

   public boolean apply(LivingEntity le, String id) {
      SkinManager.Skin skin = id == null ? null : this.skins.get(id.toLowerCase(Locale.ROOT));
      if (skin == null) {
         if (id != null && this.warned.add(id.toLowerCase(Locale.ROOT))) {
            this.plugin.getLogger().warning("Unknown skin '" + id + "', check the skins section of config.yml");
         }
         return false;
      }
      EntityEquipment eq = le.getEquipment();
      if (eq == null) {
         return false;
      }
      if (skin.head() != null) {
         eq.setItem(EquipmentSlot.HEAD, skin.head().clone());
         eq.setDropChance(EquipmentSlot.HEAD, 0.0F);
      }
      for (Map.Entry<EquipmentSlot, Color> en : skin.armor().entrySet()) {
         Material mat = switch (en.getKey()) {
            case CHEST -> Material.LEATHER_CHESTPLATE;
            case LEGS -> Material.LEATHER_LEGGINGS;
            default -> Material.LEATHER_BOOTS;
         };
         ItemStack piece = new ItemStack(mat);
         if (piece.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(en.getValue());
            piece.setItemMeta(lm);
         }
         eq.setItem(en.getKey(), piece);
         eq.setDropChance(en.getKey(), 0.0F);
      }
      if (le instanceof Mob) {
         le.setCanPickupItems(false);
      }
      le.getPersistentDataContainer().set(Keys.SKIN, org.bukkit.persistence.PersistentDataType.STRING, skin.id());
      return true;
   }

   private static String fetchTexture(String player) throws Exception {
      HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      HttpResponse<String> r = http.send(
         HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + player)).timeout(Duration.ofSeconds(10)).build(),
         HttpResponse.BodyHandlers.ofString()
      );
      Matcher id = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"").matcher(r.body());
      if (r.statusCode() != 200 || !id.find()) {
         return null;
      }
      r = http.send(
         HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id.group(1))).timeout(Duration.ofSeconds(10)).build(),
         HttpResponse.BodyHandlers.ofString()
      );
      Matcher value = Pattern.compile("\"value\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").matcher(r.body());
      return r.statusCode() == 200 && value.find() ? value.group(1) : null;
   }

   private static ItemStack head(String id, String textureValue) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      if (item.getItemMeta() instanceof SkullMeta meta) {
         UUID uuid = UUID.nameUUIDFromBytes(("LunarWrathSkin:" + id).getBytes(StandardCharsets.UTF_8));
         PlayerProfile profile = Bukkit.createProfile(uuid, null);
         profile.setProperty(new ProfileProperty("textures", textureValue));
         meta.setPlayerProfile(profile);
         item.setItemMeta(meta);
      }
      return item;
   }

   static String encodeTexture(String raw) {
      if (raw == null) {
         return null;
      }
      String t = raw.trim();
      if (t.isEmpty()) {
         return null;
      }
      String url = null;
      if (t.startsWith("http://") || t.startsWith("https://")) {
         String tail = t.substring(t.lastIndexOf('/') + 1);
         if (HASH.matcher(tail).matches()) {
            url = "http://textures.minecraft.net/texture/" + tail.toLowerCase(Locale.ROOT);
         }
      } else if (HASH.matcher(t).matches()) {
         url = "http://textures.minecraft.net/texture/" + t.toLowerCase(Locale.ROOT);
      } else {
         try {
            String json = new String(Base64.getDecoder().decode(t), StandardCharsets.UTF_8);
            if (json.contains("\"textures\"") && json.contains("textures.minecraft.net")) {
               return t;
            }
         } catch (IllegalArgumentException ignored) {
         }
         return null;
      }
      if (url == null) {
         return null;
      }
      String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
      return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
   }

   static Color parseColor(String s) {
      if (s == null) {
         return null;
      }
      String t = s.trim();
      if (t.startsWith("#")) {
         t = t.substring(1);
      }
      if (!t.matches("[0-9a-fA-F]{6}")) {
         return null;
      }
      return Color.fromRGB(Integer.parseInt(t, 16));
   }

   static record Skin(String id, ItemStack head, Map<EquipmentSlot, Color> armor) {
   }
}
