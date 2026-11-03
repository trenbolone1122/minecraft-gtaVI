# Leonida

A fan-made Fabric mod that brings our own take on GTA VI to Minecraft Java Edition: the state of Leonida
at 1:1 scale (Vice City, Vice Beach, the Keys, Grassrivers, Port Gellhorn), Jason and Lucia, a wanted
system with VCPD and Leonard County Sheriff, lore-inspired vehicles and aircraft, weapons, a GTA style HUD
and phone, interiors, landmarks, and a bundled shader pack for the trailer look.

This is an unofficial fan project. It is not affiliated with or endorsed by Rockstar Games or Take-Two
Interactive. It contains no Rockstar game files, models, textures or audio. All names of real products in
the game world are parody brands.

## Download

Get the latest release from the Releases page of this repository. Two ways to install:

- Easiest, any OS: download `Leonida-<version>.mrpack` and import it in Prism Launcher, the Modrinth App or
  ATLauncher. The launcher installs Minecraft 26.3, Fabric Loader and the right Java by itself.
- Official Minecraft launcher: download `leonida-<version>.jar` (not the `-sources` jar) and follow Install
  below.

## Requirements

- Minecraft: Java Edition, version 26.3 (the official launcher ships the Java 25 runtime it needs).
- Fabric Loader 0.20.1 or newer for 26.3.
- A GPU and driver with OpenGL 3.3 core for shaders. Without OpenGL the game still runs on Vulkan, with
  shaders off (see Graphics below).
- At least 4 GB of RAM for the game (6 GB or more if you raise render distance a lot).
- Fabric API, Iris and Sodium are not separate downloads. They are bundled inside the Leonida jar.

## Install

