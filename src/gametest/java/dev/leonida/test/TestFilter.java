package dev.leonida.test;

/** tools/pod-test.sh <module> sets -Dleonida.test=<module>; tests skip unless they belong to it. */
public final class TestFilter {
	private TestFilter() {}

	public static boolean enabled(String module) {
		String only = System.getProperty("leonida.test");
		boolean on = only == null || only.isEmpty() || only.equals("all") || only.equals(module);
		System.out.println("[leonida-test] " + module + (on ? " running" : " skipped"));
		return on;
	}
}
