package network.onemfive.core.identity;

import network.onemfive.core.service.DataService;
import ra.common.Envelope;
import ra.did.nostr.Bip340;
import ra.did.nostr.NostrEvent;
import ra.did.nostr.NostrIdentity;
import ra.did.nostr.NostrIdentityStore;
import ra.did.nostr.NostrKeyRing;
import ra.did.nostr.NostrKeys;

import java.io.File;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Owns the node's identity (and, later, user identities and contacts).
 *
 * <p>Per 1m5-docs ADR-0002 / DID {@code DESIGN.md} §7.4 the node identity is a
 * Nostr-compatible secp256k1 keypair: a 32-byte secret, an x-only BIP-340 public
 * key, and hex / {@code npub} / {@code did:nostr} encodings. Key generation,
 * derivation and signing go through {@code did-java}'s {@link NostrKeyRing}
 * (ACINQ {@code secp256k1-kmp} / libsecp256k1); there is no JCA fallback and no
 * placeholder id any more.
 *
 * <p><b>At rest</b> (§6.3): with {@code 1m5.pass} available the secret is sealed
 * (Argon2id + AES-256-GCM) into {@code <serviceDir>/identity/<pubkey>.json} and
 * {@code node.pub} holds the public key as a pointer. Without a passphrase the
 * node still gets a real identity, but its secret is written unencrypted to
 * {@code node.sec} with a warning; it is upgraded to a sealed file automatically
 * the first time the service starts with {@code 1m5.pass} set.
 *
 * <p>Legacy OpenPGP ({@code ra.common.identity.DID} + {@code ra.did.openpgp})
 * stays available for read-only verification of old contacts and
 * OpenPGP-to-Nostr migration proofs.
 */
public final class IdentityService extends DataService {

    private static final Logger LOG = Logger.getLogger(IdentityService.class.getName());

    public static final String OPERATION_GET_NODE_IDENTITY = "GET_NODE_IDENTITY";

    /** Passphrase source: this config key, else the {@code 1m5.pass} environment variable. */
    public static final String PROP_PASS = "1m5.pass";

    private NostrKeyRing keyRing;
    private NostrIdentity node; // null if the identity exists on disk but its secret is unavailable

    private volatile String nodePublicKeyHex;
    private volatile String nodeNpub;
    private volatile String nodeDid;
    private volatile boolean encryptedAtRest;

    public String getNodePublicKeyHex() { return nodePublicKeyHex; }
    public String getNodeNpub() { return nodeNpub; }
    public String getNodeDid() { return nodeDid; }
    public boolean isEncryptedAtRest() { return encryptedAtRest; }

    /** True once the node holds a usable secret (can {@link #signAsNode}). */
    public boolean hasNodeSecret() { return node != null && node.hasSecret(); }

    /** @deprecated the identity is always real secp256k1 now; kept for wire/API compatibility. */
    @Deprecated
    public boolean isRealSecp256k1() { return nodePublicKeyHex != null; }

    /** Sign an event in place with the node's key. Requires {@link #hasNodeSecret()}. */
    public NostrEvent signAsNode(NostrEvent event) {
        if (!hasNodeSecret()) throw new IllegalStateException("node secret unavailable");
        return keyRing.sign(event, node);
    }

