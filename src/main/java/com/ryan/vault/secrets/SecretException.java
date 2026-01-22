package com.ryan.vault.secrets;

public class SecretException extends Exception {
    public SecretException(String message) { super(message); }
    public SecretException(String message, Throwable cause) { super(message, cause); }
}
