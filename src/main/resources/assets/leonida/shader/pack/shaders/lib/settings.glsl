// Leonida Shaders: user options (shown in the Iris shader settings screen).
// Original code written for the Leonida mod, GPL-3.0-or-later.

#define SHADOWS // Sun and moon shadows
#define SHADOW_SAMPLES 8 // Soft shadow taps [4 8 12 16 24]
#define SHADOW_SOFTNESS 1.0 // Penumbra size [0.5 0.75 1.0 1.5 2.0]
#define COLORED_SHADOWS // Stained glass tints sunlight

#define SSR // Screen space reflections on water, glass, wet roads
#define SSR_STEPS 16 // Reflection ray steps [10 12 16 20 28 40]
#define WET_NIGHTS // Damp asphalt at night that mirrors neon (GTA look)
#define WETNESS_AMOUNT 1.0 // [0.0 0.5 0.75 1.0 1.25 1.5]

#define CLOUDS // Fair weather cumulus
#define CLOUD_OCTAVES 3 // [3 4 5 6]
#define CLOUD_COVER 0.45 // [0.25 0.35 0.45 0.55 0.65]

#define HAZE_DENSITY 1.0 // Grey-blue distance haze [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define DUSK_TINT 1.0 // Pink and purple dusk strength [0.0 0.5 1.0 1.5 2.0]

#define BLOOM // Glow around neon, lamps, the sun
#define BLOOM_STRENGTH 1.0 // [0.0 0.25 0.5 0.75 1.0 1.25 1.5 2.0]

#define RIM_LIGHT // Sun rim light on characters
#define RIM_STRENGTH 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]

#define WAVING_PLANTS // Leaves, grass and palms move in the breeze
#define WATER_WAVES 1.0 // Wave height [0.0 0.5 1.0 1.5 2.0]
#define WATER_FOAM // Shore foam and whitecaps

#define EXPOSURE 1.0 // [0.5 0.6 0.7 0.8 0.9 1.0 1.1 1.2 1.4 1.6]
#define SATURATION 1.15 // [0.8 0.9 1.0 1.04 1.08 1.12 1.15 1.2 1.3]
#define VIGNETTE 0.25 // [0.0 0.15 0.25 0.35 0.5]
#define SUN_SIZE 1.0 // Sun disc size [0.5 1.0 1.5 2.0 3.0]

// Sun path for Leonida (Florida, about 26 deg N): rises east at 06:00, about 60 deg high in the south at noon, sets
// west at 18:00. Iris reads this constant.
const float sunPathRotation = -30.0; // [-40.0 -30.0 -20.0 0.0 20.0 30.0]

// shadow map settings (Iris reads these constants)
const int shadowMapResolution = 1536; // [1024 1536 2048 3072 4096]
const float shadowDistance = 80.0; // [48.0 64.0 80.0 96.0 128.0 192.0 256.0]
const float shadowDistanceRenderMul = 1.0;
const float shadowIntervalSize = 2.0;
const bool shadowHardwareFiltering = true;
const float ambientOcclusionLevel = 1.0;
