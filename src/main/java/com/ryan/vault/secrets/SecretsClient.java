package com.ryan.vault.secrets;

import java.util.EnumSet;

/**
 * Minimal secrets interface for static secrets.
 * Your app depends ONLY on this interface to remain provider-agnostic.
 */
public interface SecretsClient extends AutoCloseable {

    EnumSet<SecretCapability> capabilities();

    /**
     * Read a secret value from a logical path and key.
     * Example: path="integration/systemA", key="password".
     */
    String getRequired(String path, String key) throws SecretException;

    @Override
    void close();
}
