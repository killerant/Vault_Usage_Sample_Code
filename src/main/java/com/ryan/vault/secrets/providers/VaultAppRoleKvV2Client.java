package com.ryan.vault.secrets.providers;

import com.ryan.vault.secrets.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryan.vault.secrets.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Vault/OpenBao KV v2 reader that authenticates via AppRole (role_id + secret_id).
 *
 * Auth flow:
 *  1) POST /v1/auth/approle/login with JSON {role_id, secret_id}
 *  2) Extract token from auth.client_token
 *  3) GET KV v2 secret using X-Vault-Token header
 *
 * Includes an in-memory cache for secret values.
 */
public class VaultAppRoleKvV2Client implements SecretsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String addr;
    private final String mount;
    private final String approleLoginPath;
    private final String roleId;
    private final String secretId;
    private final int timeoutMs;

    // Cache configuration
    private final long cacheTtlMillis;
    private final LruExpiringCache cache;

    private volatile String clientToken; // cached token

    public VaultAppRoleKvV2Client(Properties p) {
        this.addr = UrlUtil.trimTrailingSlash(Config.req(p, "vault.addr"));
        this.mount = Config.get(p, "vault.mount", "secret");
        this.approleLoginPath = Config.get(p, "vault.approle.loginPath", "/v1/auth/approle/login");
        this.roleId = Config.req(p, "vault.approle.role_id");
        this.secretId = Config.req(p, "vault.approle.secret_id");
        this.timeoutMs = Config.getInt(p, "vault.timeoutMs", 5000);

        // Cache: default 300 seconds, max 200 entries
        long ttlSeconds = Config.getLong(p, "secrets.cache.ttlSeconds", 300L);
        int maxEntries = Config.getInt(p, "secrets.cache.maxEntries", 200);
        this.cacheTtlMillis = Math.max(0L, ttlSeconds) * 1000L;
        this.cache = new LruExpiringCache(maxEntries);
    }

    @Override
    public EnumSet<SecretCapability> capabilities() {
        return EnumSet.of(SecretCapability.KV_READ);
    }

    @Override
    public String getRequired(String path, String key) throws SecretException {
        // 1) Cache check
        if (cacheTtlMillis > 0L) {
            String cached = cache.get(cacheKey(path, key));
            if (cached != null) return cached;
        }

        // 2) Read from Vault
        String value;
        try {
            ensureToken();
            try {
                value = readKv2Secret(path, key, clientToken);
            } catch (SecretException se) {
                // One retry if token became invalid/expired.
                if (se.getMessage() != null && se.getMessage().contains("HTTP 403")) {
                    clientToken = null;
                    ensureToken();
                    value = readKv2Secret(path, key, clientToken);
                } else {
                    throw se;
                }
            }
        } catch (SecretException e) {
            throw e;
        } catch (Exception e) {
            throw new SecretException("Failed to read secret from Vault", e);
        }

        // 3) Store in cache
        if (cacheTtlMillis > 0L) {
            cache.put(cacheKey(path, key), value, System.currentTimeMillis() + cacheTtlMillis);
        }

        return value;
    }

    private String cacheKey(String path, String key) {
        return path + "|" + key;
    }

    private void ensureToken() throws Exception {
        if (clientToken != null && !clientToken.isEmpty()) return;
        synchronized (this) {
            if (clientToken != null && !clientToken.isEmpty()) return;
            clientToken = loginAppRole();
        }
    }

    private String loginAppRole() throws Exception {
        String url = addr + (approleLoginPath.startsWith("/") ? approleLoginPath : ("/" + approleLoginPath));
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        String payload = "{\"role_id\":\"" + escapeJson(roleId) + "\",\"secret_id\":\"" + escapeJson(secretId) + "\"}";
        conn.getOutputStream().write(payload.getBytes("UTF-8"));

        int code = conn.getResponseCode();
        String body = HttpUtil.readResponseBody(conn);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new SecretException("Vault AppRole login failed HTTP " + code + ": " + body);
        }

        JsonNode root = MAPPER.readTree(body);
        String token = root.path("auth").path("client_token").asText(null);
        if (token == null || token.isEmpty()) {
            throw new SecretException("Vault AppRole login response missing auth.client_token");
        }
        return token;
    }

    private String readKv2Secret(String path, String key, String token) throws Exception {
        // KV v2 read: /v1/<mount>/data/<path>
        String endpoint = "/v1/" + mount + "/data/" + UrlUtil.encodePath(path);
        String url = addr + endpoint;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setRequestProperty("X-Vault-Token", token);

        int code = conn.getResponseCode();
        String body = HttpUtil.readResponseBody(conn);
        conn.disconnect();

        if (code == 404) {
            throw new SecretNotFoundException("Secret path not found: " + path);
        }
        if (code < 200 || code >= 300) {
            throw new SecretException("Vault read failed HTTP " + code + ": " + body);
        }

        JsonNode root = MAPPER.readTree(body);
        JsonNode valueNode = root.path("data").path("data").path(key);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            throw new SecretNotFoundException("Key not found: " + path + "." + key);
        }

        // Most static secrets are strings; if not, convert to JSON string.
        if (valueNode.isTextual()) {
            return valueNode.asText();
        }
        return MAPPER.writeValueAsString(valueNode);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close() {
        // Clear sensitive cached values on shutdown
        cache.clear();
        clientToken = null;
    }

    /**
     * Simple LRU cache with per-entry expiration.
     * Thread-safe via internal synchronization.
     */
    /**
     * Each cached item stores:
     *      - value → the secret itself
     *      - expiresAt → timestamp (milliseconds since epoch) when it should be treated as expired
     * So expiration is checked by comparing:
     *      System.currentTimeMillis() >= expiresAt
     * **/
    private static final class LruExpiringCache {
        private final int maxEntries;
        private final LinkedHashMap<String, Entry> map;

        private static final class Entry {
            final String value;
            final long expiresAt;
            Entry(String value, long expiresAt) {
                this.value = value;
                this.expiresAt = expiresAt;
            }
        }
        /**
         * The cache is built on a LinkedHashMap like this (simplified):
         *      new LinkedHashMap<String, Entry>(16, 0.75f, true) { ... }
         * That last parameter is important:
         *      accessOrder = true
         * This makes the map maintain entries in access order, not insertion order.
         * Meaning:
         *     - whenever you call get(key), that key becomes the “most recently used”
         *     - the “least recently used” entry naturally stays at the front
         *
         * LRU eviction via removeEldestEntry
         *  Inside the LinkedHashMap, we override "removeEldestEntry" method
         *  This method is called automatically by put().
         *  So whenever you insert a new entry, the map checks:
         *      - “Do I now have more than maxEntries items?”
         *      - If yes → it automatically evicts the oldest (“eldest”) entry
         *  Because we are using accessOrder=true, the “eldest” means:
         *    -  least recently used (LRU)
         *  So maxEntries protects you from unbounded memory usage.
         */
        LruExpiringCache(final int maxEntries) {
            this.maxEntries = Math.max(1, maxEntries);
            this.map = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, LruExpiringCache.Entry> eldest) {
                    return size() > LruExpiringCache.this.maxEntries;
                }
            };
        }

        /**
         * What happens when you call get(...)
         * Step-by-step behavior:
         * 1. It looks up the entry in the map.
         * 2. If missing → return null (cache miss)
         * 3. If present:
         *      - It checks expiry time.
         *      - If expired:
         *          - removes it immediately
         *          - returns null (treat as cache miss)
         * 4. If not expired:
         *      - returns the cached value
         * IMPORTANT SIDE-EFFECT:
         * Because LinkedHashMap is in access-order mode, calling map.get(key) also:
         *      - moves that entry to “most recently used” position.
         */
        synchronized String get(String key) {
            Entry e = map.get(key);
            if (e == null) return null;
            if (System.currentTimeMillis() >= e.expiresAt) {
                map.remove(key);
                return null;
            }
            return e.value;
        }
        /**
         * What happens when you call put(...)
         * Step-by-step behavior:
         * 1. Store the entry (or replace existing entry).
         * 2. LinkedHashMap automatically evaluates removeEldestEntry(...)
         * 3. If it now exceeds max size → evicts least recently used entry
         * So the cache keeps itself within maxEntries automatically.
         */
        synchronized void put(String key, String value, long expiresAt) {
            map.put(key, new Entry(value, expiresAt));
        }
        /**
         * What happens when you call clear()
         *      This removes everything, including secrets.
         *      Your provider calls this in close() so secrets don’t remain in memory longer than necessary
         *      when the app shuts down.
         */
        synchronized void clear() {
            map.clear();
        }
    }
}
