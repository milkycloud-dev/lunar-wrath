# Changelog

## 1.2.1 (2026-11-23)

- Horde mobs get per-hit caps too: `horde.max-damage` (raw damage before armor, default 30), `horde.max-hit` (after armor, default 10) and `horde.max-hit-fraction` (default 0.5). Bosses get `difficulty.bosses.max-damage` (default 40) and a per-boss `max-damage`. Capping raw damage before armor keeps Netherite and Protection useful at any difficulty: at x10 Black Moon a full Netherite Protection IV player takes at most 4.75 per horde hit, an unarmored one 10.
- `/luna killmobs` now clears every world, bosses included, also when no moon is running (before it removed nothing without an active moon and left bosses called with `/luna boss`). Moon mobs found in chunks loaded later, in a world with no active moon, are removed.
- Boss bar is shown only near the boss: `difficulty.bosses.bar-radius` (default 50 blocks, 0 = whole world). Before it was shown to every player in the world. Each boss can override it with `bar-radius` in `bosses.yml`; the Ender Dragon uses 100 because it circles wide.
- Russian preset: Little eturnercus weight 7 (slightly below the common horde mobs), names written in lower case. Mad eturnercus spawns with chance 0.4 at 4.5 minutes.
- Every boss can spawn on a regular moon: `min-difficulty` is 1.0 for all of them in `bosses.yml` (before the Warden and the Ender Dragon needed x2.0 and the Crimson Scourge x2.5, so with scheduled moons at x1.4 to x1.6 they never appeared, and four more bosses needed x1.5). The Warden, the Ender Dragon and both Withers roll at 0.6 like the other bosses (0.5 to 0.7), the Blood Zombie King stays the most frequent at 1.0.
- The vanilla boss bar of Withers and the Ender Dragon spawned as bosses is hidden, so only the plugin bar with its radius is shown.

## 1.2.0 (2026-11-23)

