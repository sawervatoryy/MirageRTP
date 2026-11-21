# MirageRTP `v0.1`

Современный плагин случайной телепортации для **Paper 1.21.4** (Java 21).
Несколько режимов РТП, анимированное меню, асинхронный поиск безопасной точки и физические **РТП-ямы**, в которые достаточно прыгнуть.

> Автор: **sawervatoryy** · MirageStudio

---

## ⌈ Возможности ⌋

- **Режимы РТП** — обычный, дальний, рядом с игроками, Незер, Энд (добавляются в `config.yml` без правки кода). У каждого свои радиусы, права, мир, точка отсчёта, стоимость и кулдауны.
- **Асинхронный поиск** — чанки грузятся через `World#getChunkAtAsync`, без фризов. Исключаются лава, вода, огонь, кактусы, пустота, листья, опасные блоки рядом и (опционально) регионы WorldGuard.
- **Задержка 3 сек** — игрок не должен двигаться (проверяются X/Y/Z, поворот головы не учитывается). Отмена при движении, уроне и чужом телепорте. Отсчёт в ActionBar/Title, частицы и звуки.
- **GUI-меню** с анимацией появления либо режим без GUI (`use-gui: false`).
- **РТП-ямы** — зоны по двум углам, мгновенная телепортация по привязанному режиму, анти-спам, эффекты и фоновые частицы.
- **Экономика** — Vault и PlayerPoints; при неудачной телепортации средства возвращаются.
- **PlaceholderAPI** — `%miragertp_cooldown_<режим>%`.

---

## ⌈ Установка ⌋

1. Положите `MirageRTP-v0.1.jar` в `plugins/`.
2. Перезапустите сервер — создадутся `config.yml`, `lang.yml`, `menus.yml`, `pits.yml`.
3. Настройте режимы и миры в `config.yml`, затем `/rtp reload`.

**Зависимости (все необязательные, `softdepend`):**

| Плагин | Зачем |
|---|---|
| Vault + плагин экономики | платные режимы (`cost.type: VAULT`) |
| PlayerPoints | платные режимы (`cost.type: POINTS`) |
| WorldGuard | не телепортировать в чужие приваты |
| PlaceholderAPI | плейсхолдеры |

---

## ⌈ Команды ⌋

| Команда | Описание | Право |
|---|---|---|
| `/rtp` (`/wild`, `/randomtp`) | Открыть меню (или телепорт по `default-mode`, если `use-gui: false`) | `miragertp.use` |
| `/rtp <режим>` | Телепорт сразу по режиму: `default`, `far`, `near`, `nether`, `end` | `miragertp.use` + право режима |
| `/rtp reload` | Перезагрузить все конфиги (чтение асинхронно) | `miragertp.admin` |
| `/rtp force <игрок> [режим]` | Принудительно телепортировать игрока (без задержки, кулдауна, стоимости и проверки прав) | `miragertp.admin` |
| `/rtp pit create <название> <режим>` | Создать яму | `miragertp.admin` |
| `/rtp pit setpos1 <название>` | Первый угол — по блоку, где вы стоите | `miragertp.admin` |
| `/rtp pit setpos2 <название>` | Второй угол | `miragertp.admin` |
| `/rtp pit delete <название>` | Удалить яму | `miragertp.admin` |
| `/rtp pit list` | Список ям | `miragertp.admin` |

---

## ⌈ Права ⌋

| Право | По умолчанию | Описание |
|---|---|---|
| `miragertp.use` | все | Использование `/rtp` |
| `miragertp.admin` | OP | Админ-команды |
| `miragertp.<режим>` | — | Право на режим — задаётся полем `permission` (например `miragertp.far`) |
| `miragertp.cooldown.<группа>` | — | Сокращённый кулдаун группы (например `vip`, `premium`) |
| `miragertp.bypass.cooldown` | OP | Игнорировать кулдаун |
| `miragertp.bypass.delay` | — | Игнорировать задержку и запрет на движение |
| `miragertp.bypass.cost` | OP | Не платить за режим |
| `miragertp.exempt` | — | Игрока нельзя выбрать целью режима «рядом с игроками» |

---

## ⌈ Плейсхолдеры ⌋

| Плейсхолдер | Результат |
|---|---|
| `%miragertp_cooldown_default%` | Оставшееся время (`1м 5с`) или `Готово` |
| `%miragertp_cooldown_far%` | то же для режима `far` |

Вместо `default`/`far` подставьте id любого режима из `config.yml`. Текст «Готово» — ключ `placeholder-ready` в `lang.yml`.

---

## ⌈ Настройка режимов ⌋

```yaml
modes:
  far:
    display-name: "&#00ACFFДальний РТП"
    permission: miragertp.far          # пусто — без права
    world: current                     # current = мир игрока, либо имя мира
    center: ZERO                       # SPAWN | ZERO | PLAYER | RANDOM_PLAYER
    min-radius: 5000
    max-radius: 15000
    cost: { type: VAULT, amount: 500 } # NONE | VAULT | POINTS
    cooldowns: { default: 300, vip: 150, premium: 60 }
```

