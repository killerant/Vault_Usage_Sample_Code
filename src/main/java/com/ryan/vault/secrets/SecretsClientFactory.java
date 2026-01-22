package com.ryan.vault.secrets;

import com.ryan.vault.secrets.providers.EnvSecretsClient;
import com.ryan.vault.secrets.providers.VaultAppRoleKvV2Client;

import java.util.Properties;

/**
 * Factory Pattern: decide which SecretsClient implementation to create based on configuration.
 */
public final class SecretsClientFactory {

    private SecretsClientFactory() {}

    /**
     * Property: secrets.provider = vault | env
     * - vault/openbao: uses AppRole + KV v2
     * - env: reads from environment variables
     */
    public static SecretsClient create(Properties p) throws SecretException {
        String provider = Config.get(p, "secrets.provider", "vault").toLowerCase();

        if ("vault".equals(provider) || "openbao".equals(provider)) {
            return new VaultAppRoleKvV2Client(p);
        }

        if ("env".equals(provider)) {
            return new EnvSecretsClient();
        }

        throw new SecretException("Unknown secrets.provider: " + provider);
    }
}
