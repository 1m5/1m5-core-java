package network.onemfive.core.business;

import network.onemfive.core.Core;
import network.onemfive.core.service.BusinessService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * {@link BitcoinService} registers, starts (an SPV wallet against bitcoinj's RegTest
 * network - no local "bitcoind -regtest" instance required, see
 * {@code bitcoin-client-java}'s {@code BitcoinServiceIntegrationTest}), and is
 * discoverable on the bus as a {@link BusinessService}.
 */
public class BitcoinServiceTest {

    @After
    public void tearDown() {
        Core.reset();
    }

    @Test
    public void registersStartsAndIsDiscoverable() {
        Assert.assertTrue(Core.get().start(new Properties()));
        Core.get().registerServices(BitcoinService.class);
        Assert.assertTrue("BitcoinService should reach RUNNING",
                Core.get().awaitServices(15_000, BitcoinService.class));

        boolean found = false;
        for (BusinessService s : Core.get().bus().findRunningServices(BusinessService.class)) {
            if (s instanceof BitcoinService) found = true;
        }
        Assert.assertTrue("BitcoinService should be discoverable as a BusinessService", found);
    }
}
