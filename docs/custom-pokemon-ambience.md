# Personal Pokémon ambient sound packs

The port retains Cobblemon's original sound assets and `cry` events. You can optionally replace the map ambience for species whose clips you have permission to use. Nothing is bundled or downloaded by the generator.

## Build a pack

1. Put your `.ogg` files in one folder. Name each file with the exact Cobblemon Showdown ID used by the sound event, for example `pikachu.ogg` or `ponytagalar.ogg`.
2. Run Python 3 from the repository root:

   ```powershell
   py utilityscripts/build-pokemon-ambient-pack.py C:\path\to\my-ogg-files C:\path\to\pokemon-ambient-overrides.zip
   ```

3. Move the generated ZIP to the Minecraft instance's `resourcepacks` folder, enable it in **Options → Resource Packs**, and place it above packs that also override Cobblemon sounds.

The generator packages only the supplied clips, replaces only the corresponding `pokemon.<showdown-id>.ambient` event, and leaves battle cries untouched. It validates the filename and Ogg container header. If the output ZIP exists, it stops unless you pass `--force`. It does not download, transcode, or determine the licensing of audio files.

For multiplayer, each player needs the same pack enabled to hear the custom clips. A server owner may distribute that ZIP as the server resource pack; only upload audio the server owner has permission to redistribute. The Pokémon's ambient playback remains driven by the game, so the pack does not turn a clip into continuous background music.

The generated metadata targets the resource-pack format used by the 26.3 port. If Minecraft changes that format, pass `--pack-format` and `--pack-format-minor` to the script.
