package onemfive.routing;

import ra.common.network.Network;
import ra.common.network.NetworkPeer;
import ra.common.FileUtil;
import ra.networkmanager.InMemoryPeerDB;
import ra.networkmanager.P2PRelationship;
import ra.networkmanager.PeerDB;
import ra.networkmanager.RelType;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import static java.util.Objects.isNull;

public class PeerRelationshipsDB extends InMemoryPeerDB {

    private static final Logger LOG = Logger.getLogger(PeerRelationshipsDB.class.getName());

    private boolean initialized = false;
    private String location;
    private String name;
    private Properties properties;
    private final Object peerSaveLock = new Object();

    private final int MaxAcksTracked = 50;
    private final int MaxPeersTracked = 1000;

    // Key: Left Peer ID|Relationship Type|Right Peer ID
    private final Map<String,P2PRelationship> relationships = new HashMap<>();

    public PeerRelationshipsDB() {
        super();
    }

    public P2PRelationship relateByRelType(String idLeftPeer, RelType relType, String idRightPeer) {
        P2PRelationship rel = null;
        if(idLeftPeer==null || idRightPeer==null) {
            LOG.warning("Must provide ids for both peers when relating them.");
            return null;
        }
        if(idLeftPeer.equals(idRightPeer)) {
            LOG.info("Both peers are the same, skipping.");
            return null;
        }
        rel = relationships.get(idLeftPeer+"|"+relType.name()+"|"+idRightPeer);
        if(isNull(rel)) {

        }
        return rel;
    }

    public P2PRelationship getRelationship(String idLeftPeer, RelType relType, String idRightPeer) {
        P2PRelationship rt = null;

        return rt;
    }

    @Override
    public long numberPeersByNetwork(Network network) {
        RelType relType = RelType.fromNetwork(network);
        long count = -1;
        NetworkPeer np = getLocalPeerByNetwork(network);
        if(np==null) {
            return 0;
        }

        return count;
    }


    /**
     * Remove relationship
     */
    public boolean removeRelationship(String startPeerId, RelType relType, String endPeerId) {

        return true;
    }

    /**
     * Saves Peer Request status results.
     * Determine if results change Reliable Peers list.
     * Reliable Peers are defined as peers known by given peer who have displayed
     * a minimum number of acks (SensorsConfig.mr) and minimum avg response time (<= SensorsConfig.lmc)
     *
     * @param startPeerId originator
     * @param relType RelType
     * @param endPeerId destination
     * @param timeSent time sent in milliseconds since epoch
     * @param timeAcknowledged time acknowledged in milliseconds since epoch
     */
    public void savePeerStatusTimes(String startPeerId, RelType relType, String endPeerId, Long timeSent, Long timeAcknowledged) {
        P2PRelationship networkRel = getRelationship(startPeerId, relType, endPeerId);
        if(networkRel!=null) {
            // Update stats
            networkRel.addAck(timeAcknowledged - timeSent);


            LOG.info("Peer status times: {" +
                    "\n\tack received by local peer in: "+(timeAcknowledged-timeSent)+"ms"+
                    "\n\tlast ack: "+networkRel.getLastAckTime()+
                    "\n\ttotal acks: "+networkRel.getTotalAcks()+
                    "\n\tmed round trip latency: "+networkRel.getMedAckLatencyMS()+
                    "\n\tavg round trip latency: "+networkRel.getAvgAckLatencyMS()+"ms\n} of remote peer "+endPeerId+" with start peer "+startPeerId);

        } else if(RelType.toNetwork(relType.name())!=null && numberPeersByNetwork(startPeerId, RelType.toNetwork(relType.name())) <= MaxPeersTracked) {
            relateByRelType(startPeerId, relType, endPeerId);
            LOG.info("New relationship ("+ relType.name()+") with peer: "+endPeerId);
        } else {
            LOG.info("Max peers tracked: "+ MaxPeersTracked);
        }
    }
}
