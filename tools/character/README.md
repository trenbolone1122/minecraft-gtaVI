# Character skin generator

Python 3 + Pillow scripts that produce every texture of the `character` module. All art is original,
drawn pixel by pixel in code; no external images are copied.

- `skingen.py OUT` writes Jason and Lucia outfits, the pedestrian, police and SWAT library and the npc
  skins under `OUT/assets/leonida/textures/entity/character/`, plus `OUT/skin_index.json`.
- `extras.py OUT` writes the 3D accessory textures (ponytail, cap brim) and the wardrobe block textures.
- `preview.py SHEET.png SKIN.png...` renders a flat front/back contact sheet for review.

Regenerate and install from the repo root:

```
cd tools/character
python3 skingen.py /tmp/charout && python3 extras.py /tmp/charout
cp -r /tmp/charout/assets/leonida/textures/* ../../src/main/resources/assets/leonida/textures/
```

Output is deterministic (seeded), so unchanged skins produce identical files. Run `python3 hd.py /tmp/charout` after skingen.py for the 128x128 Jason and Lucia set in textures/entity/character/hd/.
