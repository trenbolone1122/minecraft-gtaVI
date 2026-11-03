#version 330 compatibility
// Leonida Shaders: shadow colour for stained glass (tinted sunlight), white for opaque blocks.
uniform sampler2D gtexture;
in vec2 texcoord;
in vec4 glcolor;
flat in int blockId;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
	vec4 c = texture(gtexture, texcoord) * glcolor;
	if (c.a < 0.1) discard;
	outColor = c;
}
