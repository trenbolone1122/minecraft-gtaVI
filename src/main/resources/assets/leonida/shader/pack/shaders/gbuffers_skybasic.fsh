#version 330 compatibility
// Leonida Shaders: the vanilla sky gradient is replaced in deferred.fsh; only stars are kept (added there).
uniform int renderStage;
in vec4 glcolor;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
	if (renderStage != MC_RENDER_STAGE_STARS) discard;
	outColor = vec4(glcolor.rgb * glcolor.a, 1.0);
}
