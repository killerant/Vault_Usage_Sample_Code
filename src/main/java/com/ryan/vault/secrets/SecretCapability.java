package com.ryan.vault.secrets;

/**
 * Capability-driven design: what a secrets provider can do.
 * For your requirement, we only need KV_READ.
 */
public enum SecretCapability {
    KV_READ
}
