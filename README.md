<div align="center">
  <img src="common/src/main/resources/assets/cobblemon/icon_cobblemon.png" alt="Cobblemon icon" width="112">

  # Cobblemon · Minecraft 26.3

  **An experimental Fabric port for a newer world.**

  Catch, train, battle, and explore with Cobblemon on Minecraft 26.3.

  [Getting started](#getting-started) · [What's in the port](#whats-in-the-port) · [Build from source](#build-from-source) · [Project status](#project-status) · [Português](docs/README-pt-br.md)
</div>

---

> **Community port · Work in progress**
>
> This is an unofficial adaptation based on the Cobblemon Team's work. It is not an official Cobblemon release, and the port is not endorsed by the team. Back up worlds before trying it.

| Minecraft | Mod loader | Java | Status |
| :--- | :--- | :--- | :--- |
| **26.3** | **Fabric 0.19.5+** | **25** | Experimental |

## A familiar adventure, on 26.3

Cobblemon brings Pokémon into Minecraft's world: discover them in the wild, build a team, battle, evolve, and make a home for them. This project carries that experience to Minecraft 26.3, including the systems and visual changes needed for the newer game version.

This repository contains the **Cobblemon port**. Addons and the Aventura modpack are developed separately and are not bundled here.

## Getting started

Use an installation dedicated to this port. For multiplayer, every player and the server should use the same port build.

1. Install **Minecraft Java 26.3** and **Fabric Loader 0.19.5 or newer** for 26.3 in your launcher.
2. Use **Java 25** for the instance and add a compatible **Fabric API** for 26.3. The build below was validated with Fabric API `0.161.0+26.3`.
3. Put the installable Cobblemon Fabric JAR in the instance's `mods` folder. If there is no release available yet, [build it from source](#build-from-source).
4. Start the game and create a test world. For a dedicated server, install matching Fabric, Fabric API, and Cobblemon versions on the server as well.

The `releaseJar` build includes the runtime libraries it packages, including Fabric Language Kotlin. JEI, Mod Menu and other addons are optional; compatibility varies by version.

## What's in the port

| Area | What to expect |
| :--- | :--- |
| **The 26.3 migration** | Updated rendering, interfaces, networking, world generation, recipes, machines and persistence. |
| **Exploration** | Adjusted riding and flight controls, aerial spawning and movement, and an option to start a wild battle by throwing your Pokémon. |
| **Visual fixes** | Pokémon face culling, translucent models, emissive layers and item models adapted to the new renderer. |
| **Performance work** | Shared rendering geometry, leaner animation evaluation, aerial spawn checks and berry rendering. Results depend on the scene and hardware. |

Some gameplay changes go beyond the version migration and may differ from upstream Cobblemon. They are included so players and maintainers can review the complete port and its design choices.

## Project status

The tested Fabric revision passed common tests, **34 server GameTests**, the client GameTest suite, access widener validation and a release build. The built JAR also initialized a separate Fabric client and started a dedicated server outside Gradle. The user confirmed the optional ambient-sound flow in game. These checks cover selected scenarios and do not establish compatibility with every addon, graphics device or multiplayer setup.

**Still to check:** two authenticated players together, wider addon compatibility and the remaining third-party distribution notices. NeoForge and TBCS are outside the current port scope. See the [review brief](docs/26.3-review-brief.md) and [validation guide](docs/26.3-validation.md) for details.

## Build from source

Install JDK 25 and Git, then run from the repository root:

```bash
./gradlew :fabric:releaseJar
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The installable JAR is written to `fabric/build/libs/` with the `Cobblemon-fabric-` prefix and **without** the `dev-shadow` classifier. Use the build from the exact revision you intend to test. To launch a development client, run `./gradlew :fabric:runClient`.

<details>
<summary><strong>Run the port's automated checks</strong></summary>

```bash
./gradlew :common:test :fabric:runGameTest :fabric:runClientGameTest :fabric:validateAccessWidener
```

Client GameTests open a Minecraft window and use disposable test worlds. Individual tests cannot replace an external launcher or multiplayer check. More details are in the [validation guide](docs/26.3-validation.md).

</details>

## Make it your own

The port keeps Cobblemon's original Pokémon sound mappings. If you have audio you are permitted to use, the optional [ambient sound pack guide](docs/custom-pokemon-ambience.md) explains how to add it for Pokémon in the world. It does not include or download audio files.

## Contributing & credits

Bug reports are most useful with the Minecraft, Fabric and port versions, steps to reproduce, and the relevant log or crash report. For visual issues, include a screenshot when possible. Avoid including account details or private files.

The original project was created by the [Cobblemon Team and its contributors](https://gitlab.com/cable-mc/cobblemon). This port is based on upstream commit `7511facebce13cc86706d49ae7ebab515e862fc8`; the upstream project's work and asset notices remain in the repository. See the [provenance review](docs/26.3-provenance-review.md) and [third-party component inventory](docs/26.3-third-party-inventory.md) before redistributing builds.

Source code is covered by the repository's [MPL 2.0 license](LICENSE). Pokémon models and other assets can carry separate terms; consult their individual license files and the [Cobblemon Assets license](https://gitlab.com/cable-mc/cobblemon-assets/-/blob/master/LICENSE). Third-party libraries retain their own licenses.
