package net.sharksystem.hub;

public interface NewHubConnectionListenerManagement {
    void addNewConnectedHubListener(NewHubConnectedListener connectedHubListener);
    void removeNewConnectedHubListener(NewHubConnectedListener connectedHubListener);
}
