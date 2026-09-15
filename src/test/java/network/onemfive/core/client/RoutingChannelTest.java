package network.onemfive.core.client;

import network.onemfive.core.routing.RoutingService;
import org.junit.Assert;
import org.junit.Test;

/** Catches drift between {@link RoutingChannel}'s app-safe constants and {@link RoutingService}'s real ones - see that class's javadoc for why they're duplicated rather than shared. */
public class RoutingChannelTest {

    @Test
    public void mirrorsRoutingServiceExactly() {
        Assert.assertEquals(RoutingService.class.getName(), RoutingChannel.NAME);
        Assert.assertEquals(RoutingService.OPERATION_ROUTE, RoutingChannel.OPERATION_SEND);
        Assert.assertEquals(RoutingService.OPERATION_REGISTER_PEER, RoutingChannel.OPERATION_REGISTER_PEER);
        Assert.assertEquals(RoutingService.HEADER_X_DEST_PEER, RoutingChannel.HEADER_DEST_PEER_ID);
    }
}
