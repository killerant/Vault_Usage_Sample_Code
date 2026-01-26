![Static Badge](https://img.shields.io/badge/Java-red?style=for-the-badge)

# Vault AppRole + KV v2 (Java 8) — Using Factory

This project is a sample of how to use Hashicorp Vault AppRole + KV v2 implementation. This sample is utilizing the factory
design pattern to ensure we can easily swap out different secret providers _(e.g. vault, environment variables, property files, etc.)_ 
in the future if needed depending on the requirement. 

Available providers in the factory as of now:
- __VaultSecretProvider:__ For Hashicorp Vault and openbao
- __PropertiesFileSecretProvider:__ For properties file
- __EnvSecretProvider:__ For environment variables

## Software Versions Used
| Software        | Version | 
|-----------------|---------|
| Hashicorp Vault | 1.21.0  | 
| Java 8          | 8/17    | 
| Windows         | 11      | 

**NOTE:** All the steps below were tested using Java 17 except the last section which was tested 
using Java 8. We can try running all the commands below using Java 8 but might require some adjustments.

## Configure and Start the Vault
Start the vault in dev mode: (Open a command prompt)

```bat
vault server -dev
``` 
Open a different command prompt and configure the vault:

- Set the environment variable VAULT_ADDR
    ```bat
    set VAULT_ADDR=http://127.0.0.1:8200
    ```
- Create a policy file (e.g. myapp-policy.hcl) with the following content:
    ```hcl
  path "secret/data/myapp/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
  }
  ```
- Create the policy in vault:
    ```bat
    vault policy write myapp-policy myapp-policy.hcl
    ```
- Enable AppRole authentication method:
    ```bat
    vault auth enable approle
    ```
- Create an AppRole _(i.e. myapp-role)_ with the policy created above:
  - __token_policies="myapp-policy"__: Tokens issued via this AppRole will have the myapp-policy policy attached, controlling their permissions.
  - __secret_id_ttl=0__: The Secret ID for this AppRole will never expire (TTL = 0 means no expiration). Configure this for development only.
  ```bat
  vault write auth/approle/role/myapp-role token_policies="myapp-policy" secret_id_ttl=0 
  ```
- Retrieve the Role ID for the AppRole created above (Take note of the role id as this is what we need to put on the config file):
    ```bat
    vault read auth/approle/role/myapp-role/role-id
    ```
- Generate a Secret ID for the AppRole created above (Take note of the secret id as this is what we need to put on the config file):
    ```bat
    vault write -f auth/approle/role/myapp-role/secret-id
    ```
- Add a secret to the vault at path `secret/data/myapp/config` (for testing purposes):
    ```bat
    vault kv put secret/myapp/config username=<USERNAME_TO_STORE> password=<PASSWORD_TO_STORE>
    ```

## Config the vault properites file on local machine (e.g. c:/temp/vault_token.properties )
Create: `c:/temp/vault_token.properties`
- __secret.provider__: Set to `vault` to indicate that we are using Hashicorp Vault as the secret provider. In the existing code we can also use `env` _(use environment variables)_ or `file` _(use a properties file)_ as secret providers.
- __vault.addr__: The address of the Vault server.
- __vault.mount__: The mount point for the KV secrets engine (default is `secret`).
- __vault.approle.role_id__: The Role ID obtained from the previous step.
- __vault.approle.secret_id__: The Secret ID obtained from the previous step.
- __secrets.cache.ttlSeconds__: Time-to-live for cached secrets in seconds (optional).
- __secrets.cache.maxEntries__: Maximum number of entries in the cache (optional).
- __vault.timeoutMs__: Timeout for Vault operations in milliseconds (optional).

```properties
secrets.provider=vault
vault.addr=http://127.0.0.1:8200
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
mvn clean install
```

Run:

In Intellij, just right-click the main class and select run/debug.

------------------------------------------------------------------------------
## HOW TO RUN WITH TLS ENABLED


### Create a TLS certificate (self-signed for dev/test purposes)
- Start __gitbash__
- Create a working directory (e.g. C:/Users/bulalra1/vault_tls)
```bat
mkdir vault-tls
```
- Change directory
```bat
cd vault-tls
```
- Create an OpenSSL config file (e.g. vault.cnf)
```cnf

[ req ]
default_bits       = 2048
prompt             = no
default_md         = sha256
distinguished_name = dn
x509_extensions    = v3_req

[ dn ]
C  = PH
ST = Bulacan
L  = SJDM
O  = Finastra
OU = Equation
CN = localhost
emailAddress = rai@gmail.com

[ v3_req ]
subjectAltName = @alt_names

[ alt_names ]
DNS.1 = localhost
IP.1  = 127.0.0.1

```
- Generate a self‑signed cert
```bat
openssl req -x509 -nodes -days 365 ^
  -newkey rsa:2048 ^
  -keyout vault.key ^
  -out vault.crt ^
  -config vault.cnf
```
- Verify Subject Alternative Name (SAN) is present. This is important because Modern TLS 
clients (including Vault CLI, Go’s TLS stack, curl, browsers) ignore the CN and require SAN.
```bat
openssl x509 -in vault.crt -noout -text
```
You must see:
``` text
X509v3 Subject Alternative Name:
    DNS:localhost, IP Address:127.0.0.1
```
### Configure the Vault to use the TLS certificate
- Create a config file for vault (e.g. vault.hcl)
```bat
ui = true

listener "tcp" {
  address       = "127.0.0.1:8200"
  tls_cert_file = "vault-tls/vault.crt"
  tls_key_file  = "vault-tls/vault.key"
}

storage "file" {
  path = "vault-data"
}

disable_mlock = true
```
What this does:

| Setting          | Meaning              | 
|------------------|----------------------|
| listener "tcp"   | Vault listens on TCP | 
| tls_cert_file    | Enables HTTPS        | 
| tls_key_file     | Private key for TLS  | 
| storage "file"   | Simple local storage | 
| disable_mlock    | Required on Windows  | 

__NOTE:__ When we add __tls_cert_file__, Vault becomes HTTPS‑only

- Start the Vault with TLS enabled
```bat
vault server -config=vault.hcl
```
You should see logs like:
```text
==> Vault server configuration:
             Listener 1: tcp (addr: "127.0.0.1:8200", tls: "enabled")
```
### Initialize and unseal Vault
- Open a new command prompt
- Tell Vault CLI to use HTTPS
```bat
set VAULT_ADDR=https://127.0.0.1:8200
```
```bat
set VAULT_CACERT=C:\Users\bulalar1\vault-tls\vault.crt
```
- Initialize Vault
```bat
vault operator init
```
Take note of the unseal keys and root token displayed.
Sample:
```text
C:\Users\bulalar1>vault operator init
Unseal Key 1: UNSEAL_KEY_1_PLACEHOLDER
Unseal Key 2: UNSEAL_KEY_2_PLACEHOLDER
Unseal Key 3: UNSEAL_KEY_3_PLACEHOLDER
Unseal Key 4: UNSEAL_KEY_4_PLACEHOLDER
Unseal Key 5: UNSEAL_KEY_5_PLACEHOLDER

Initial Root Token: TOKEN_PLACEHOLDER
```
**NOTE:** Take note of the keys and root token as you cannot recover them again once you close the command prompt window.

- Unseal Vault (need 3 of the 5 keys)
```bat
vault operator unseal
```
You will see:
```text
Unseal Key (will be hidden): 
```
Just enter the Unseal key from the **_vault operator init_** in the previous step. Repeat this 3 times with 
different keys (e.g. Key 1-3) until you see that the vault is unsealed _(you will see Sealed = true on the first unseal commands)_:
```text
Sealed          false
```
### Configure Approle
Enable the KV secrets engine. Enable KV v2 at the standard _**secret/**_ path:
```bat
vault secrets enable -path=secret kv-v2
```
It should display:
```text
Success! Enabled the kv-v2 secrets engine at: secret/
```
Verify it exists now
```bat
vault secrets list -detailed
```
It will display:
```text
Path        Type   Version
----        ----   -------
secret/     kv     2
```
Create a policy file (e.g. myapp-policy.hcl) with the following content:
```hcl
  path "secret/data/myapp/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
  }
```
Create the policy in vault:
```bat
vault policy write myapp-policy myapp-policy.hcl
```
Enable AppRole authentication method:
```bat
vault auth enable approle
```
Create an AppRole _(i.e. myapp-role)_ with the policy created above:
- __token_policies="myapp-policy"__: Tokens issued via this AppRole will have the myapp-policy policy attached, controlling their permissions.
- __secret_id_ttl=0__: The Secret ID for this AppRole will never expire (TTL = 0 means no expiration). Configure this for development only.
  ```bat
  vault write auth/approle/role/myapp-role token_policies="myapp-policy" secret_id_ttl=0 
  ```
Retrieve the Role ID for the AppRole created above (Take note of the role id as this is what we need to put on the config file):
```bat
vault read auth/approle/role/myapp-role/role-id
```
Generate a Secret ID for the AppRole created above (Take note of the secret id as this is what we need to put on the config file):
```bat
vault write -f auth/approle/role/myapp-role/secret-id
```
Add a secret to the vault at path `secret/data/myapp/config` (for testing purposes):
```bat
vault kv put secret/myapp/config username=USERNAME_TO_STORE password=PASSWORD_TO_STORE
```
**NOTE:** You can try to view the secrets via the UI: `https://127.0.0.1:8200/ui`. Use the root token from the 
initialization step to login.

### Test via Postman
- Login to get the token:
  - URL: `https://127.0.0.1:8200/v1/auth/approle/login'
  - Method: POST
  - Headers:
    - Content-Type: application/json
  - Body: (raw)
    ```json
    {
      "role_id": "<role-id-from-above>",
      "secret_id": "<secret-id-from-above>"
    }
    ```
  Take note of _**client_token**_ from the response
  - Sample:
  ```text
    "auth": {
        "client_token": CLIENT_TOKEN_PLACEHOLDER,
        "accessor": "ImilFkRIkEWUtJ5Ew6Szoi0F",
  ```
- Create a new request to retrieve the secret:
  - URL: `https://127.0.0.1:8200/v1/secret/data/myapp/config'
  - Method: GET
  - Headers:
    - X-Vault-Token: _**client_token**_ retured from the login above

  Response will contain the secrets.
  - Sample:
  ```json
  {
    "request_id": "0d90596c-4d76-c723-d334-8e9edf1d6006",
    "lease_id": "",
    "renewable": false,
    "lease_duration": 0,
    "data": {
        "data": {
            "password": PASSWORD_PLACEHOLDER,
            "username": "myuser"
        },
        "metadata": {
            "created_time": "2026-01-26T11:22:03.6248571Z",
            "custom_metadata": null,
            "deletion_time": "",
            "destroyed": false,
            "version": 1
        }
    },
    "wrap_info": null,
    "warnings": null,
    "auth": null,
    "mount_type": "kv"
  }
  ```
**NOTE:** You will get the warning below in thre response given that the Vault is configured
to use TLS and we have not configured Postman to trust the self-signed certificate.
```text
GET https://127.0.0.1:8200/v1/secret/data/myapp/config
Warning: Self signed certificate
```
### Test via the Java App
- Update the `vault_token.properties` file in the project root to use `https` for the
- Given that we have not configured the app to recognize the self-signed certificate, we will get the error below:
```text
Exception in thread "main" com.ryan.vault.secrets.SecretException: Failed to read secret from Vault
	at com.ryan.vault.secrets.providers.VaultAppRoleKvV2Client.getRequired(VaultAppRoleKvV2Client.java:89)
	at com.ryan.vault.app.Main.main(Main.java:29)
Caused by: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
	at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:131)
```


--------------------------------------------------------------------
**NOTE:** All the steps above was done using Java 17 as I forgot to switch my JAVA_HOME to Java 8. Only the steps below were 
done using Java 8. We can try to do all the steps above using Java 8 but might require some adjustments.

## Configure the HTTPS truststore for the App (Running in Intellij)
Create a dedicated truststore
```bat
"%JAVA_HOME%\bin\keytool" -importcert -noprompt ^
  -alias vault-ca ^
  -file C:\Users\bulalar1\vault-tls\vault.crt ^
  -keystore C:\Users\bulalar1\vault-tls\java8-vault-truststore.jks ^
  -storetype JKS ^
  -storepass TRUSTSTORE_PASSWORD_TO_USE

```
- This will create _java8-vault-truststore.jks_ file in the current directory.
- The truststore contains the self-signed certificate _(vault.crt)_ that we created earlier.

Configure IntelliJ to use this truststore when running the application.
In Intellij:
- Run > **Edit Configurations**
- Select this java application
- In the __VM options__ field, add the following options to specify the truststore:
```bat
 -Djavax.net.ssl.trustStore=C:\Users\bulalar1\vault-tls\java8_vault-truststore.jks 
 -Djavax.net.ssl.trustStorePassword=TRUSTSTORE_PASSWORD_USED_IN_THE_STEP_ABOVE
 -Djavax.net.ssl.trustStoreType=JKS 
 -Djdk.tls.client.protocols=TLSv1.2 
```

## Run the app via command line (if we need to run the app from the command line):
```bat
java -Djavax.net.ssl.trustStore=C:/path/truststore.jks -Djavax.net.ssl.trustStorePassword=trustPassword      -jar targetault-approle-java8-1.0.0.jar c:/temp/vault_token.properties
```
### Additional Notes
- When you need to rerun the Vault after closing the command prompt, you won't need to reinitialize it again. Just unseal it again using the unseal keys from the first initialization _(i.e.vault operator unseal)_.