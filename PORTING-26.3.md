# Minecraft 26.3 port candidate

This branch is a review candidate for porting Cobblemon 1.8.2 from the upstream base commit `7511facebc` to Minecraft 26.3. The port and its follow-up fixes are presented as one review snapshot, keeping local development journals and unrelated private artifacts out of candidate history.

## Scope

- Minecraft 26.3, Java 25, Fabric.
- Ported client and common APIs, gameplay, persistence, recipes, registries, machines, Pokédex, world generation, and rendering.
- Follow-up fixes for battle startup, riding and flight, aerial Pokémon behavior, multiplayer synchronization, Pokemon model culling, emissive cutouts, animation evaluation, and item models.
- Fabric GameTests and client GameTests covering migrated behavior.
- NeoForge and TBCS are not included in this candidate. Optional add-on integrations remain subject to the status documented by their source and build configuration.

The separate Aventura modpack and its trainer, structure, raid, and Mega Showdown content are not part of this Cobblemon port branch.

## Build and verification

Use JDK 25 and the wrapper from the repository root:

```shell
./gradlew :fabric:compileKotlin :fabric:validateAccessWidener --console=plain
./gradlew :common:test :fabric:runGameTest :fabric:runClientGameTest --console=plain
./gradlew :fabric:releaseJar --console=plain
```

The project recorded successful integrated Fabric builds and GameTest runs during development. Those logs came from the local development environment and are not a substitute for a fresh run on this review branch. In particular, a clean external launcher installation and multiplayer with two authenticated clients still need independent validation.

## Optional ambient audio

The public candidate keeps the original Cobblemon audio and sound mappings. Players may make their own optional resource pack from audio they have permission to use; see [the ambient audio pack guide](docs/custom-pokemon-ambience.md). No user-supplied audio clips are included.
