package network.onemfive.core.identity;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ra.did.nostr.NostrEvent;
import ra.did.nostr.NostrKeys;

import java.io.File;
import java.util.ArrayList;
import java.util.Properties;

/**
 * The node identity: real secp256k1 / BIP-340 through did-java, sealed at rest
 * when {@code 1m5.pass} is available, and stable across restarts.
 */
public class IdentityServiceTest {

    private File dir;

    @Before
    public void setUp() {
        // clear any identity from a prior run so the test is idempotent
        IdentityService probe = new IdentityService();
        probe.start(new Properties());
        dir = probe.getServiceDirectory();
        probe.shutdown();
        for (String n : new String[]{"node.pub", "node.sec"}) {
            new File(dir, n).delete();
        }
        File idDir = new File(dir, "identity");
        File[] sealed = idDir.listFiles();
        if (sealed != null) for (File f : sealed) f.delete();
    }

    private static Properties withPass(String pass) {
        Properties p = new Properties();
        if (pass != null) p.setProperty(IdentityService.PROP_PASS, pass);
        return p;
    }

    @Test
    public void generatesARealXOnlyIdentitySealedAtRest() {
        IdentityService svc = new IdentityService();
        Assert.assertTrue(svc.start(withPass("node-passphrase")));

        String pub = svc.getNodePublicKeyHex();
        Assert.assertTrue("x-only pubkey should be 64 lowercase hex", NostrKeys.isHex64(pub));
        Assert.assertEquals(pub, NostrKeys.npubToHex(svc.getNodeNpub()));
        Assert.assertTrue(svc.getNodeDid().startsWith("did:nostr:"));
        Assert.assertTrue(svc.isEncryptedAtRest());
        Assert.assertTrue(svc.hasNodeSecret());

        Assert.assertTrue(new File(dir, "node.pub").exists());
        Assert.assertFalse("no plaintext secret when 1m5.pass is set", new File(dir, "node.sec").exists());
        Assert.assertTrue(new File(new File(dir, "identity"), pub + ".json").exists());

        NostrEvent e = NostrEvent.unsigned(1, new ArrayList<>(), "hello from the node", 1_700_000_000L);
        svc.signAsNode(e);
        Assert.assertTrue(e.verify().ok);
        Assert.assertEquals(pub, e.getPubkey());
        svc.shutdown();
    }

    @Test
    public void identityIsStableAcrossRestarts() {
        IdentityService first = new IdentityService();
        first.start(withPass("pw"));
        String pub = first.getNodePublicKeyHex();
        first.shutdown();

        IdentityService second = new IdentityService();
        Assert.assertTrue(second.start(withPass("pw")));
        Assert.assertEquals(pub, second.getNodePublicKeyHex());
        Assert.assertTrue(second.hasNodeSecret());
        Assert.assertTrue(second.isEncryptedAtRest());

        // wrong passphrase: identity known, but no usable secret
        IdentityService wrong = new IdentityService();
        wrong.start(withPass("nope"));
        Assert.assertEquals(pub, wrong.getNodePublicKeyHex());
        Assert.assertFalse(wrong.hasNodeSecret());
        wrong.shutdown();
        second.shutdown();
    }

    @Test
    public void withoutAPassphraseTheSecretIsPlaintextThenUpgraded() {
        IdentityService noPass = new IdentityService();
        noPass.start(withPass(null));
        String pub = noPass.getNodePublicKeyHex();
        Assert.assertTrue(NostrKeys.isHex64(pub));
        Assert.assertFalse(noPass.isEncryptedAtRest());
        Assert.assertTrue(noPass.hasNodeSecret());
        Assert.assertTrue("plaintext secret without 1m5.pass", new File(dir, "node.sec").exists());
        noPass.shutdown();

        // now start with a passphrase - it upgrades in place
        IdentityService upgraded = new IdentityService();
        upgraded.start(withPass("finally-a-pass"));
        Assert.assertEquals(pub, upgraded.getNodePublicKeyHex());
        Assert.assertTrue(upgraded.isEncryptedAtRest());
        Assert.assertTrue(upgraded.hasNodeSecret());
        Assert.assertFalse("plaintext node.sec removed after upgrade", new File(dir, "node.sec").exists());
        Assert.assertTrue(new File(new File(dir, "identity"), pub + ".json").exists());
        upgraded.shutdown();
    }
}
