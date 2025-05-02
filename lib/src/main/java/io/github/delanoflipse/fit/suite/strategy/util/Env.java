package io.github.delanoflipse.fit.suite.strategy.util;

public class Env {
    public static String getEnv(String key, String def) {
        String env = System.getenv(key);
        if (env == null || env.equals("")) {
            return def;
        }
        return env;
    }
}
