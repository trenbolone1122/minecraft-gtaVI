package dev.leonida.fx.look;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import dev.leonida.fx.FxConfig;
import dev.leonida.fx.FxModule;
import dev.leonida.fx.mixin.PostPassAccessor;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

/**
 * The built-in Leonida look: a time of day colour grade with haze and bloom as a post effect, plus warmer sky, fog,
 * cloud and sunset colours through extra environment attribute layers. Disabled while a shader pack is active.
 */
public final class LeonidaLook {
	public static final Identifier GRADE = Identifier.fromNamespaceAndPath("leonida", "fx/grade");
	public static final Identifier GRADE_LOW = Identifier.fromNamespaceAndPath("leonida", "fx/grade_low");
	public static final String UNIFORM_BLOCK = "LeonidaGrade";
	private static final int UBO_SIZE = 4 * 16 + 2 * 64 + 8 * 16;

	/** Current phase weights, updated every client tick. */
	private static float day = 1, golden, night, rain, underwater;
	private static boolean leonidaSky;
	private static boolean shaderPackActive;
	private static long clientTicks, lastProcessTick = -1000;
	private static int shaderPackCheck;
	private static final Map<PostPass, GpuBuffer> buffers = new WeakHashMap<>();

	private LeonidaLook() {}

	public static float day() {
		return day;
	}

	public static float golden() {
		return golden;
	}

	public static float night() {
		return night;
	}

	/** Grade, sky and fog are active (quality not OFF, no shader pack, a sky dimension). */
	public static boolean active() {
		return FxConfig.get().quality.grade && !shaderPackActive && FxConfig.get().gradeStrength > 0.0F;
	}

	/** True while an Iris/Oculus shader pack renders the world (its pack draws its own sky, water and foam). */
	public static boolean shaderPackActive() {
		return shaderPackActive;
	}