- **Кулдаун** берётся из `default`; если у игрока есть `miragertp.cooldown.vip`, применяется значение `vip`. Из нескольких подходящих групп выбирается наименьшее.
- **center `RANDOM_PLAYER`** — центр поиска вокруг случайного другого игрока в целевом мире (радиусы задают расстояние от него).
- Новый режим = новый блок в `modes` + (по желанию) предмет в `menus.yml` + `/rtp reload`.

### Основные параметры `config.yml`

| Ключ | Описание |
|---|---|
| `use-gui` | `true` — `/rtp` открывает меню; `false` — сразу телепорт по `default-mode` |
| `delay.seconds` | Задержка перед телепортацией (по умолчанию `3`, `0` — отключить) |
| `delay.move-tolerance` | Допуск смещения в блоках; `0.0` — отмена при любом изменении X/Y/Z |
| `delay.display` | `ACTIONBAR`, `TITLE` или `BOTH` |
| `search.max-attempts` | Число попыток найти безопасную точку |
| `search.unsafe-blocks` | Список запрещённых блоков |
| `search.avoid-worldguard-regions` | Пропускать любые регионы WorldGuard |
| `protection.damage-immunity-seconds` | Полный иммунитет к любому урону после телепортации (по умолчанию 5 секунд, 0 — отключить) |
| `debug` | Логировать координаты телепортаций и отменённый урон от падения |
| `effects.*` | Частицы и звуки (отсчёт, отмена, телепорт, яма) |

---

## ⌈ РТП-ямы ⌋

Яма — прямоугольная зона между двумя точками. Когда игрок **входит** в неё, он мгновенно телепортируется по привязанному режиму — в обход 3-секундной задержки и запрета на движение.

**Создание:**
```
/rtp pit create spawn_default default
/rtp pit setpos1 spawn_default      ← встаньте в один угол (на нижний край ямы)
/rtp pit setpos2 spawn_default      ← встаньте в противоположный угол (на верхний край)
```
Зона включает все блоки между точками по X, Y и Z. Обе точки должны быть в одном мире (при смене мира вторая точка сбрасывается). Названия: латиница, цифры, `_`, `-`.

**Настройки (`config.yml → pit`):**

| Ключ | Описание |
|---|---|
| `anti-spam-millis` | Защита от повторного срабатывания (по умолчанию 1500 мс) |
| `respect-cooldown` | `true` — учитывать кулдаун режима; `false` — телепортировать всегда |
| `set-cooldown` | Ставить ли кулдаун после телепорта через яму |
| `ambient.*` | Фоновые частицы внутри ямы (интервал, количество, дальность видимости) |
| `protection.fall-grace-millis` | Иммунитет к урону от падения после телепорта — до первого приземления, но не дольше значения (по умолчанию 15000 мс); во время поиска точки и пока игрок стоит внутри ямы он действует всегда |

Платные режимы работают и в ямах: стоимость списывается так же, как через `/rtp`.

---

## ⌈ Файлы ⌋

| Файл | Содержимое |
|---|---|
| `config.yml` | Режимы, задержка, поиск, ямы, эффекты |
| `lang.yml` | Все сообщения (`&f`, `&#RRGGBB`, плейсхолдеры `{seconds}`, `{mode}`, `{time}` …) |
| `menus.yml` | Главное меню: заголовок, анимация, предметы режимов |
| `pits.yml` | Ямы (управляются командами) |

Плейсхолдеры в `menus.yml`: `{mode_name}`, `{min}`, `{max}`, `{cost}`, `{cooldown}`, `{status}`, `{world}`.

---

## ⌈ Сборка ⌋

```bash
mvn clean package
```
Готовый jar — `target/MirageRTP-v0.1.jar`. Требуется **JDK 21**. Зависимости Vault, PlayerPoints, WorldGuard и PlaceholderAPI подключаются как `provided` и в jar не шейдятся.

---

## ⌈ Структура проекта ⌋

```
org.github.sawervatoryy.mirageRTP
├── MirageRTP.java          главный класс, жизненный цикл, перезагрузка
├── command/                RtpCommand
├── config/                 Settings, Lang, MenuSettings, ConfigSnapshot
├── effect/                 Effect, ParticleSpec, SoundSpec
├── gui/                    RtpMenu, RtpMenuHolder
├── hook/                   Vault, PlayerPoints, WorldGuard
├── listener/               MoveListener, PlayerListener, MenuListener
├── manager/                TeleportManager, PitManager, CooldownManager
├── model/                  RtpMode, Pit, BlockPos, TeleportOptions, ...
├── placeholder/            RtpExpansion
├── service/                LocationFinder, EconomyService
├── task/                   PitAmbientTask
└── util/                   Text, Scheduling
```

---

## ⌈ Заметки ⌋

- Кулдауны хранятся в памяти и сбрасываются при перезапуске сервера.
- Конфиги не дополняются автоматически новыми ключами при обновлении — при апдейте сверяйтесь с дефолтными файлами.
- Если платный режим настроен, а Vault/PlayerPoints не установлен, игроки не смогут им воспользоваться, а в консоль будет выведено предупреждение.
