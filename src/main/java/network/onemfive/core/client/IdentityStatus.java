package network.onemfive.core.client;

/**
 * Node identity summary across the {@link CoreClient} boundary - public id only,
 * per {@code DESIGN.md}'s verb table. Mirrors the public fields of
 * {@code network.onemfive.core.identity.IdentityService}.
 */
public final class IdentityStatus {

    private static final IdentityStatus UNAVAILABLE =
            new IdentityStatus(null, null, null, false, false);

    private final String publicKeyHex;
    private final String npub;
    private final String did;
    private final boolean encryptedAtRest;
    private final boolean hasSecret;

    public IdentityStatus(String publicKeyHex, String npub, String did,
                           boolean encryptedAtRest, boolean hasSecret) {
        this.publicKeyHex = publicKeyHex;
        this.npub = npub;
        this.did = did;
        this.encryptedAtRest = encryptedAtRest;
        this.hasSecret = hasSecret;
    }

    /** No {@code IdentityService} running, or it has not established an identity yet. */
    public static IdentityStatus unavailable() { return UNAVAILABLE; }

    public String getPublicKeyHex() { return publicKeyHex; }
    public String getNpub() { return npub; }
    public String getDid() { return did; }
    public boolean isEncryptedAtRest() { return encryptedAtRest; }
    public boolean hasSecret() { return hasSecret; }

    @Override
    public String toString() {
        return "IdentityStatus{did=" + did + ", encryptedAtRest=" + encryptedAtRest
                + ", hasSecret=" + hasSecret + "}";
    }
}
