package com.phoenix.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelServiceStateTest {
    @Test fun wireGuardPeerTrafficRequiresBothDirections() {
        assertFalse(TunnelService.hasWireGuardPeerTraffic(0, 0))
        assertFalse(TunnelService.hasWireGuardPeerTraffic(512, 0))
        assertFalse(TunnelService.hasWireGuardPeerTraffic(0, 256))
        assertTrue(TunnelService.hasWireGuardPeerTraffic(512, 256))
    }

    @Test fun onlyConnectedStateMapsToConnected() {
        assertTrue(TunnelService.isUiStateConnected(TunnelService.STATE_CONNECTED))
        assertFalse(TunnelService.isUiStateConnected(TunnelService.STATE_CONNECTING))
        assertFalse(TunnelService.isUiStateConnected(TunnelService.STATE_WAITING_HANDSHAKE))
        assertFalse(TunnelService.isUiStateConnected(TunnelService.STATE_DISCONNECTING))
        assertFalse(TunnelService.isUiStateConnected(TunnelService.STATE_DISCONNECTED))
    }
}
