<p align="center"><img src="assets/logo.png" width="128" height="128" alt="LunarWrath logo"></p>

<h1 align="center">LunarWrath: Blood Moon events for Paper and Purpur</h1>

<p align="center">Scheduled blood, black and purple moons with hordes, flying bosses, tiered loot and a world that punishes anyone who tries to wait the night out. Drop in one jar, no setup required.</p>

<p align="center">
  <a href="https://github.com/milkycloud-dev/lunar-wrath/actions/workflows/build.yml"><img src="https://github.com/milkycloud-dev/lunar-wrath/actions/workflows/build.yml/badge.svg" alt="Build"></a>
  <a href="https://github.com/milkycloud-dev/lunar-wrath/actions/workflows/release.yml"><img src="https://github.com/milkycloud-dev/lunar-wrath/actions/workflows/release.yml/badge.svg" alt="Release"></a>
  <a href="https://github.com/milkycloud-dev/lunar-wrath/releases/latest"><img src="https://img.shields.io/github/v/release/milkycloud-dev/lunar-wrath" alt="Latest release"></a>
  <a href="https://github.com/milkycloud-dev/lunar-wrath/releases"><img src="https://img.shields.io/github/downloads/milkycloud-dev/lunar-wrath/total" alt="Downloads"></a>
</p>

<p align="center"><a href="#english">English</a> | <a href="#русский">Русский</a></p>

---

<a id="english"></a>

## English

### Quick start