    /**
     * BIP-340 Schnorr-sign the SHA-256 of {@code canonicalBytes} with the node's
     * key - the {@link network.onemfive.core.client.CoreClient#signAsNode(byte[])}
     * primitive. Requires {@link #hasNodeSecret()}. Unlike {@link #signAsNode(NostrEvent)}
     * this does not build or verify a Nostr event; the caller owns whatever
     * canonical form {@code canonicalBytes} is (a Nostr event's own canonical
     * preimage, or any other canonical payload the host needs the node identity to
     * attest to).
     */
    public byte[] signAsNode(byte[] canonicalBytes) {
        if (!hasNodeSecret()) throw new IllegalStateException("node secret unavailable");
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(canonicalBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        byte[] secret = NostrKeys.fromHex(node.secretHex());
        try {
            return Bip340.sign(digest, secret);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        keyRing = new NostrKeyRing();
        if (!keyRing.init(p)) {
            LOG.severe("BIP-340 backend failed to load - node identity unavailable");
            return true; // don't take the daemon down over it
        }
        File dir = getServiceDirectory();
        keyRing.setStore(new NostrIdentityStore(new File(dir, "identity")));

        String pass = resolvePass(p);
        File pubFile = new File(dir, "node.pub");
        File legacySec = new File(dir, "node.sec");

        try {
            if (pubFile.exists()) {
                nodePublicKeyHex = new String(Files.readAllBytes(pubFile.toPath())).trim();
                loadExisting(pass, legacySec);
            } else {
                firstRun(pubFile, legacySec, pass);
            }
            if (nodePublicKeyHex != null) {
                nodeNpub = NostrKeys.hexToNpub(nodePublicKeyHex);
                nodeDid = NostrKeys.hexToDid(nodePublicKeyHex);
                LOG.info("node identity " + shortKey()
                        + (hasNodeSecret()
                        ? (encryptedAtRest ? " (secret sealed at rest)" : " (secret UNENCRYPTED - set 1m5.pass)")
                        : " (secret unavailable)"));
            }
            return true;
        } catch (Exception e) {
            LOG.warning("could not establish node identity: " + e.getMessage());
            return true;
        }
    }

    private void loadExisting(String pass, File legacySec) throws Exception {
        if (keyRing.isPersisted(nodePublicKeyHex)) {
            if (pass == null) {
                LOG.warning("node identity " + shortKey() + " is sealed but 1m5.pass is not set - "
                        + "the node cannot sign until it is provided");
                return;
            }
            try {
                node = keyRing.loadIdentity(nodePublicKeyHex, pass);
                encryptedAtRest = true;
            } catch (GeneralSecurityException bad) {
                LOG.warning("could not open the sealed node identity: " + bad.getMessage());
            }
            return;
        }
        if (legacySec.exists()) {
            byte[] raw = Files.readAllBytes(legacySec.toPath());
            byte[] secret = raw.length == 32 ? raw : Arrays.copyOf(raw, 32);
            node = NostrIdentity.fromSecretKey(secret);
            Arrays.fill(secret, (byte) 0);
            if (!node.getPublicKeyHex().equals(nodePublicKeyHex)) {
                LOG.warning("node.sec does not derive node.pub - keeping the recorded public key");
            }
            if (pass != null) {
                keyRing.persist(node, pass);
                secureDelete(legacySec);
                encryptedAtRest = true;
                LOG.info("upgraded node identity to a sealed secret; removed plaintext node.sec");
            } else {
                LOG.warning("node identity secret is stored UNENCRYPTED (node.sec) - set 1m5.pass to seal it");
            }
            return;
        }
        LOG.warning("node identity " + shortKey() + " is recorded but no secret is available "
                + (pass == null ? "(1m5.pass not set and no node.sec)" : "(no sealed file and no node.sec)"));
    }

    private void firstRun(File pubFile, File legacySec, String pass) throws Exception {
        node = keyRing.generateIdentity();
        nodePublicKeyHex = node.getPublicKeyHex();
        Files.write(pubFile.toPath(), nodePublicKeyHex.getBytes());
        if (pass != null) {
            keyRing.persist(node, pass);
            encryptedAtRest = true;
            LOG.info("generated node identity " + shortKey() + " (secret sealed at rest)");
        } else {
            byte[] secret = NostrKeys.fromHex(node.secretHex());
            Files.write(legacySec.toPath(), secret);
            Arrays.fill(secret, (byte) 0);
            LOG.warning("generated node identity " + shortKey()
                    + " but 1m5.pass is not set - the secret is stored UNENCRYPTED in node.sec");
        }
    }

    @Override
    public void handleDocument(Envelope envelope) {
        String op = envelope.getRoute() != null ? envelope.getRoute().getOperation() : null;
        if (OPERATION_GET_NODE_IDENTITY.equals(op)) {
            envelope.addNVP("nodePublicKeyHex", nodePublicKeyHex);
            envelope.addNVP("nodeNpub", nodeNpub);
            envelope.addNVP("nodeDid", nodeDid);
            envelope.addNVP("encryptedAtRest", encryptedAtRest);
            envelope.addNVP("hasSecret", hasNodeSecret());
        } else {
            LOG.warning("unsupported operation: " + op);
        }
    }

    @Override
    public boolean shutdown() {
        if (node != null) node.clearSensitive();
        return super.shutdown();
    }

    // -- helpers -------------------------------------------------

    private static String resolvePass(Properties p) {
        String fromConfig = p == null ? null : p.getProperty(PROP_PASS);
        if (fromConfig != null && !fromConfig.isEmpty()) return fromConfig;
        String fromEnv = System.getenv(PROP_PASS);
        return (fromEnv != null && !fromEnv.isEmpty()) ? fromEnv : null;
    }

    private static void secureDelete(File f) {
        try {
            long len = f.length();
            if (len > 0) Files.write(f.toPath(), new byte[(int) Math.min(len, 1 << 16)]);
        } catch (Exception ignored) {
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private String shortKey() {
        return nodePublicKeyHex == null ? "(none)"
                : nodePublicKeyHex.substring(0, Math.min(16, nodePublicKeyHex.length())) + "...";
    }
}
