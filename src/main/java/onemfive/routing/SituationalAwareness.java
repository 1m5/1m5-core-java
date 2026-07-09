package onemfive.routing;

import onemfive.ManCon;
import ra.common.network.Network;

public class SituationalAwareness {
    public Network desiredNetwork = null;
    public boolean desiredNetworkConnected = false;
    public boolean isWebRequest;
    public int envelopeSensitivity;
    public ManCon envelopeManCon;
    public boolean envelopeSensitivityWithinMaxAvailableManCon;
    public boolean envelopeSensitivityWithinMinRequiredManCon;
    public ManCon selectedManCon;
}
