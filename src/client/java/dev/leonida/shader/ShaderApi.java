package dev.leonida.shader;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Iris status through reflection, so we compile without Iris and keep working if a player removes it.
 * fx uses its own IrisApi check to switch its vanilla grade off; this adds "is it our pack".
 */
public final class ShaderApi {
	private ShaderApi() {}

	public static boolean irisLoaded() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	public static boolean shaderPackInUse() {
		if (!irisLoaded()) return false;
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object inst = api.getMethod("getInstance").invoke(null);
			return (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Name of the selected pack in Iris, or "" (Iris internal API, may change between versions). */
	public static String currentPackName() {
		if (!irisLoaded()) return "";
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Method m = iris.getMethod("getCurrentPackName");
			Object o = m.invoke(null);
			return o == null ? "" : o.toString();
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "";
		}
	}

	public static boolean isLeonidaPackActive() {
		return shaderPackInUse() && currentPackName().contains(ShaderModule.PACK_NAME);
	}
}