1. Download [`LunarWrath.jar`](https://github.com/milkycloud-dev/lunar-wrath/releases/latest) and put it in `plugins/`.
2. Restart the server (Paper or Purpur, Java 21).
3. In game: `/luna help`, then `/luna start` to try a moon right away.

That is it. By default a moon rises every Friday and Saturday at 21:00 UTC; change the schedule in `plugins/BloodMoonPlus/config.yml` and run `/luna reload`.

### Why it exists

This started as a translation of BloodMoonPlus 1.0.1. Then players found color codes leaking into item tooltips, the moon was trivial to sit out in the Nether or on a pillar, and bosses either never showed up or flattened half the spawn. Fixing that one piece at a time turned into rewriting most of the event logic, so by now it is its own plugin with its own release line.

The goal is simple: a few times a week the night turns hostile for everyone at once, it is loud and visible, and the only way through is to fight. Hiding gets you hurt, quitting gets you killed, stashing loot in a chest wakes something up, and nothing the event spawns is allowed to wreck what people built.

### How it works

A scheduler starts the moon at fixed times (weekly mode, by real time zone) or every N real hours. When it fires, every configured world gets locked into night and thunder, a boss bar with the remaining time appears, and hordes start spawning around each player. Waves grow with difficulty, which you set per schedule slot and which keeps rising while players camp.

Bosses are planned at the start and spawn near random participants. When the timer runs out the world is cleared of moon mobs, time jumps to dawn and survivors get their rewards. The session is saved, so a restart or reload in the middle of a moon resumes it with the same end time, difficulty and moon type.

### Features

- **Three moon types.** Blood, Black (stronger, darker, harder bosses) and Purple (astral mobs and effects), each with its own announcement, colors and boss bar. Start them by schedule or by hand.
- **Hordes.** Weighted mob lists, armor, health and damage scaling, per-player caps, special raiders (paratroopers, pyromancers, crossbowmen) at higher difficulty.
- **Eleven bosses.** Ground bosses with abilities (slam, charge, roar, summon, sonic boom, lightning, pull, guard, self heal), a Black Moon Ender Dragon and a flying Wither that hunt players in the Overworld. Damage leaderboard with its own reward table. Bosses never break blocks.
- **No hiding.**
  - Camping raises the difficulty with no upper limit and names the camper in chat; killing moon mobs lowers it and names the top killers.
  - Players in the Nether or the End are struck by lightning, high elytra flyers are knocked down with strikes that grow 50% stronger each time.
  - Opening a chest, barrel, ender chest or shulker box during the moon sets off a seal blast (no block damage) and, on the next try, summons a boss.
  - Quitting during the danger window kills the player on the spot.
- **Tiered loot.** Six rarities with pity counters, 45 custom items with abilities, separate tables for mobs, bosses and survivors. Epic, legendary and mythic drops get their own chat lines, sounds and particles. Vanilla items show in each player's own language.
- **Quiet chat.** Repeated deaths are merged into one line per minute, horde alerts go to the action bar, every announcement category can be switched off.
- **Admin tools.** Countdown and warnings (`/luna warn 10m lethal black start`), live lightning control with adjustable frequency, 13 scary sounds for the whole server, time and difficulty control, all with Tab completion.
- **Wither control.** Outside the moon the Wither is banned; during the moon players may build one. With WorldGuard the plugin toggles `deny-spawn` on `__global__` for you.
- **PlaceholderAPI.** About 60 placeholders under `%bloodmoon_...%`: status, time left, boss health, session stats, history, pity counters.
- **Languages.** English (default), Russian and Spanish. Missing keys fall back to the copy inside the jar, so an update never shows raw keys.

### Requirements

- Paper or Purpur 1.21.4 or newer (runs in production on Purpur 26.3), Java 21.
- Optional: PlaceholderAPI, WorldGuard 7 (Wither sync), Multiverse-Core, MythicMobs (boss mode `MYTHICMOBS`).

### Configuration

All files live in `plugins/BloodMoonPlus/` and are created on first start.

| File | What is in it |
|---|---|
| `config.yml` | worlds, schedule, event, hordes, difficulty, anti-camping (dynamic difficulty, dimension, elytra and container wrath, quit penalty), countdown, announcements. Every option is commented. |
| `bosses.yml` | bosses and their abilities (`TYPE:cooldown:args`), boss spawn plan |
| `items.yml` | custom items: material, name, lore, enchantments, abilities, passives |
| `loot.yml` | rarities, pity, loot tables for mobs, bosses and survivors |
| `locales/locales.yml` | `Language: en`, `ru` or `es` |
| `locales/<lang>.yml` | every message the plugin sends |
| `worlds/<world>/config.yml` | per-world overrides: permanent moon, beds, survivor and boss rewards |

Most used settings:

- `schedule.mode` (`weekly` or `interval`), `schedule.timezone`, `schedule.days` with times, difficulty and duration per slot.
- `event.global`: start in every loaded world or only in `worlds`.
- `dynamic-difficulty.max-difficulty`: `0` for no limit.
- `container-wrath.explosion.enabled`, `container-wrath.boss-enabled`, `warnings-before-punish`.
- `announce.*`: switch chat categories on or off.

Texts use [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Keep screen titles short (about 15 bold characters) so they fit any screen.

**Russian server?** Every release has `LunarWrath-russian-preset.zip`: a Russian `config.yml`, `items.yml`, `loot.yml`, `bosses.yml` and `locales.yml` with `Language: ru`. Unzip it into `plugins/BloodMoonPlus/`, move `ru.yml` into `locales/`, run `/luna reload`.

### Commands

`/luna` (aliases `/bm`, `/bloodmoon`). Tab completes every argument.

| Command | What it does |
|---|---|
| `/luna` | current moon status |
| `/luna next` | upcoming scheduled moons |
| `/luna help` | all commands |
| `/luna start [world] [xN] [time] [blood\|black\|purple]` | start a moon |
| `/luna blood\|black\|purple [world] [time] [xN]` | start a moon of that kind |
| `/luna stop [world\|all]` | stop the moon |
| `/luna warn <time> [normal\|dangerous\|lethal] [moon] [start]` | announce a moon, optionally start the countdown |
| `/luna countdown <time> [type] [moon] [world]` / `/luna cancel` | countdown to the start |
| `/luna time <status\|add\|take\|set> [time] [world\|all]` | time left |
| `/luna difficulty <xN> [world]` | set the difficulty |
| `/luna dynamic <status\|on\|off\|reset> [world\|all]` | difficulty growth from camping |
| `/luna boss <id> [world\|player]` | summon a boss |
| `/luna horde [world]` / `/luna killmobs [world]` | spawn a wave / remove moon mobs |
| `/luna lightning <storm\|smite\|strike\|frequency\|stop\|status>` | lightning effects, `frequency` changes running ones |
| `/luna sound <sound\|random> [all\|world\|player] [volume]` | play a scary sound |
| `/luna give <player> <item> [amount]` / `/luna items` / `/luna chances <table>` | items and loot tables |
| `/luna reload` | reload all configs |

### Permissions

| Permission | Default | What it gives |
|---|---|---|
| `bloodmoon.use` | everyone | `/luna`, `/luna next`, `/luna help` |
| `bloodmoon.admin` | op | every admin command |
| `bloodmoon.bypass` | op | exempt from the spectator lock |
| `bloodmoon.bypass.blockplace` | op | build while `block-placement.allow` is false |
| `bloodmoon.bypass.containerwrath` | op | open containers during the moon |
| `bloodmoon.bypass.dimensionwrath` | op | no lightning in the Nether and the End |
| `bloodmoon.bypass.elytrawrath` | op | no lightning when flying high |
| `bloodmoon.bypass.dynamicdifficulty` | op | camping does not raise the difficulty |
| `bloodmoon.bypass.quitpenalty` | op | leave during the moon without dying |

Operators, creative and spectator players are ignored by the anti-camping systems.

### Updating

Replace the jar and restart (or `plugman reload BloodMoonPlus`). Your config files are kept; options added in a new version use their defaults until you add them. Read [CHANGELOG.md](CHANGELOG.md) for what changed.

### Limits

- The plugin id is still `BloodMoonPlus` (data folder, PlaceholderAPI prefix, permission nodes), so old BloodMoonPlus installs upgrade by swapping the jar.
- Item, boss, rarity and loot table IDs (`hoja_eclipse`, `wither_sombra`, `LEGENDARIO`) come from the original plugin and are kept so saved data stays valid. Players never see them.
- Fixed night needs worlds with their own clock; the Nether and the End skip the time lock.
- WorldGuard sync works through console commands on `__global__` only.

### Releases

Every `v*` tag runs CI checks (version, changelog, locale parity), builds the jar and publishes a release with `LunarWrath.jar`, the Russian preset and notes from [CHANGELOG.md](CHANGELOG.md). Build locally with `python build.py` (JDK 21 and the Paper API on the classpath, see the workflow). `tools/deploy.py` can push a release to your own server over SFTP; it runs only where the repository variable `DEPLOY_ENABLED` is `true`.

### License

Proprietary, see [LICENSE](LICENSE). Based on BloodMoonPlus 1.0.1; rights to the original code stay with its authors.

---

<a id="русский"></a>

## Русский

### Быстрый старт

1. Скачайте [`LunarWrath.jar`](https://github.com/milkycloud-dev/lunar-wrath/releases/latest) и положите в `plugins/`.
2. Перезапустите сервер (Paper или Purpur, Java 21).
3. Для русского языка распакуйте `LunarWrath-russian-preset.zip` из того же релиза в `plugins/BloodMoonPlus/`, перенесите `ru.yml` в `locales/` и выполните `/luna reload`.
4. В игре: `/luna help`, затем `/luna start`, чтобы сразу проверить.

### Зачем это появилось

Начиналось как перевод BloodMoonPlus 1.0.1. Потом игроки нашли цветовые коды, которые вылезали в описаниях предметов, луну можно было спокойно пересидеть в Незере или на столбе, а боссы то не появлялись, то сносили половину спавна. Чинили по одному куску и в итоге переписали большую часть логики, так что теперь это отдельный плагин со своими релизами.

Задумка простая: несколько раз в неделю ночь становится враждебной сразу для всех, это громко и заметно, и пройти её можно только боем. Прятки наказываются, выход из игры убивает, сундук с припрятанным лутом будит стража, а всё, что порождает событие, не ломает построенное игроками.

### Как работает

Планировщик запускает луну в заданное время (недельный режим по реальному часовому поясу) или каждые N реальных часов. В выбранных мирах наступает ночь с грозой, появляется полоса с оставшимся временем, вокруг игроков спавнятся орды. Волны растут вместе со сложностью: её задают для каждого слота расписания, и она растёт дальше, пока игроки отсиживаются.

Боссы планируются в начале и появляются рядом со случайными участниками. Когда таймер кончается, мир очищается от мобов луны, наступает рассвет, выжившие получают награды. Сессия сохраняется: после рестарта посреди луны она продолжается с тем же временем окончания, сложностью и типом луны.

### Возможности

- **Три типа луны.** Кровавая, Чёрная (сильнее, темнее, жёстче боссы) и Фиолетовая (астральные мобы и эффекты), у каждой свои оповещения, цвета и полоса.
- **Орды.** Весовые списки мобов, броня, масштаб здоровья и урона, лимит на игрока, особые налётчики (десантники, пироманты, арбалетчики) на высокой сложности.
- **Одиннадцать боссов.** Наземные боссы со способностями, Дракон Края Чёрной Луны и летающий Иссушитель, которые охотятся на игроков в обычном мире. Топ урона со своей таблицей наград. Боссы не ломают блоки.
- **Не спрятаться.**
  - Отсиживание поднимает сложность без потолка и называет виновника в чате; убийства мобов луны снижают её и называют лучших бойцов.
  - В Незере и Энде бьют молнии, высоко летящих на элитрах сбивают ударами, каждый следующий на 50% сильнее.
  - Попытка открыть сундук, бочку, эндер-сундук или шалкер во время луны взрывает печать (блоки целы), следующая призывает босса.
  - Выход из игры в опасный период убивает игрока на месте.
- **Лут по редкостям.** Шесть редкостей с гарантом, 45 кастомных предметов со способностями, отдельные таблицы для мобов, боссов и выживших. Ванильные предметы показываются на языке клиента.
- **Тихий чат.** Повторные смерти сводятся в одну строку в минуту, оповещения об ордах идут в экшн-бар, любую категорию можно выключить.
- **Инструменты админа.** Отсчёт и предупреждения, управление молниями с настраиваемой частотой, 13 жутких звуков на весь сервер, управление временем и сложностью, везде автодополнение по Tab.
- **Контроль Иссушителя.** Вне луны запрещён, во время луны его можно построить; с WorldGuard плагин сам переключает `deny-spawn` у `__global__`.
- **PlaceholderAPI.** Около 60 плейсхолдеров `%bloodmoon_...%`.
- **Языки.** Английский (по умолчанию), русский и испанский.

### Требования

- Paper или Purpur 1.21.4 и новее (в работе на Purpur 26.3), Java 21.
- Необязательно: PlaceholderAPI, WorldGuard 7, Multiverse-Core, MythicMobs.

### Настройка

Все файлы в `plugins/BloodMoonPlus/`, создаются при первом запуске: `config.yml` (миры, расписание, орды, сложность, защита от отсиживания, отсчёт, оповещения, каждая опция с комментарием), `bosses.yml`, `items.yml`, `loot.yml`, `locales/locales.yml` (язык), `locales/<язык>.yml` (все сообщения), `worlds/<мир>/config.yml` (настройки мира).

Чаще всего меняют: `schedule.mode`, `schedule.timezone`, `schedule.days`; `event.global`; `dynamic-difficulty.max-difficulty` (`0` без потолка); `container-wrath.explosion.enabled` и `container-wrath.boss-enabled`; `announce.*`. Тексты в формате [MiniMessage](https://docs.advntr.dev/minimessage/format.html), титулы на экране держите короткими, около 15 жирных символов.

### Команды и права

Команды те же, что в английском разделе: `/luna help` показывает список на выбранном языке, Tab подсказывает все аргументы. Права: `bloodmoon.use` (все), `bloodmoon.admin` (оператор) и `bloodmoon.bypass.*` для обхода отдельных механик (оператор), полный список в таблице выше.

### Обновление

Замените jar и перезапустите сервер (или `plugman reload BloodMoonPlus`). Конфиги сохраняются, новые опции работают со значениями по умолчанию. Что изменилось, в [CHANGELOG.md](CHANGELOG.md).

### Ограничения

- Внутренний id остаётся `BloodMoonPlus` (папка данных, префикс PlaceholderAPI, права), старые установки BloodMoonPlus обновляются заменой jar.
- ID предметов, боссов, редкостей и таблиц (`hoja_eclipse`, `wither_sombra`, `LEGENDARIO`) из оригинала сохранены ради совместимости данных, игроки их не видят.
- Фиксированная ночь работает только в мирах со своими часами; в Незере и Энде время не фиксируется.

### Релизы

Каждый тег `v*` прогоняет проверки (версия, changelog, совпадение ключей локалей), собирает jar и публикует релиз с `LunarWrath.jar`, русским пресетом и заметками из [CHANGELOG.md](CHANGELOG.md). Локальная сборка: `python build.py`.

### Лицензия

Проприетарная, см. [LICENSE](LICENSE). Основано на BloodMoonPlus 1.0.1; права на исходный код оригинала остаются за его авторами.
