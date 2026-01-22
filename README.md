# Vault AppRole + KV v2 (Java 8) — Factory + Cache + Jackson

This project is the same AppRole + KV v2 implementation, but uses **Jackson** for JSON parsing.

## Config file on VM
Create: `c:/temp/vault_token.properties`

```properties
secrets.provider=vault
vault.addr=https://vault.company:8200
vault.mount=secret
vault.approle.role_id=YOUR_ROLE_ID
vault.approle.secret_id=YOUR_SECRET_ID

# cache
secrets.cache.ttlSeconds=300
secrets.cache.maxEntries=200

vault.timeoutMs=5000
```

## Build & run with Maven (recommended)

```bat
mvn -q -DskipTests package
java -jar targetault-approle-java8-1.0.0.jar c:/temp/vault_token.properties integration/systemA password
```

## Build without Maven (manual)
If you cannot use Maven, download these jars and place them in `lib/`:
- jackson-databind
- jackson-core
- jackson-annotations

Then compile:

```bat
mkdir out
javac -source 1.8 -target 1.8 -cp "lib/*" -d out src\main\java\comankapp\secrets\*.java src\main\java\comankapp\secrets\providers\*.java src\main\java\comankapppp\Main.java
```

Run:

```bat
java -cp "out;lib/*" app.com.ryan.vault.Main c:/temp/vault_token.properties integration/systemA password
```

## HTTPS truststore (if needed)
If Vault uses an internal CA not trusted by the JVM, pass a truststore when launching:

```bat
java -Djavax.net.ssl.trustStore=C:/path/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit      -jar targetault-approle-java8-1.0.0.jar c:/temp/vault_token.properties
```
