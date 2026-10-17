![Hammer Repair](https://raw.githubusercontent.com/AloneX15/hammer-repair-mod/main/docs/images/banner.jpg)

**Hammer Repair** adds a single, permanent repair hammer that mends armor by spending the **user's experience
points**. Keep a teammate's gear in shape during co-op, or fix your own pieces on the go, without an anvil and
without "Too Expensive!".

![A player repairs their teammate's damaged diamond armor with the hammer](https://raw.githubusercontent.com/AloneX15/hammer-repair-mod/main/docs/images/cooperative-repair.jpg)

## How it works

Hold **right-click**. Every **second** the hammer lands a hit that restores **up to 20 durability**, costing
**1 experience point per 2 durability**, always paid by the player holding the hammer.

| Mode | Hands | What gets repaired |
|---|---|---|
| **Teammate** | Hammer in your main hand, aiming at another player within **3 blocks** with a clear line of sight | Their equipped armor piece with the **lowest durability percentage** (ties: helmet, chestplate, leggings, boots) |
| **Yourself** | Hammer in your **off hand**, damaged armor piece in your main hand | That piece. It takes priority over equipping it; undamaged pieces equip as usual |

![First person: hammer in the off hand, damaged diamond chestplate in the main hand](https://raw.githubusercontent.com/AloneX15/hammer-repair-mod/main/docs/images/self-repair.jpg)

- Releasing the button, switching items, losing sight, walking away, dying or disconnecting stops the action, and
  **an unfinished hit is never charged**.
- Only the damage changes: enchantments, custom names and every other component are kept.
- Experience is spent as exact points (not levels), with no rounding loss on the XP bar.
- Free in Creative; disabled in Spectator.
- Visible arm swing for everyone, a soft anvil sound, sparks and happy-villager particles on every repair. Short
  action-bar messages (no XP, nothing to repair, consent, done) that never spam.
- Optional consent: with `require_target_sneaking`, your teammate has to crouch to accept the repair.

## The hammer

| | |
|---|---|
| **Item** | `hammer_repair:repair_hammer` |
| **Durability** | None: it never breaks |
| **Stack size** | 1 |
| **Rarity** | Epic |
| **Fire and lava** | Resistant, like netherite |
| **Enchantments** | Not enchantable |
| **Combat** | No extra damage or effects |
| **Creative tab** | Tools & Utilities |

## Recipe

![Default recipe in the crafting table](https://raw.githubusercontent.com/AloneX15/hammer-repair-mod/main/docs/images/recipe.jpg)

| | | |
|---|---|---|
| Netherite Block | Nether Star | Netherite Block |
| Netherite Ingot | Heavy Core | Netherite Ingot |
| | Blaze Rod | |

The recipe is fully configurable in `config/hammer_repair/recipe.json`: shaped or shapeless, items or tags from any
mod, and alternatives per slot. Apply changes with `/hammerrepair reload` (operator level 2). A datapack defining
`hammer_repair:repair_hammer` always takes priority, and `"enabled": false` turns the config recipe off.

![In-game tooltip](https://raw.githubusercontent.com/AloneX15/hammer-repair-mod/main/docs/images/tooltip.jpg)

## Configuration

`config/hammer_repair/common.json` (server side; the host decides in single player and LAN):

| Field | Default | Range | Meaning |
|---|---|---|---|
| `durability_per_xp` | 2 | 1–100 | Durability restored per experience point |
| `max_repair_per_hit` | 20 | 1–1000 | Maximum durability per hit |
| `hit_interval_ticks` | 20 | 5–200 | Ticks between hits (20 = 1 second) |
| `max_distance` | 3.0 | 1–6 | Reach of the teammate repair, in blocks |
| `require_target_sneaking` | false | | Your teammate must crouch to accept |

Invalid files are rejected with a clear message (field, row and column), and the last valid configuration stays
active. Full guide: [configuration (Spanish)](https://github.com/AloneX15/hammer-repair-mod/blob/main/docs/configuracion.md).

## Compatibility

- **Minecraft 1.21.1** (and 1.21) and **26.1.x, 26.2.x, 26.3.x**. One file per Minecraft version.
- Requires **Fabric Loader** and **Fabric API**. Needed on **both client and server**; a client without the mod gets a
  clear message when joining.
- Armor from other mods works when the game recognizes it as a helmet, chestplate, leggings or boots and it uses
  standard durability.
- Tested in CI with Lithium and FerriteCore.

## Links

- Source code and issues: [GitHub](https://github.com/AloneX15/hammer-repair-mod)
- License: MIT

Created by **TakumiStudios**.
