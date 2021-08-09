package net.sharksystem.hub;

import net.sharksystem.hub.protocol.*;

import java.io.InputStream;
import java.io.OutputStream;

public class MultipleChannelsConnectorImpl extends ConnectorImpl {
    public MultipleChannelsConnectorImpl(InputStream is, OutputStream os) throws ASAPHubException {
        super(is, os);
    }

    @Override
    public void silentRQ(HubPDUSilentRQ pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void silentRPLY(HubPDUSilentRPLY pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void channelClear(HubPDUChannelClear pdu) {
        this.pduNotHandled(pdu);
    }

    @Override
    public void register(HubPDURegister pdu) {

    }

    @Override
    public void connectPeerRQ(HubPDUConnectPeerRQ pdu) {

    }

    @Override
    public void hubStatusRQ(HubPDUHubStatusRQ pdu) {

    }

    @Override
    public void hubStatusRPLY(HubPDUHubStatusRPLY pdu) {

    }

    @Override
    public boolean isHubSide() {
        return false;
    }

    @Override
    public void unregister(HubPDUUnregister hubPDU) {

    }

    @Override
    protected void resumedConnectorProtocol() {

    }

    @Override
    public CharSequence getPeerID() {
        return null;
    }
}
