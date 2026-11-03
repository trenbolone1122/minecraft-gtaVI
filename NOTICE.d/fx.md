# fx module

- Physics (block debris, glass shards, ragdolls) is our own lightweight pure-Java solver in
  `dev.leonida.fx.physics`; no third-party physics library or native code is bundled, so it runs
  the same on Windows, macOS and Linux.
- Physics Mod (net.diebuddies, closed source) was decompiled only to study its approach
  (client-side simulation, debris textured with block particle sprites, per-part humanoid ragdolls).
  No code or assets were copied from it.
- Post-processing shaders under `assets/leonida/shaders/post/fx` and `post_effect/fx` are original;
  they reuse vanilla Minecraft's `core/screenquad` vertex shader and `post/blit` by reference only.
- Optional integration: if Iris or Oculus is installed and a shader pack is active, the Leonida grade
  turns itself off (detected via reflection; no Iris code is bundled).
