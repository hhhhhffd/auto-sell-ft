<div align="center">

<h1>SellHelper</h1>
<hr>

**Client-side Fabric mod that automates selling items on the FunTime auction (`/ah`)**

![Minecraft](https://img.shields.io/badge/MINECRAFT-1.21.4-62B47A?style=flat-square&logo=minecraft&logoColor=white)
![Fabric](https://img.shields.io/badge/FABRIC_LOADER-0.18.4-DBD0B4?style=flat-square)
![Java](https://img.shields.io/badge/JAVA-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Release](https://img.shields.io/github/v/release/hhhhhffd/auto-sell-ft?style=flat-square)
![License](https://img.shields.io/github/license/hhhhhffd/auto-sell-ft?style=flat-square)

[Download](https://github.com/hhhhhffd/auto-sell-ft/releases/tag/Realese) · [Build](#build) · [License](#license)

</div>

> [!NOTE]
> Archived — no longer maintained.

## What it is

SellHelper repeats the manual routine of listing items on the FunTime server auction. It finds the configured item, splits the stack to the chosen amount, runs `/ah sell <price>` until the server reports the storage is full, then waits and re-lists with `/ah resell`. It was written against the FunTime server's chat messages and GUI layout and may no longer work after server changes.

## Features

- Commands to set the item, amount and price from in-game.
- A **SellHelper Toggle** key (under Controls) with a green `SellHelper Active` indicator beside the crosshair.
- Two resell modes: direct `/ah resell`, or an emulated GUI click-through.
- Settings saved to `config/sellhelper.json`.

## Requirements

| Part | Version |
| --- | --- |
| Minecraft | 1.21.4 |
| Fabric Loader | 0.18.4+ |
| Fabric API | 0.119.4+1.21.4 |
| Java | 21+ |

## Install

Download `sellhelper-1.0.1.jar` from the [latest release](https://github.com/hhhhhffd/auto-sell-ft/releases/tag/Realese) (the first release, `v 1.0`, has `sellhelper-1.0.0.jar`) and put it into `mods/`.

## Usage

| Command | Description |
| --- | --- |
| `/sellhelper set_item` | remember the held item and its count as `amount` |
| `/sellhelper set_item <count>` | remember the held item with a given count |
| `/sellhelper set_price <price>` | set the price for `/ah sell` |
| `/sellhelper ah_resell <0\|1>` | resell mode |

Flow: item found (hand, hotbar, inventory) -> stack split to the amount -> `/ah sell <price>` repeated until the server answers that storage is full -> `/ah resell` every 63 seconds -> continue when the server reports a purchase -> notify and switch off when the items run out.

`ah_resell 1` (default) sends `/ah resell` directly. `ah_resell 0` opens `/ah`, clicks slot 46 (resell) and confirms in slot 52.

Config example (`config/sellhelper.json`, created automatically):

```json
{
  "itemId": "minecraft:shulker_box",
  "amount": 16,
  "price": 100000,
  "ahResell": 1
}
```

## Build

```bash
./gradlew build
```

The JAR is written to `build/libs/sellhelper-1.0.0.jar`.

## License

MIT, see [LICENSE](LICENSE).
