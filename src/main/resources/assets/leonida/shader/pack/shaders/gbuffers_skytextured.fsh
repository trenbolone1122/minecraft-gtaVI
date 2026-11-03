#version 330 compatibility
// Leonida Shaders: keeps the vanilla moon (with phases); our own sun disc is drawn in deferred.fsh.
uniform sampler2D gtexture;
uniform int renderStage;
in vec2 texcoord;
in vec4 glcolor;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
	if (renderStage != MC_RENDER_STAGE_MOON) discard;
	vec4 c = texture(gtexture, texcoord) * glcolor;
	outColor = vec4(c.rgb * c.a * 1.6, 1.0);
}
