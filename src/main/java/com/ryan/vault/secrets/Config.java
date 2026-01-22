package com.ryan.vault.secrets;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Simple .properties loader + helpers. */
public final class Config {

    private Config() {}

    public static Properties load(String filePath) throws IOException {
        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(filePath);
            p.load(in);
            return p;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    public static String req(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return v.trim();
    }

    public static String get(Properties p, String key, String def) {
        String v = p.getProperty(key);
        return v == null ? def : v.trim();
    }

    public static int getInt(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return def;
        return Integer.parseInt(v.trim());
    }

    public static long getLong(Properties p, String key, long def) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) return def;
        return Long.parseLong(v.trim());
    }
}
