package network.onemfive.core;

import network.onemfive.core.routing.ManConNetworks;
import org.junit.Test;
import ra.common.network.Network;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ManCon band and {@code select()} clamping. Before {@code maxAvailable} was
 * driven from transport readiness it stayed at {@link ManCon#NONE} and every
 * {@code select()} collapsed to NONE - the VERYHIGH/EXTREME/NEO bands were dead.
 */
public class ManConStatusTest {

    @Test
    public void defaultBandCollapsesUntilAvailabilityIsDriven() {
        ManConStatus s = new ManConStatus();
        // maxAvailable == NONE, minRequired == HIGH -> nothing is deliverable
        assertEquals(ManCon.NONE, s.select(ManCon.HIGH));
        assertFalse(s.meetsFloor(s.select(ManCon.HIGH)));
    }

    @Test
    public void selectClampsIntoTheBand() {
        ManConStatus s = new ManConStatus();
        s.setMinRequired(ManCon.HIGH);          // floor: never less severe than HIGH
        s.setMaxAvailable(ManCon.VERYHIGH);     // ceiling: never more severe than VERYHIGH

        assertEquals("raised to the floor", ManCon.HIGH, s.select(ManCon.LOW));
        assertEquals("kept in band", ManCon.HIGH, s.select(ManCon.HIGH));
        assertEquals("clamped to the ceiling", ManCon.VERYHIGH, s.select(ManCon.NEO));
        assertTrue(s.meetsFloor(s.select(ManCon.NEO)));
    }

    @Test
    public void invertedBandIsHeldNotDowngraded() {
        ManConStatus s = new ManConStatus();
        s.setMinRequired(ManCon.HIGH);
        s.setMaxAvailable(ManCon.LOW); // transports cannot meet the floor

        ManCon selected = s.select(ManCon.HIGH);
        assertEquals(ManCon.LOW, selected);
        assertFalse("router must hold, not send HIGH traffic at LOW", s.meetsFloor(selected));
    }

    @Test
    public void setMaxAvailableReportsChange() {
        ManConStatus s = new ManConStatus();
        assertTrue(s.setMaxAvailable(ManCon.HIGH));
        assertFalse("no change -> false (no listener fire)", s.setMaxAvailable(ManCon.HIGH));
        assertTrue(s.setMaxAvailable(ManCon.VERYHIGH));
    }

    @Test
    public void maxAvailableForReadyNetworks() {
        assertEquals(ManCon.NONE, ManConNetworks.maxAvailableFor(Collections.emptyList()));
        assertEquals(ManCon.LOW, ManConNetworks.maxAvailableFor(Arrays.asList(Network.HTTP)));
        assertEquals(ManCon.HIGH, ManConNetworks.maxAvailableFor(Arrays.asList(Network.Tor, Network.HTTP)));
        assertEquals(ManCon.VERYHIGH, ManConNetworks.maxAvailableFor(Arrays.asList(Network.I2P, Network.Tor)));
        assertEquals("non-internet reaches every level",
                ManCon.NEO, ManConNetworks.maxAvailableFor(Arrays.asList(Network.Bluetooth)));
    }

    @Test
    public void defaultForJurisdiction() {
        // from jurisdictions-levels.txt (RSF World Press Freedom Index 2026)
        assertEquals(ManCon.LOW, ManConStatus.defaultFor("NO"));        // Norway - Good
        assertEquals("case-insensitive", ManCon.MEDIUM, ManConStatus.defaultFor("gb")); // UK - Satisfactory
        assertEquals(ManCon.HIGH, ManConStatus.defaultFor("US"));       // USA - Problematic
        assertEquals(ManCon.VERYHIGH, ManConStatus.defaultFor("ZW"));   // Zimbabwe - Difficult
        assertEquals(ManCon.EXTREME, ManConStatus.defaultFor("CN"));    // China - Very serious
        assertEquals(ManCon.HIGH, ManConStatus.defaultFor("KN"));       // OECS member
    }

    @Test
    public void defaultForFallsBackToHigh() {
        assertEquals(ManCon.HIGH, ManConStatus.defaultFor("ZZ"));   // not a jurisdiction -> the "*" line
        assertEquals(ManCon.HIGH, ManConStatus.defaultFor(null));
        assertEquals(ManCon.HIGH, ManConStatus.defaultFor("  "));
    }

    @Test
    public void applyJurisdictionSetsTheFloor() {
        ManConStatus s = new ManConStatus();
        s.applyJurisdiction("cn");
        assertEquals(ManCon.EXTREME, s.getMinRequired());
        s.applyJurisdiction("NO");
        assertEquals(ManCon.LOW, s.getMinRequired());
    }
}
