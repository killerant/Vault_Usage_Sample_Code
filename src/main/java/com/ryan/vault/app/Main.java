package com.ryan.vault.app;

import com.ryan.vault.secrets.*;
import com.ryan.vault.secrets.Config;
import com.ryan.vault.secrets.SecretsClient;
import com.ryan.vault.secrets.SecretsClientFactory;

import java.util.Properties;

/**
 * Demo program (Java 8, non-Spring):
 *  - Reads config from c:/temp/vault_token.properties by default
 *  - Uses factory to create the secrets provider
 *  - Fetches a secret value (static password/key)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String configFile = args.length > 0 ? args[0] : "c:/Temp/vault_token.properties";
        String secretPath  = args.length > 1 ? args[1] : "myapp/config";
        String secretKey   = args.length > 2 ? args[2] : "password";

        Properties p = Config.load(configFile);

        SecretsClient secrets = null;
        try {
            secrets = SecretsClientFactory.create(p);
            String value = secrets.getRequired(secretPath, secretKey);
            System.out.println("Secret retrieved for " + secretPath + "." + secretKey + " (length=" + value.length() + ")");
        } finally {
            if (secrets != null) secrets.close();
        }
    }
}
