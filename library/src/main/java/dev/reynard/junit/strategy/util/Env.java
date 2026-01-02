package dev.reynard.junit.strategy.util;

public class Env {

    public enum Keys {
        // Tag for the image
        OUTPUT_TAG("default"),
        OUTPUT_DIR(""),
        USE_SER("true"),
        CONTROLLER_IMAGE("dflipse/reynard-controller:latest"),
        PROXY_IMAGE("dflipse/reynard-proxy:latest"),
        LOCAL_HOST("localhost"),
        LOG_LEVEL("info");

        private final String defaultValue;

        Keys(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }

    public static String getEnv(Keys key) {
        return getEnv(key.name(), key.getDefaultValue());
    }

    public static String getEnv(String key, String def) {
        String env = System.getenv(key);
        if (env == null || env.equals("")) {
            return def;
        }
        return env;
    }

    private static boolean isTruthy(String value) {
        return value != null && !value.isEmpty() && !value.equalsIgnoreCase("false");
    }

    public static boolean getEnvBool(Keys key) {
        String value = getEnv(key);
        return isTruthy(value);
    }

    public static boolean getEnvBool(String key, boolean def) {
        String value = getEnv(key, def ? "true" : "false");
        return isTruthy(value);
    }
}
