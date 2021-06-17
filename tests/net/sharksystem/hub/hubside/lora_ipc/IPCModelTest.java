package net.sharksystem.hub.hubside.lora_ipc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class IPCModelTest {

    @Test
    public void getIPCMessage() {
        IPCModel connectRequest = new ConnectRequestModel("alice", "bob", 60);
        assertEquals("ConnectRequest,alice,bob,60", connectRequest.getIPCMessage());
    }

    @Test
    public void createConnectRequestModelFromString(){
        ConnectRequestModel connectRequest = (ConnectRequestModel) IPCModel.createModelObjectFromIPCString("ConnectRequest,alice,bob,60");
        assertEquals("alice", connectRequest.getSourcePeerID());
        assertEquals("bob", connectRequest.getTargetPeerID());
        assertEquals(60, connectRequest.getTimeout());
    }

    @Test
    public void getIPCMessageRegistrationModel() {
        IPCModel registrationModel = new RegistrationModel("alice", true);
        assertEquals("Registration,alice,true", registrationModel.getIPCMessage());
    }

    @Test
    public void createConnectRegistrationModelFromStringRegister() {
        RegistrationModel registrationModel = (RegistrationModel) IPCModel.createModelObjectFromIPCString("Registration,alice,true");
        assertEquals("alice", registrationModel.getPeerId());
        assertEquals(true, registrationModel.isRegister());
    }

    @Test
    public void createConnectRegistrationModelFromStringUnregister() {
        RegistrationModel registrationModel = (RegistrationModel) IPCModel.createModelObjectFromIPCString("Registration,alice,false");
        assertEquals("alice", registrationModel.getPeerId());
        assertEquals(false, registrationModel.isRegister());
    }

    @Test
    public void createConnectRegistrationModelFromStringRegisterEdgeUpperCase() {
        RegistrationModel registrationModel = (RegistrationModel) IPCModel.createModelObjectFromIPCString("Registration,alice,True");
        assertEquals("alice", registrationModel.getPeerId());
        assertEquals(true, registrationModel.isRegister());
    }

    @Test
    public void createConnectRegistrationModelFromStringUnregisterEdgeUpperCase() {
        RegistrationModel registrationModel = (RegistrationModel) IPCModel.createModelObjectFromIPCString("Registration,alice,False");
        assertEquals("alice", registrationModel.getPeerId());
        assertEquals(false, registrationModel.isRegister());
    }

    @Test
    public void getIPCMessageRegisteredPeersModel() {
        List<String> registeredPeers = new ArrayList<>(Arrays.asList("alice", "bob"));
        IPCModel registeredPeersModel = new RegisteredPeersModel(registeredPeers);
         assertEquals("RegisteredPeers,alice,bob", registeredPeersModel.getIPCMessage());
    }

    @Test
    public void createRegisteredPeersModelFromString() {
        RegisteredPeersModel registeredPeersModel = (RegisteredPeersModel) IPCModel.createModelObjectFromIPCString
                ("RegisteredPeers,alice,bob");
        assertTrue(registeredPeersModel.getRegisteredPeers().contains("alice"));
        assertTrue(registeredPeersModel.getRegisteredPeers().contains("bob"));
    }

    @Test
    public void createRegisteredPeersModelFromStringEdgeNoPeersRegistered() {
        RegisteredPeersModel registeredPeersModel = (RegisteredPeersModel) IPCModel.createModelObjectFromIPCString
                ("RegisteredPeers");
        assertTrue(registeredPeersModel.getRegisteredPeers().isEmpty());
    }

    @Test
    public void getIPCMessageFromDisconnectRequestModel() {
        IPCModel connectRequest = new DisconnectRequestModel("alice", "bob");
        assertEquals("DisconnectRequest,alice,bob", connectRequest.getIPCMessage());
    }

    @Test
    public void createDisconnectRequestModelObjectFromString() {
        DisconnectRequestModel disconnectRequestModel = (DisconnectRequestModel) IPCModel.createModelObjectFromIPCString
                ("DisconnectRequest,alice,bob");
        assertEquals("alice", disconnectRequestModel.getSourcePeerID());
        assertEquals("bob", disconnectRequestModel.getTargetPeerID());
    }

}