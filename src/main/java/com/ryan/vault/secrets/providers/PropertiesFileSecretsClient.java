package com.ryan.vault.secrets.providers;

import com.ryan.vault.secrets.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Properties;

/**
 * Provider that reads secrets from an external .properties file.
 *
 * Key format:
 *    <path>.<key>
 * Example:
 *    integration/systemA.password=SuperSecret123
 */

public class PropertiesFileSecretsClient implements SecretsClient {

    private final Properties props = new Properties();
    private final String filePath;

    public PropertiesFileSecretsClient(Properties config) throws SecretException {
        this.filePath = Config.req(config, "secrets.file.path");
        loadFromDisk();
    }

    @Override
    public EnumSet<SecretCapability> capabilities() {
        return EnumSet.of(SecretCapability.KV_READ);
    }

    @Override
    public String getRequired(String path, String key) throws SecretException {
        String propKey = path + "." + key;
        String value = props.getProperty(propKey);

        if (value == null || value.trim().isEmpty()) {
            throw new SecretNotFoundException(
                    "Missing property secret: " + propKey + " in file: " + filePath
            );
        }
        return value.trim();
    }

    private void loadFromDisk() throws SecretException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(filePath);
            props.load(in);
        } catch (IOException e) {
            throw new SecretException("Failed to load secrets file: " + filePath, e);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() {
        // If you want to reduce secret lifetime in memory:
        props.clear();
    }

}