- Boss damage no longer one-shots players. Ability damage (slam, explosion, lightning, sonic boom, charge) was multiplied twice by the mob damage scale; now it is scaled once. Bosses get their own difficulty scaling `difficulty.scaling.boss-damage` (default +10% per level, capped at x10 by `boss-damage-max-difficulty`) instead of the mob `damage` scaling.
- Per-hit damage cap for bosses: `difficulty.bosses.max-hit` (health points after armor, default 14) and `difficulty.bosses.max-hit-fraction` (share of the player's max health, default 0.75), the lower one wins. Covers melee, projectiles, Wither skulls, explosions, dragon breath, abilities and mobs a boss summons. Each boss can override it with `max-hit` in `bosses.yml`; new `ability-damage` multiplier per boss.
- Retuned bosses: both Withers, the Ender Dragon and the Warden deal much less raw damage, every boss has a `max-hit` between 8 and 16.
- Horde odds: skeletons weight 34 (was 25), zombies 22 (was 35), husks 10 (was 12). Incendium piglins have weights `difficulty.incendium.weights` (elytra paratrooper 3, crossbowman 1, pyromancer 1) and their own `difficulty.incendium.min-difficulty` (default 1.0, was tied to epic effects at x3).
- Player skins for mobs: new `skins` section in `config.yml` (texture hash, textures.minecraft.net URL, base64 value or a player name fetched from Mojang) with optional leather armor colors. Horde mobs and bosses take `skin: <id>`; horde mobs also take `id` and `scale`, and `SUMMON` can summon a horde mob by id.
- Russian preset: Little eturnercus (horde zombie with the eturnercus skin) and the boss Mad eturnercus (giant zombie with the same skin that summons Little eturnercus).

## 1.1.1 (2026-11-21)

- `/luna lightning storm` and `/luna lightning frequency` no longer cap the storm at 50 strikes per second. New option `lightning.max-strikes-per-second` in `config.yml`: `0` (default) means no limit, any positive value sets a cap. Applied on `/luna reload`.

## 1.1.0 (2026-11-21)

- Full English translation for the public release. English is the default language; bundled `config.yml`, `items.yml`, `loot.yml`, `bosses.yml`, the world template and the MythicMobs example are in English, and every chat, title and admin message goes through `locales/<lang>.yml` (about 150 new keys under `text`, `moon`, `time`, `mob`).
- Russian stays complete: `locales/ru.yml` has every key, and `presets/ru/` holds the Russian `config.yml`, `items.yml`, `loot.yml`, `bosses.yml` and `locales.yml`. Releases ship it as `LunarWrath-russian-preset.zip`.
- Server logs are in English instead of Spanish and Russian.
- Black and purple moon renaming of warning texts is configurable per language (`moon.rename`), moon names come from `moon.name`.
- `plugin.yml` declares all `bloodmoon.bypass.*` permissions with descriptions.
- Default schedule for new installs: Friday and Saturday at 21:00 UTC.
- Releases: CI checks that the tag matches `plugin.yml`, that the changelog has the version, that `en.yml` and `ru.yml` have the same keys and placeholders, that presets cover every option and that English defaults contain no Russian text. Release notes start with install steps.
- `tools/deploy.py`: optional automatic deploy of a release to your own server over SFTP with a remote console reload check, enabled per repository with `DEPLOY_ENABLED`.
- Removed the unused `messages.yml`.

## 1.0.14 (2026-11-21)

- `/luna chances`: limited items show `(выдано: N/M)` instead of the Spanish `(únicos: N/M)`.

## 1.0.13 (2026-11-21)

- Rarer legendary and mythic drops: base weights 10 to 7 and 1.5 to 1, every table's `LEGENDARIO` and `MITICO` weight cut by 30%, pity lowered (legendary 0.03 per roll up to 150 stacks, mythic 0.05 up to 200).
- Difficulty no longer inflates loot without limit: `rarity-luck` 0.6 to 0.45, and `rarity-luck` and `loot-chance` stop growing above x5 (`*-max-difficulty: 5.0`). Needed since camping difficulty became uncapped in 1.0.11.
- Difficulty decay message names up to three players with the most moon-mob kills since the previous decay (`{players}`, also `{player}` for the last kill).
- Loot broadcasts, the legendary action bar, the mythic title and `/luna chances` show vanilla items by their translated name (`<lang:...>`) instead of the raw material id such as `NETHERITE_SCRAP`; mob kill messages show translated mob names.

## 1.0.12 (2026-11-21)

- Full-screen titles and subtitles shortened so they fit the screen at GUI scale 4 on 1920x1080 (title up to 15 bold characters, subtitle up to about 35 regular): quit warning, join warning, countdown, dangerous and lethal warnings, black and purple moon, moon start, mythic drop. Bold removed from subtitles, emoji removed from titles.
- Same texts updated in `config.yml`, locales, `messages.yml` and the built-in defaults.

## 1.0.11 (2026-11-21)

- Dynamic difficulty is uncapped by default: `dynamic-difficulty.max-difficulty: 0` means no limit, any positive value sets a cap. Mob stats stay bounded by `difficulty.max-health-cap`, `speed-cap` and `max-horde-size-multiplier`.
- Difficulty escalation is announced in chat with the camper's name (`broadcast-chat: true`, `{player}` in `announcement.escalate`). Dimension and elytra wrath also name the player in chat, every `broadcast-interval-seconds`.
- New `death-summary`: the first death of a player during the moon is announced as before; further deaths within `window-seconds` (60) are hidden from chat and posted as one summary line per window.
- Container wrath: attempts that do not summon the boss now set off a sealed-chest blast that breaks no blocks, hits only the opener for `explosion.damage` (6 = 3 hearts) and knocks them back, with a chat warning not to open chests. `explosion.enabled` and `boss-enabled` switch both parts off separately.
- Lightning: `/luna lightning frequency <storm|smite> <player|all> <value>` changes running effects live (storm strikes per second 0.05-50, smite interval from 0.1 s); smite intervals accept fractions.
- New `/luna sound <sound|random> [all|world|player] [volume]` with 13 presets (wither, dragon, warden, roar, heartbeat, sonic, curse, ghast, scream, cave, horn, bell, thunder).
- Commands unified: English subcommand names everywhere, Tab offers only them plus arguments for every subcommand; old Russian and Spanish aliases still work. New `/luna help`; unknown subcommands point to it. Removed the non-existent `survivors` from usage.

## 1.0.10 (2026-11-21)

- Elytra wrath damage escalates: each consecutive lightning strike deals `damage-multiplier` (default 1.5) times the previous one, capped at `max-damage` (40). The streak survives landing, healing and relogging and resets only after `streak-reset-seconds` (60) without a strike, on death or when the moon ends.
- One-time chat warning once the next strike would deal `warn-damage` (8) or more, showing its damage in hearts.
- New container wrath: chests, trapped chests, barrels, ender chests, shulker boxes and chest/hopper minecarts cannot be opened in a world with an active moon. The first attempt only warns; later attempts summon the `wither_sombra` boss next to the container, at most once per player every `punish-cooldown-seconds` (180). Ops, creative/spectator and `bloodmoon.bypass.containerwrath` are exempt; containers already denied by protection plugins are ignored.
- New `container-wrath` section and new `elytra-wrath` keys in `config.yml`; missing keys fall back to the defaults above.
- Restored the ability summary line and the `Владелец: {owner}` line in the lore of 23 items.
- `plugin.yml` descriptions and the world template survivor message are now Russian.
- Root and `src` copies of `items.yml`, `loot.yml` and `messages.yml` match the files deployed on the server.

## 1.0.9 (2026-11-20)

- Project renamed to **LunarWrath** and moved to `milkycloud-dev/lunar-wrath`; it is no longer a translation of BloodMoonPlus but a separate plugin built on it.
- Plugin id stays `BloodMoonPlus` (data folder, permissions, PlaceholderAPI prefix), so existing installs upgrade by swapping the jar.
- `plugin.yml`: version 1.0.9, English description, author and website.
- Build output renamed to `LunarWrath.jar`; release workflow builds the jar on every `v*` tag and publishes notes from this file.
- New README (English and Russian), logo and proprietary license with credit to the original BloodMoonPlus.

## 1.0.8 (2026-11-20)

- Fixed `EpicEffectsManager`: world border size 59999996 exceeded the 59999968 limit and threw every second during epic moons; red vignette, spores and fireworks now run.
- Fixed `DRAGON_BREATH` particles in `DimensionWrathManager` and purple moon start: 26.x requires Float data. Purple moon start no longer aborts before quit penalty, wither control, spectator kick, start commands and boss planning.
- `config.yml`: `wither-control` moved out of the middle of `event`, so `reward-on-stop`, `commands-on-*`, `survivor-commands` and `start-announcement` are read again; removed stray duplicate `horde: 0.20`.
- Resumed sessions after restart or reload now unlock the wither in WorldGuard and keep their moon type (`active.<world>.type` in `data.yml`).
- WorldGuard sync keeps other mobs listed in `wither-control.keep-deny-spawn` instead of clearing the whole `deny-spawn` flag.
- Player-built withers during the moon are no longer force-uncancelled, so region protections and other plugins still apply. Bosses still spawn.
- `BlockExplodeEvent` terrain protection now applies only while the moon is active in that world; `EntityChangeBlockEvent` protection only to moon mobs, so falling blocks, villagers and farmland work normally.
- Bosses stay tracked while their chunk is unloaded; untracked boss entities are removed when their chunk loads.
- Death cause and mob names are escaped before MiniMessage parsing (no tag injection through renamed items or name tags).
- Locale files fall back to the bundled jar copy for missing keys; loot and death fallbacks detect missing keys correctly.

## 1.0.7 (2026-11-19)

- **Chat Spam Reduction During Blood Moon:**
  - **Horde Wave Alerts:** Set default `announce.horde: false`. When a horde targets a player, an Actionbar alert (`⚡ Орда нападает на вас! ⚡`) and ravager roar sound are delivered directly and personally to that player, eliminating global chat spam.
  - **Dynamic Difficulty Broadcast Throttling:** Added `dynamic-difficulty.broadcast-chat: false` (default `false`). When difficulty increases due to camping or decays from combat, the camping player is notified directly via Actionbar and personal chat message without spamming everyone in global chat.
  - **Dimension & Elytra Wrath Direct Messaging:** Added `broadcast-chat: false` (default `false`) in both `dimension-wrath` and `elytra-wrath`. Repeated 30-second penalty warnings are sent directly to the offending player in Nether, End, or the sky, preserving global chat clarity until their eventual death.
- **Blood Moon Player Death Broadcasting:**
  - Enabled `announce.player-deaths: true` by default.
  - In `EventListener.onPlayerDeath`: When a player dies during Blood Moon, strikes lightning, plays deep thunder sound (`Sound.ENTITY_LIGHTNING_BOLT_THUNDER`), sends Actionbar alert to all players in that world (`☠ <player> погиб во время Кровавой Луны!`), and broadcasts atmospheric event death message in chat with cleaned cause description (`☠ <player> пал во время Кровавой Луны! (<cause>)`), replacing redundant vanilla death messages (`e.deathMessage(null)`).
- **Tiered Loot Drop Highlights:**
  - **Epic (`EPICO`):** Enabled `broadcast: true` in `loot.yml`. Broadcasts a clean, simple line in chat (`✦ <player> получил эпический предмет: <item>`) with a subtle pickup sound.
  - **Legendary (`LEGENDARIO`):** Prominent gold announcement in chat (`★ ЛЕГЕНДАРНЫЙ ДРОП: <player> выбил реликвию <item>!`), toast sound, personal Actionbar alert, and golden totem particle burst (`Particle.TOTEM_OF_UNDYING`).
  - **Mythic (`MITICO`):** Major gradient banner in chat (`⚡ МИФИЧЕСКИЙ АРТЕФАКТ ⚡ <player> пробудил древнюю реликвию: <item>!`), resonant bell chime and toast challenge sounds, personal on-screen Title and Actionbar, and particle explosion (`Particle.PORTAL` + `Particle.SOUL_FIRE_FLAME`).

## 1.0.6 (2026-11-19)

- **Dynamic Wither Control & WorldGuard Synchronization (`WitherControlManager`):**
  - **Blood Moon Lifecycle Automation:** Automatically clears WorldGuard's `deny-spawn` on `__global__` in configured worlds when Blood Moon begins, allowing Withers to spawn.
  - **Peacetime Re-Locking:** Automatically restores WorldGuard's `deny-spawn: [minecraft:wither]` on `__global__` immediately when Blood Moon ends.
  - **Fail-Safe Startup and Shutdown:** Verifies active session state on reload or enable; on plugin disable or server shutdown, guarantees that `deny-spawn: [minecraft:wither]` is restored.
  - **Dual-Layer Protection in `EventListener`:** Custom `onWitherSpawn` handler blocks player-spawned Withers outside Blood Moon with localized message (`event.wither-only-bloodmoon`), permits them during Blood Moon, and unconditionally un-cancels Blood Moon boss spawns (`BossManager.isSpawningBoss()`).
  - **Configurable:** Added `wither-control:` block in `config.yml` with `enabled`, `allow-only-during-bloodmoon`, `sync-worldguard`, and `worlds`.

## 1.0.5 (2026-11-19)

- **Standalone Boss Summoning Outside Blood Moon:**
  - `/luna boss <id> [world|player]` now supports summoning bosses anytime on demand, even when Blood Moon is completely inactive.
  - Added support for targeting specific players or worlds directly in the command.
  - Tab completion now auto-completes boss IDs at argument 2, and online players or worlds at argument 3.
- **Enhanced Non-Destructive Wither Boss (`wither_sombra`, `azote_carmesi`):**
  - **Zero Block Destruction Guarantee:** Cancelled `EntityChangeBlockEvent` (prevents wither eating/breaking terrain), cleared `blockList()` with zero yield in `EntityExplodeEvent` and `BlockExplodeEvent`, and disabled explosion fires in `ExplosionPrimeEvent`.
  - **Aggressive Aerial Pursuit:** Flying AI applies continuous vertical and horizontal vectors towards victims, hovering ~3.5 blocks above them.
  - **Three-Headed Targeting:** Main head and both side heads (`Wither.Head.CENTER`, `LEFT`, `RIGHT`) simultaneously lock onto survival/adventure victims.
  - **Multi-Skull Barrage:** Fires spread wither skulls at nearby players with zero explosion yield.
  - **Explosion Shockwave (`EXPLODE` ability):** Powerful radial explosion with soul flames, custom sounds, and player knockback that deals zero block damage.
  - **Survival/Adventure Targeting:** Admins in Survival mode can now engage and test bosses directly, while Spectator and Creative players remain untargetable.

## 1.0.4 (2026-11-18)

- **Countdown Parser & Single Warning Logic:**
  - Fixed parameter parsing in `/luna countdown` when specifying duration units like `5m`, `10m`, `30s` (previously defaulted to 30 seconds due to duration token collision).
  - Implemented single-warning countdown mode: sending strictly one atmospheric warning announcement at scheduled time, with zero periodic chat spam during the countdown until the moon rises (`countdown.periodic-alerts: false`).
- **Non-Destructive Black Moon Bosses:**
  - Added Ender Dragon boss (`dragon_negro` / `Чёрный Дракон Края`): actively hunts players in the Overworld without destroying blocks (cancelled `EntityChangeBlockEvent` and block-cleared explosions).
  - Added Wither boss (`wither_sombra` / `Теневой Исушитель`): hunts players and fires wither skulls without destroying terrain.
  - Registered both bosses in `bosses.yml` with spawn tables and abilities (`/luna boss dragon_negro`, `/luna boss wither_sombra`).
- **Operator (OP) Permissions & Bypass:**
  - Players with OP (`p.isOp()`) are completely exempt and allowed to switch to Spectator mode (`/gm 3`, `/gamemode spectator`) without being kicked.
  - Blood Moon mobs and bosses ignore OP players (`EntityTargetLivingEntityEvent` cancelled).
  - OP players are ignored by Dimension Wrath, Elytra Wrath, Dynamic Difficulty camping tracking, and Quit Penalty.
- **Comprehensive Tab Completion:**
  - Added auto-completion across all subcommands, time formats, warning levels, moon types, and boss identifiers via Tab.

## 1.0.3 (2026-11-18)

- **Moon Types (Black Moon & Purple Moon):**
  - Added support for 3 distinct Moon variations: classic Blood Moon (`blood`), Black Moon (`black`), and Purple Moon (`purple`).
  - Added quick start commands: `/luna black [duration] [diff] [world]`, `/luna purple`, `/luna blood` (with Russian aliases `черная`, `фиолетовая`, `кровавая`).
  - Black Moon features darkness aura, 1.5x mob damage/health multiplier by default, 1.5x horde size multiplier, and purple boss bar.
  - Purple Moon features astral/void theme, 1.3x mob stats, 1.25x movement speed, and dragon breath/portal particles.
  - Fully configurable attributes, announcements, bossbar colors, and multipliers in `config.yml` under `moon-types:`.
- **Three Warning Types (Normal, Dangerous, Lethal):**
  - Added 3 distinct warning levels for planned Blood Moons via `/luna warn <time> [normal|dangerous|lethal] [moon_type] [start]`.
  - Added atmospheric chat broadcasts with gradients, full-screen titles, and custom sound effects.
  - Added support for `start` flag in `/luna warn` to schedule and start countdown automatically.
- **Dynamic Difficulty Escalation (Anti-Camping & Anti-Passivity):**
  - Automatically escalates Blood Moon difficulty (+0.25x per 25s up to x5.0) when players hide in bunkers or avoid combat for > 45 seconds.
  - Spawns punitive hordes around camping players to force engagement.
  - Gradually decreases difficulty (-0.1x) when players actively engage in combat and defeat monsters.
  - Added management commands: `/luna dynamic status`, `/luna dynamic on|off`, `/luna dynamic reset [world]`.
- **Dimension Wrath (Nether & The End Anti-Camping):**
  - Prevents players from evading the event by hiding in Nether or The End.
  - Quadrupled damage (4.0 HP / 2 full hearts every 2 seconds) with targeted lightning strikes and dimension-specific particles.
  - Broadcasts warning alerts to chat every 30 seconds and shows actionbar alerts.
- **Elytra Wrath (Sky Flight Anti-Cheat):**
  - Detects players gliding on Elytra at high altitude (Y >= 90) for > 3 seconds.
  - Strikes with lightning, collapses Elytra flight (`setGliding(false)`), inflicts 4.0 HP damage every 2 seconds, and broadcasts alerts every 30 seconds.
- **Spectator Mode (GM 3) Lockdown:**
  - Blocks `/gm 3`, `/gamemode spectator`, and related commands during countdown and active Blood Moon.
  - Automatically kicks existing spectators back into Survival mode when the event or countdown begins.
- **Timer Adjustment Controls:**
  - Added `/luna time status`, `/luna time add <time> [world]`, `/luna time take <time> [world]`, `/luna time set <time> [world]`.
- **Lightning Control Commands:**
  - Added `/luna lightning storm`, `smite`, `strike`, `stop`, `status`.
- **Scheduled Warning Clean-Up:**
  - Scheduled warnings set strictly to 10 minutes before start (`schedule.warnings-minutes: [10]`), eliminating 5-minute and 1-minute spam.

## 1.0.2 (2026-11-18)

- **Countdown & Warning System:**
  - Added `/luna countdown <seconds> [difficulty] [duration] [world]` command (with Russian aliases `отсчет`, `таймер`, `обратный-отсчет`).
  - Added support for `/luna start in <seconds>` / `/luna start <seconds>s` syntax.
  - Added `/luna cancel` / `/luna countdown cancel` command to abort scheduled countdowns.
  - Added configurable scary sound effects (`entity.wither.ambient`, `entity.ender_dragon.growl`) on warning intervals.
  - Added ticking heartbeat sound (`block.note_block.basedrum`) on the final 5 seconds (5, 4, 3, 2, 1) with accelerating pitch.
- **Hidden Event Parameters (Immersion):**
  - Added `warning.show-parameters: false` toggle (default: `false`).
  - Scheduled warnings, countdown alerts, and start messages no longer leak difficulty multipliers (`x{difficulty}`) or duration, preserving immersion and mystery.
- **Block Placement Fix & Configuration:**
  - Fixed client/server WorldBorder limitation that prevented players from placing blocks during Blood Moon (expanded vignette border to maximum world size 59,999,996 with infinite warning distance).
  - Added configurable `block-placement:` toggle (`allow: true/false`, `exempt-creative: true`, `bypass-permission`, `deny-message`).
- **Full Warning & Announcement Customization:**
  - All start titles, subtitles, chat alerts, and countdown messages are now fully configurable directly in `config.yml` under `event.start-announcement:`, `countdown:`, and `warning:`.
  - Fixed multi-world announcement duplication in `QuitPenaltyManager` (now dispatched per-world without duplicate broadcast spam).
- **Quit Penalty Module ("Blood Moon Evolution"):**
  - Players who disconnect during the active penalty cooldown (default 60 seconds) die immediately upon logout.
  - Added full-screen scary warning title and subtitle with configurable delay and duration.
  - Added terrifying red chat broadcast warning upon Blood Moon start.
  - Added server-wide coward death broadcast when a player logs out and dies during the penalty window.
  - Added reconnect punishment verification via PersistentDataContainer (`bloodmoon:quit_penalized`).
  - Added join warning with remaining danger cooldown timer.
  - Added comprehensive configuration options in `config.yml` under `quit-penalty:` (duration, messages, sounds, creative bypass, drop toggles).
- **Mob & Combat Enhancements:**
  - Fixed mob infighting during hordes (`combat.prevent-mob-infighting: true`).
  - Scaled lightning strike frequency and rain thresholds for intense atmospheric immersion.
  - Unlocked difficulty scaling up to 1000.0 without entity or wave flooding.

## 1.0.1 (2026-11-17)

- Исправлено отображение цветового кода &7 в описании (lore) кастомных предметов (items.yml): амперсанд заменен на тег <gray> для корректного отображения в MiniMessage без артефактов.
- Разблокировано масштабирование сложности до 1000.0 без лагов спавна и перегрузки сущностей.
- Интегрированы уровни мобов и русские кастомные титулы.
- Добавлен модуль эпических эффектов для высоких сложностей.
- Обновлена конфигурация по умолчанию и приложен обновленный бинарный пакет плагина BloodMoonPlus-1.0.1-RU.jar в релизе.

## 1.0.0 (2026-11-11)

First public release of the Russian localization package.

- Clean, standalone Russian localization and configuration files for BloodMoonPlus v1.0.1.
- Verified compatibility, zero style/spelling warnings.
- Pre-built binary package attached in GitHub Releases.
