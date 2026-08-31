package network.onemfive.core.identity;

import network.onemfive.core.service.DataService;
import ra.common.Envelope;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Internal data service that owns the node's identity and (later) user identities
 * and contacts.
 *
 * <p>Direction (1m5-docs ADR-0002): Nostr-compatible identities - secp256k1
 * 32-byte private key, x-only public key, hex / {@code npub}, BIP-340 Schnorr
 * signing, SHA-256 event ids, canonical event serialization, keys encrypted at
 * rest. Legacy OpenPGP ({@code ra.common.identity.DID} + keyring) is kept
 * read-only for verifying old contacts and OpenPGP-to-Nostr migration proofs.
 *
 * <p><b>Skeleton scope:</b> establish a stable 32-byte node secret and a public
 * identifier so the node has an identity. It uses JCA secp256k1 when the running
 * JDK provides it, otherwise a {@link SecureRandom} secret with a SHA-256-derived
 * placeholder id. Proper x-only point derivation, BIP-340 Schnorr, the event
 * model, encryption-at-rest, {@code npub}/{@code nsec}, migration proofs, and
 * key-domain separation are a later phase and will bring in a real secp256k1
 * implementation (see TODO.md).
 */
public final class IdentityService extends DataService {

    private static final Logger LOG = Logger.getLogger(IdentityService.class.getName());

    public static final String OPERATION_GET_NODE_IDENTITY = "GET_NODE_IDENTITY";

    private volatile String nodePublicKeyHex;
    private volatile boolean realSecp256k1;

    public String getNodePublicKeyHex() {
        return nodePublicKeyHex;
    }

    public boolean isRealSecp256k1() {
        return realSecp256k1;
    }

    @Override
    public boolean start(Properties p) {
        if (!super.start(p)) return false;
        try {
            File pubFile = new File(getServiceDirectory(), "node.pub");
            File secFile = new File(getServiceDirectory(), "node.sec");
            if (pubFile.exists()) {
                nodePublicKeyHex = new String(Files.readAllBytes(pubFile.toPath())).trim();
                LOG.info("loaded node identity " + shortKey());
            } else {
                byte[][] keypair = generate();
                byte[] secret = keypair[0];
                byte[] pub = keypair[1];
                nodePublicKeyHex = toHex(pub);
                Files.write(pubFile.toPath(), nodePublicKeyHex.getBytes());
                // TODO(P2): encrypt the secret at rest (1m5.pass-derived key).
                Files.write(secFile.toPath(), secret);
                LOG.info("generated node identity " + shortKey()
                        + (realSecp256k1 ? " (secp256k1)" : " (placeholder - secp256k1 unavailable on this JDK)"));
            }
            return true;
        } catch (Exception e) {
            LOG.warning("could not establish node identity: " + e.getMessage());
            return true; // skeleton: don't fail the daemon over this
        }
    }

    /** @return {@code [secret(32B), publicId]}. */
    private byte[][] generate() throws Exception {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256k1"));
            KeyPair kp = kpg.generateKeyPair();
            realSecp256k1 = true;
            // Skeleton: store the encoded forms; x-only derivation comes in P2.
            return new byte[][]{kp.getPrivate().getEncoded(), kp.getPublic().getEncoded()};
        } catch (Exception noCurve) {
            realSecp256k1 = false;
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            byte[] pub = MessageDigest.getInstance("SHA-256").digest(secret);
            return new byte[][]{secret, pub};
        }
    }

    @Override
    public void handleDocument(Envelope envelope) {
        String op = envelope.getRoute() != null ? envelope.getRoute().getOperation() : null;
        if (OPERATION_GET_NODE_IDENTITY.equals(op)) {
            envelope.addNVP("nodePublicKeyHex", nodePublicKeyHex);
            envelope.addNVP("realSecp256k1", realSecp256k1);
        } else {
            LOG.warning("unsupported operation: " + op);
        }
    }

    private String shortKey() {
        return nodePublicKeyHex == null ? "(none)"
                : nodePublicKeyHex.substring(0, Math.min(16, nodePublicKeyHex.length())) + "...";
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }
}