	public static void tick(Minecraft mc) {
		clientTicks++;
		if (--shaderPackCheck <= 0) {
			shaderPackCheck = 40;
			shaderPackActive = detectShaderPack();
		}
		ClientLevel level = mc.level;
		if (level == null) return;
		leonidaSky = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.dimension() != Level.END;
		float angle = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SUN_ANGLE);
		float elev = Mth.cos(angle * Mth.DEG_TO_RAD); // 1 noon, 0 horizon, -1 midnight
		float d = smooth(0.05F, 0.45F, elev);
		float n = smooth(0.02F, -0.28F, elev);
		float g = 1.0F - smooth(0.0F, 0.42F, Math.abs(elev + 0.04F));
		day = d;
		night = n;
		golden = g;
		rain = level.getRainLevel(1.0F);
		Camera cam = mc.gameRenderer.mainCamera();
		underwater = cam != null && cam.getFluidInCamera() == FogType.WATER ? 1.0F : 0.0F;
	}

	private static float smooth(float e0, float e1, float x) {
		float t = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
		return t * t * (3 - 2 * t);
	}

	private static boolean detectShaderPack() {
		FabricLoader fl = FabricLoader.getInstance();
		if (!fl.isModLoaded("iris") && !fl.isModLoaded("oculus")) return false;
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object inst = api.getMethod("getInstance").invoke(null);
			return (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
		} catch (Throwable t) {
			// unknown loader version: assume packs might be on and stay out of the way
			return true;
		}
	}

	/** Called at the end of {@code GameRenderer.update}: request our post chain right after the frame start. */
	public static void requestPostEffect(List<Identifier> requested) {
		if (!active() || Minecraft.getInstance().level == null) return;
		Identifier id = FxConfig.get().quality.bloom ? GRADE : GRADE_LOW;
		int at = requested.isEmpty() ? 0 : 1;
		requested.add(Math.min(at, requested.size()), id);
	}

	/** Called before our post chain runs: uploads the per frame uniforms into every pass that declares them. */
	public static void beforeProcess(Identifier chainId, List<PostPass> passes) {
		if (!chainId.equals(GRADE) && !chainId.equals(GRADE_LOW)) return;
		Minecraft mc = Minecraft.getInstance();
		Camera cam = mc.gameRenderer.mainCamera();
		if (cam == null) return;
		lastProcessTick = clientTicks;
		float near = 0.05F;
		float far = ((dev.leonida.fx.mixin.CameraAccessor) cam).leonida$fx$depthFar();
		boolean z01 = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		FxConfig cfg = FxConfig.get();
		float strength = cfg.gradeStrength;
		boolean bloom = cfg.quality.bloom;
		boolean ao = cfg.quality.ordinal() >= FxConfig.Quality.MEDIUM.ordinal();
		Matrix4f viewProj = cam.getViewRotationProjectionMatrix(new Matrix4f());
		Matrix4f invViewProj = new Matrix4f(viewProj).invert();
		Vec3 cp = cam.position();
		float time = (float) ((Util.getMillis() % 3_600_000L) / 1000.0);
		SkyState sky = skyState(mc);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
					.putVec4(day, golden, night, rain)
					.putVec4(strength, bloom ? 1.0F : 0.0F, leonidaSky ? 1.0F : 0.0F, underwater)
					.putVec4(near, far, z01 ? 1.0F : 0.0F, ao ? 1.0F : 0.0F)
					.putVec4(rdFogStart(), Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F, 0.0F, 0.6F + 0.3F * rain)
					.putMat4f(invViewProj)
					.putMat4f(viewProj)
					.putVec4(sky.sunDir.x, sky.sunDir.y, sky.sunDir.z, sky.sunStrength)
					.putVec4(sky.sunColor.x, sky.sunColor.y, sky.sunColor.z, sky.glow)
					.putVec4(sky.zenith.x, sky.zenith.y, sky.zenith.z, sky.gain)
					.putVec4(sky.horizon.x, sky.horizon.y, sky.horizon.z, sky.warm)
					.putVec4(sky.cloudLit.x, sky.cloudLit.y, sky.cloudLit.z, sky.cover)
					.putVec4(sky.cloudShade.x, sky.cloudShade.y, sky.cloudShade.z, bloom ? Mth.clamp(night * 0.75F + rain * 0.9F, 0.0F, 1.0F) : 0.0F)
					.putVec4(sky.cloudGlow.x, sky.cloudGlow.y, sky.cloudGlow.z, 1.0F)
					.putVec4((float) Math.floorMod((long) Math.floor(cp.x), 4096L) + (float) (cp.x - Math.floor(cp.x)), (float) cp.y,
							(float) Math.floorMod((long) Math.floor(cp.z), 4096L) + (float) (cp.z - Math.floor(cp.z)), time)
					.get();
			for (PostPass pass : passes) {
				Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).leonida$fx$customUniforms();
				GpuBuffer current = uniforms.get(UNIFORM_BLOCK);
				if (current == null) continue;
				GpuBuffer ours = buffers.get(pass);
				if (ours == null || ours.isClosed()) {
					ours = RenderSystem.getDevice().createBuffer(() -> "Leonida grade UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
					buffers.put(pass, ours);
					if (current != ours) current.close();
					uniforms.put(UNIFORM_BLOCK, ours);
				}
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(ours.slice(), data);
				data.rewind();
			}
		} catch (RuntimeException e) {
			FxModule.LOG.warn("Leonida grade uniform upload failed", e);
		}
	}

	/** Sun, sky and cloud colours for the current time, after the lighting of the leonidastate3d.com renderer. */
	public static final class SkyState {
		public final Vector3f sunDir = new Vector3f(0, 1, 0);
		public final Vector3f sunColor = new Vector3f(1);
		public final Vector3f zenith = new Vector3f();
		public final Vector3f horizon = new Vector3f();
		public final Vector3f cloudLit = new Vector3f();
		public final Vector3f cloudShade = new Vector3f();
		public final Vector3f cloudGlow = new Vector3f();
		public float sunStrength, glow, gain = 1, warm, cover;
	}

	private static Vector3f rgb(float r, float g, float b) {
		return new Vector3f(r, g, b);
	}

	public static SkyState skyState(Minecraft mc) {
		SkyState s = new SkyState();
		ClientLevel level = mc.level;
		float angle = level == null ? 0.0F : level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SUN_ANGLE);
		float rad = angle * Mth.DEG_TO_RAD;
		float elev = Mth.cos(rad);
		float elD = (float) Math.toDegrees(Math.asin(Mth.clamp(elev, -1.0F, 1.0F)));
		float dayL = smooth(-4, 8, elD);
		float low = 1.0F - smooth(2, 22, elD);
		float glow = smooth(-7, 1, elD) * (1.0F - smooth(6, 18, elD));
		float nightL = 1.0F - smooth(-12, -3, elD);
		float sunUp = smooth(-1.5F, 4, elD);
		// vanilla sun path: rises in the east (+x), sets in the west (-x)
		Vector3f sun = new Vector3f(-Mth.sin(rad), elev, 0.0F).normalize();
		if (sunUp > 0.02F) {
			s.sunDir.set(sun);
			s.sunColor.set(1.0F, 0.97F - 0.42F * low, 0.92F - 0.72F * low);
			s.sunStrength = sunUp;
		} else {
			// moonlight: dim and blue, from the opposite side
			s.sunDir.set(sun).negate();
			s.sunColor.set(0.55F, 0.65F, 1.0F);
			s.sunStrength = 0.18F * nightL;
		}
		s.glow = glow;
		float dusk = 1.0F - smooth(-2, 2, elD);
		// zenith: deep Florida blue by day, purple at dusk, navy at night
		Vector3f z = rgb(0.12F, 0.34F, 0.80F);
		z.lerp(rgb(0.27F, 0.36F, 0.66F), low);
		z.lerp(rgb(0.30F, 0.20F, 0.48F), dusk);
		z.lerp(rgb(0.012F, 0.02F, 0.055F), nightL);
		// horizon and haze: grey-blue by day, gold at golden hour, pink after sunset, dark at night
		Vector3f h = rgb(0.62F, 0.69F, 0.78F);
		h.lerp(rgb(0.95F, 0.62F, 0.38F), low * smooth(-2, 3, elD));
		h.lerp(rgb(0.80F, 0.38F, 0.48F), dusk);
		h.lerp(rgb(0.03F, 0.04F, 0.085F), nightL);
		float grey = rain * 0.85F;
		Vector3f overcast = rgb(0.46F, 0.49F, 0.53F).mul(0.15F + 0.85F * dayL);
		z.lerp(overcast, grey);
		h.lerp(new Vector3f(overcast).mul(1.15F), grey);
		s.zenith.set(z);
		s.horizon.set(h);
		s.gain = 1.0F + 0.15F * glow;
		s.warm = glow * smooth(-3, 2, elD) * (1.0F - grey);
		// clouds: sunlit white tops, blue-grey undersides; orange while the sun sets, pink afterwards
		float cloudSun = smooth(-8, 1, elD);
		Vector3f sunset = rgb(1.0F, 0.42F, 0.16F).lerp(rgb(0.92F, 0.28F, 0.42F), 1.0F - smooth(-5, 2, elD));
		Vector3f lit = new Vector3f(s.sunColor).mul(sunUp > 0.02F ? 0.85F * sunUp : 0.0F).add(rgb(0.18F, 0.19F, 0.22F).mul(dayL));
		lit.lerp(new Vector3f(sunset).mul(0.9F * cloudSun), glow).add(0.012F, 0.014F, 0.02F);
		lit.add(rgb(0.08F, 0.09F, 0.13F).mul(nightL));
		lit.min(new Vector3f(1.0F));
		Vector3f shade = rgb(0.58F, 0.63F, 0.72F).mul(0.15F + 0.85F * dayL);
		shade.lerp(rgb(0.42F, 0.30F, 0.42F).mul(0.3F + 0.7F * cloudSun), glow).mul(0.05F + 0.95F * Math.max(dayL, cloudSun));
		shade.max(rgb(0.035F, 0.04F, 0.065F));
		s.cloudLit.set(lit.lerp(new Vector3f(overcast).mul(1.25F), grey * 0.7F));
		s.cloudShade.set(shade.lerp(new Vector3f(overcast).mul(0.8F), grey * 0.7F));
		s.cloudGlow.set(sunset).mul(0.85F * glow * cloudSun * (1.0F - grey));
		boolean cloudsOn = mc.options.getCloudStatus() != net.minecraft.client.CloudStatus.OFF;
		s.cover = cloudsOn ? 0.5F + 0.45F * rain : 0.0F;
		return s;
	}

	/** Our post chain ran recently, so it draws the sky and clouds. */
	private static boolean postSkyActive() {
		return active() && leonidaSky && clientTicks - lastProcessTick < 40;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Sky, fog, cloud and sunset colours

	/** Adds our layers after the vanilla ones on the client level. */
	public static void addLayers(EnvironmentAttributeSystem.Builder b, ClientLevel level) {
		b.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (base, tick) -> sky(base));
		b.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (base, tick) -> fog(base));
		b.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (base, tick) -> sunset(base));
		b.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (base, tick) -> clouds(base));
		b.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (base, tick) -> skyLight(base));
		b.addTimeBasedLayer(EnvironmentAttributes.WATER_FOG_COLOR, (base, tick) -> waterFog(base));
		b.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE, (base, tick) -> fogDistance(base, true));
		b.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (base, tick) -> fogDistance(base, false));
	}

	/**
	 * Clear air over Leonida: atmospheric fog starts near the render distance edge (vanilla's edge fade still hides
	 * chunk borders) and thins out with camera height, so from y 150-250 the city is visible out to the full render
	 * distance. Only ever pushes fog further away; rain and boss fog offsets are applied by vanilla on top.
	 */
	/** Vanilla's render distance fog start (FogRenderer: end minus clamp(end / 10, 4, 64)). */
	private static float rdFogStart() {
		float rd = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
		return rd - Mth.clamp(rd / 10.0F, 4.0F, 64.0F);
	}

	private static float fogDistance(float base, boolean start) {
		if (!active() || !leonidaSky) return base;
		Minecraft mc = Minecraft.getInstance();
		Camera cam = mc.gameRenderer.mainCamera();
		float rd = mc.options.getEffectiveRenderDistance() * 16.0F;
		float camY = cam != null ? (float) cam.position().y : 64.0F;
		float h = smooth(90.0F, 170.0F, camY);
		float v = start ? rd * (0.72F + 0.24F * h) : rd * (1.6F + 3.0F * h);
		return Math.max(base, v);
	}

	private static float k() {
		return active() && leonidaSky ? FxConfig.get().gradeStrength * (1.0F - rain * 0.7F) : 0.0F;
	}

	private static Vector3fc mix(Vector3fc base, float r, float g, float b, float t) {
		if (t <= 0) return base;
		return new Vector3f(base).lerp(new Vector3f(r, g, b), Mth.clamp(t, 0, 1));
	}

	private static Vector3fc sky(Vector3fc base) {
		float k = k();
		if (k <= 0) return base;
		SkyState s = skyState(Minecraft.getInstance());
		return new Vector3f(base).lerp(s.zenith, k);
	}

	/** Fog matches the horizon of our sky so far terrain fades into it (grey-blue by day). */
	private static Vector3fc fog(Vector3fc base) {
		float k = k();
		if (k <= 0) return base;
		Minecraft mc = Minecraft.getInstance();
		SkyState s = skyState(mc);
		Vector3f c = new Vector3f(base).lerp(s.horizon, k);
		// from the air the render distance edge sits below the horizon, where our sky draws hazy sea: fade far
		// terrain into that colour so no pale fog rim or ghost silhouettes show around the edge
		Camera cam = mc.gameRenderer.mainCamera();
		if (postSkyActive() && cam != null) {
			float h = smooth(90.0F, 170.0F, (float) cam.position().y);
			Vector3f sea = new Vector3f(s.horizon).mul(0.55F, 0.86F, 0.95F).lerp(s.horizon, 0.25F);
			c.lerp(sea, 0.9F * h * k);
		}
		return c;
	}

	private static Vector4fc sunset(Vector4fc base) {
		float k = k();
		if (k <= 0 || base.w() <= 0.001F) return base;
		// our post sky draws the sunset glow itself
		if (postSkyActive()) return new Vector4f(base.x(), base.y(), base.z(), 0.0F);
		Vector4f c = new Vector4f(base);
		c.lerp(new Vector4f(1.0F, 0.42F, 0.30F, Math.min(1.0F, base.w() * 1.25F)), 0.65F * k);
		return c;
	}

	private static Vector4fc clouds(Vector4fc base) {
		// our post sky draws soft cumulus instead of the blocky clouds
		if (postSkyActive()) return new Vector4f(base.x(), base.y(), base.z(), 0.0F);
		float k = k();
		if (k <= 0) return base;
		Vector4f c = new Vector4f(base);
		c.lerp(new Vector4f(1.0F, 0.74F, 0.78F, base.w()), 0.5F * golden * k);
		c.lerp(new Vector4f(0.30F, 0.26F, 0.42F, base.w()), 0.35F * night * k);
		return c;
	}

	private static Vector3fc skyLight(Vector3fc base) {
		float k = k();
		if (k <= 0) return base;
		Vector3fc c = mix(base, 1.0F, 0.78F, 0.62F, 0.55F * golden * k);
		// night: neutral cool grey instead of vanilla's blue moonlight, so streets read dark and neon carries the colour
		return mix(c, 0.62F, 0.64F, 0.70F, 0.6F * night * k);
	}

	private static Vector3fc waterFog(Vector3fc base) {
		float k = k();
		if (k <= 0) return base;
		return mix(base, 0.05F, 0.42F, 0.48F, 0.6F * k);
	}
}
