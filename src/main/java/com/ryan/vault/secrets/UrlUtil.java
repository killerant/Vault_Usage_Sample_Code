package com.ryan.vault.secrets;

import java.net.URLEncoder;

public final class UrlUtil {
    private UrlUtil() {}

    public static String trimTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Encode each path segment but keep slashes. */
    public static String encodePath(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(urlEncode(parts[i]));
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
