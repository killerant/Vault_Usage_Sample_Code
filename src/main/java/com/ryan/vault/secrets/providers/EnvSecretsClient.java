package com.ryan.vault.secrets.providers;

import com.ryan.vault.secrets.*;
import com.ryan.vault.secrets.SecretCapability;
import com.ryan.vault.secrets.SecretException;
import com.ryan.vault.secrets.SecretNotFoundException;
import com.ryan.vault.secrets.SecretsClient;

import java.util.EnumSet;

/**
 * Provider that reads secrets from environment variables.
 * Convention: path + '_' + key with separators normalized and upper-cased.
 */
public class EnvSecretsClient implements SecretsClient {

    @Override
    public EnumSet<SecretCapability> capabilities() {
        return EnumSet.of(SecretCapability.KV_READ);
    }

    @Override
    public String getRequired(String path, String key) throws SecretException {
        String env = (path + "_" + key)
                .replace("/", "_")
                .replace("-", "_")
                .replace(".", "_")
                .toUpperCase();

        String value = System.getenv(env);
        if (value == null || value.trim().isEmpty()) {
            throw new SecretNotFoundException("Missing environment secret: " + env);
        }
        return value;
    }

    @Override
    public void close() {
        // no-op
    }
}
