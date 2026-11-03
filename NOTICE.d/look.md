# look module: third-party material

None. Every texture under `assets/leonida/textures/block/look/` (albedo, labPBR `_n` normal/AO/height and `_s` specular/emissive maps) is generated procedurally from scratch by `src/main/java/dev/leonida/look/tools/gen_look_assets.py` (with `texlib.py` and `materials.py`), using only numpy and Pillow. No game textures, photos or third-party texture packs were used.

The files `iron_bars_template.json` and `stairs_template.json` in the same tools folder are structural model templates mirroring vanilla Minecraft block model geometry (element boxes only, no textures), used to generate our own models.