1. Install and run Minecraft: Java Edition 26.3 once from the official launcher.
2. Run the Fabric installer (https://fabricmc.net/use/installer/), choose Client, Minecraft 26.3 and the
   latest loader, and install. This adds a "fabric-loader-26.3" profile to the launcher.
3. Download the Leonida jar from Releases.
4. Put it in your `mods` folder:
   - Windows: `%appdata%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
5. If you already had separate Fabric API, Iris or Sodium jars in `mods`, you can remove them; the bundled
   versions are Fabric API 0.165.0+26.3, Iris 1.11.7 and Sodium 0.9.3 beta 1.
6. Optional: in the launcher, edit the Fabric profile, More Options, JVM arguments, and set `-Xmx6G` (or
   at least `-Xmx4G`).
7. Start the Fabric profile.

## First launch

- On first start the Leonida shader pack is copied to `.minecraft/shaderpacks/Leonida Shaders` and selected
  in Iris, but only if you had no other pack selected. Change or turn off shaders in Options, Video
  Settings, Shader Packs.
- Create New World, World tab, World Type: "Leonida". Turn on Allow Commands if you want to teleport around.
  You spawn on the sand at Ocean Drive in Vice Beach. The world is the map at 1 block = 1 m, so it is big.
- The first chunks around you take a moment to generate; flying fast into new areas can show empty
  chunks for a second or two.

## Controls

All keys can be changed in Options, Controls, Key Binds (Leonida categories).

| Key | Action |
|---|---|
| M | open the map |
| Up arrow | open the phone |
| Z | switch character (Jason, Lucia) |
| R | reload |
| Tab (hold) | weapon wheel |
| H | horn |
| L | headlights |
| J | siren and lights (police vehicles) |
| G | aircraft smoke or searchlight |
| F7 | Leonida Settings |

## Leonida Settings

Open with F7, or Options, "Leonida..." (top left). Left click raises a value, right click lowers it.
Changes apply right away and are saved to `config/leonida.json`.

| Setting | What it changes |
|---|---|
| Performance preset | Low, Medium or High; sets all of the settings below in one go |
| Traffic density | number of AI cars around you (50% is the default) |
| Pedestrian density | number of pedestrians around you (50% is the default) |
| Max police units | cap on officers plus cruisers while you are wanted |
| Effects quality | budget for debris, ragdolls and glass (Off, Low, Medium, High) |
| Mining debris | debris when you break blocks |
| Minimap, update rate | radar on or off, and how often it redraws (5 to 60 per second) |
| Shader profile | Low, Medium or High quality in the Leonida shader pack (reloads Iris) |
| Distant Horizons, LOD distance | far terrain on or off (off by default) and how far it reaches |
| Faster building generation | experimental cache for city chunks; off by default because it can misplace downtown buildings |

"Shader pack settings..." opens the Iris options for the pack. Traffic, pedestrian and police settings
apply in single player; on a dedicated server they keep the server defaults.

## Useful places and commands

With Allow Commands on (spectator mode is easiest: `/gamemode spectator`):

| Place | Command |
|---|---|
| Downtown Vice City skyline | `/tp @s -480 220 750 180 10` |
| Sahara Arena | `/tp @s -303 150 -430` |
| Fleeca Field | `/tp @s -1606 150 -1038` |
| Jason's house (the Keys) | `/tp @s -2354 120 5532` |

Mod commands (type the command and press Tab to see its options): `/vehicle` (spawn cars, traffic on or
off, density, police), `/leonida_aircraft` (spawn and fly aircraft, parachute, police helicopter),
`/gta` (wanted level, money, crimes, pedestrians, police), `/leonida character` (switch character and
outfit), `/leonida_landmark`, `/leonida_vc` and `/leonida_build` (list, view and place landmarks and
buildings).

## Graphics: OpenGL, Vulkan, Iris and Sodium

Minecraft 26.3 has two renderers. It tries OpenGL first and, if the OpenGL window or context fails, it
silently falls back to Vulkan. The renderer choice (Default, OpenGL or Vulkan) is saved as
`preferredGraphicsBackend` in `options.txt`.

- Iris (shaders) only works on OpenGL. On Vulkan the game runs with shaders off.
- Sodium is the performance renderer that Iris builds on. Both are bundled.
- Iris decides at startup, before the game window exists, whether to hook OpenGL. If OpenGL then fails and
  the game drops to Vulkan, stock Iris crashes the game. Leonida prevents that with a startup guard:
  1. Before the game starts, it opens a hidden OpenGL test window in a short separate Java process, using
     the same request Minecraft makes (OpenGL 3.3 core, sRGB framebuffer).
  2. If that fails, it sets the backend to Vulkan for this launch, so the game starts with shaders off
     instead of crashing, and remembers that it did so.
  3. On the next launch it tests again and puts your own backend setting back once OpenGL works.
  4. If OpenGL works and your backend is on Default, it pins OpenGL, because Iris needs it.
- The guard keeps its state in `config/leonida-shader-gl.properties`. Set `glGuard=false` there to switch
  it off completely (then you are responsible for the backend setting).
- Shader pack install and selection are controlled by `config/leonida-shader.properties`
  (`autoInstall=false` stops copying the pack, `autoSelect=false` stops selecting it).
- If the game started without shaders and you expected them, look in the launcher log (or `logs/latest.log`)
  for the line `[Leonida Shaders] OpenGL guard:`. It says whether the OpenGL test passed and, if not, the
  driver's own error.

## GPU and driver notes

- All GPUs: install the latest driver from the GPU vendor (NVIDIA, AMD or Intel), not the one Windows
  Update picked. Old drivers are the most common reason OpenGL fails and the game falls back to Vulkan.
- Laptops with two GPUs (NVIDIA Optimus, AMD switchable): make Java use the dedicated GPU. Windows:
  Settings, System, Display, Graphics, add `javaw.exe` from the launcher's runtime folder and pick High
  performance (or set it in the NVIDIA Control Panel or AMD Software). Otherwise the game can start on the
  integrated GPU and run very slowly with shaders.
- NVIDIA: works with shaders on current drivers. This is the GPU family the mod was tested on (RTX 4000
  Ada).
- AMD and Intel: use current drivers. On integrated graphics, turn shaders off (Video Settings, Shader
  Packs, OFF) and lower render distance; Sodium alone still helps a lot.
- Linux: use current Mesa (AMD, Intel) or the NVIDIA proprietary driver. If OpenGL fails and the game ends
  up on Vulkan, try launching with the environment variable `SDL_VIDEO_FORCE_EGL=1` (on X servers without
  working GLX this made OpenGL start in our tests). On Wayland, `SDL_VIDEODRIVER=x11` is worth a try if the
  window or OpenGL context fails.
- Headless or virtual displays (CI, remote machines): plain Xvfb has no GPU OpenGL, so the game falls back
  to Vulkan or software rendering. Our in-game tests run under Xvfb with VirtualGL (`vglrun -d egl`) to get
  NVIDIA OpenGL. Some NVIDIA EGL setups crash with an X error while the game exits; that happens after the
  game has closed and is harmless.
- macOS: untested. Apple supports OpenGL only up to 4.1, which is enough for Minecraft, but shader
  performance on Apple GPUs is unknown.
- Dedicated servers: the shader pack, Iris and Sodium are client only and are not loaded on a server.
- OptiFine is not supported. Use the bundled Iris instead.

## Performance tips

- On a low-end or 4 GB GPU, open Leonida Settings (F7) and pick the Low preset first.
- Pre-generate the area before you drive or fly fast: `/leonida pregen 16` (radius in chunks), or the
  "Pre-generate around me" row in Leonida Settings. It runs in the background with progress in the action bar;
  `/leonida pregen stop` cancels it. Otherwise you can outrun chunk loading and stand on blank sky.
- With shaders on a 4 GB GPU, use render distance 12 to 16.
- Distant Horizons is off by default. While it is off, its background LOD generation is off too, so it does not
  compete with normal chunk loading; the Distant Horizons switch in Leonida Settings turns both on together.
- Start with render distance 12 to 16 with shaders on, and raise it if your GPU keeps up. Downtown towers
  are up to about 250 blocks tall, so the skyline needs a decent render distance to show.
- If frame rate is low, turn shaders off first, then lower render distance, then simulation distance.
- Give the game more memory (`-Xmx6G`) if it stutters while you fly over the city.

## Known issues (current release)

- No missions or story yet.
- Downtown and the skyline have had little in-game checking so far.
- New gunfire and shell-casing sounds have not been listened to in game yet.
- Fleeca Field has not been checked in game yet.

## Record the trailer on Windows

Double-click `record-trailer.bat` in the repo folder (or run `tools\record-trailer.ps1`). It needs `java` (JDK 25;
`JAVA_HOME` is used if set), `ffmpeg`, `ffprobe` and `yt-dlp` on PATH, and stops with one clear line if one is
missing. Nothing else is downloaded. It renders the 19 shots of the 60 s trailer (the `trailer60` timeline in
`src\gametest\java\dev\leonida\test\fx\cine\shots.json`) with shaders on, Distant Horizons off and the building
cache off, then builds `trailer-out\leonida-trailer-60s.mp4`.

- Timeline: quiet intro (Keys islands at sunrise, Keys bridge at noon, Jason's house), the drop at 12.5 s (Jason
  walking toward the camera, Brian's Marina, Starlet Motel, downtown, the Shamal past the towers, Sahara HQ,
  Palaestra Tower, Ocean Drive, Lucia, Lucia getting into her car), a breakdown at 40.5 s (Beach Patrol HQ,
  lifeguard tower at sunset) and a night finale from 46 s (Skyviews wheel, Sahara Arena, Fleeca Field, then Jason
  and Lucia together under the Vice Beach neon). Hard cuts, a 1 s fade in from black, a fade to black over 58.5-60 s.
- Capture is frame by frame into an offscreen 1920x1080 target, so every clip is 1920x1080 at 60 fps however slow
  the GPU is. A slow GPU only takes longer. The game window can look frozen while it renders; leave it alone.
- Each shot renders its slot plus 0.5 s; the trailer takes the slot from 0.25 s in.
- Music: "Contact" by Karl Casey @ White Bat Audio (https://www.youtube.com/watch?v=HJcZhkNOsbI), fetched as audio with
  yt-dlp and cut to 60 s from 14.1 s so the drop lands at 12.5 s, with a 1.0 s fade in and a 2.5 s fade out.
- SFX from the mod's own sounds at about -6 dB under the music: `vehicle/door.ogg` and `vehicle/start.ogg` when
  Lucia opens the car door, `aircraft/woosh.ogg` centred on the Shamal pass, with the music ducked about 4 dB under it.
- Options (pass them to the .bat or the script):
  - `-RenderDistance 16` lowers the load on 4 GB cards like the GTX 1650 (default 32).
  - `-Shots 4,12,19` renders only some shots (timeline numbers 1-19 or shot names). Add `-NoAssemble` to skip the trailer.
  - `-SkipRender` only re-assembles from clips already in `trailer-out\` (missing clips show as black).
  - `-Music C:\path\cut.wav` uses your own 60 s cut, placed from 0 s as given, instead of the download.
    `-MusicUrl` and `-MusicStart` change the download and its start point.
  - `-Bpm 128` snaps the cuts to the nearest beat, with a beat on `-DropAt` (default 12.5 s).
  - `-OutDir D:\clips` writes the output somewhere else.
- CineCapture reads these overrides as system properties `leonida.cine.<key>` or env vars `LEONIDA_CINE_<KEY>`:
  `run` (comma list of shot names), `out` (output folder), `ffmpeg` (path to ffmpeg.exe), `rd`, `dh`, `cache`,
  `shots` (path to a shot list), `width`, `height`, `frames` (cap per shot).
- New camera features in shots.json: mode `3p-front` (the character walks the key path toward a leading camera,
  face visible), `actors` (extra Jason/Lucia figures walking beside or on their own path, optionally boarding a
  prop vehicle, which swings its door) and `props` (parked vehicles, site-relative or absolute).

## Build (developers)

Requirements: JDK 25. Everything else comes from the Gradle wrapper.

```
./gradlew build                                   # jar in build/libs/
./gradlew runClient                               # dev client
./gradlew runClientGameTest -PleonidaTest=<module> # in-game tests for one module, with screenshots
```

## Layout

One Fabric mod, one source tree, one package per feature under `dev.leonida`:

| Module | What it does |
|---|---|
| core | shared state and APIs (player state, wanted level, map queries, generator contracts) |
| world | Leonida dimension, terrain, water, vegetation, map queries |
| road | streets, highways, bridges, markings, street furniture |
| build | generic buildings with facades and interiors |
| landmark, vicecity | hand-built landmarks |
| look | GTA VI material blocks and textures |
| character | Jason, Lucia, pedestrians, police skins, wardrobe |
| gameplay | crimes, wanted system, police, robberies, pedestrians |
| weapon | guns, melee, explosives, weapon wheel |
| vehicle, aircraft | cars, bikes, boats, planes, helicopters |
| hud | radar, wanted stars, money, ammo, phone, map |
| fx | colour grade, debris, glass, ragdolls |
| shader | the Leonida shader pack and its installer |

Each module has its own mixin config (`leonida.<module>.mixins.json`), language part
(`assets/leonida/lang/parts/<module>.json`), client game test, and credits file in `NOTICE.d/`.

## GitHub Actions

- Build: every push and pull request builds the jar and uploads it as an artifact.
- Release: pushing a tag like `v0.1.0` builds and publishes a GitHub release with the jar.
- In-game tests: run manually from the Actions tab; starts a real Minecraft client on a virtual screen
  for the chosen modules and uploads the screenshots.

## Credits and licenses

Code is GPL-3.0-or-later (see LICENSE). Map data is derived from the GTADB.org community map (YANIS v17,
CC BY 4.0). Third-party code, assets, fonts and bundled mods are credited with their licenses in `NOTICE.d/`.

Trailer music: "Contact" by Karl Casey @ White Bat Audio
(https://www.youtube.com/watch?v=HJcZhkNOsbI).
