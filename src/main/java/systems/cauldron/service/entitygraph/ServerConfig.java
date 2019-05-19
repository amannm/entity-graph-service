package systems.cauldron.service.entitygraph;

import java.net.InetAddress;

public class ServerConfig {

    private InetAddress serverAddress;
    private int serverPort;
    private String databaseAddress;
    private int databasePort;
    private int healthPort;


    public String getServerUrl() {
        return "http://" + serverAddress.getHostAddress() + ":" + serverPort;
    }

    public String getApiUrl() {
        return "http://" + serverAddress.getHostAddress() + ":" + serverPort + "/graph";
    }

    public String getDatabaseUrl() {
        return "http://" + databaseAddress + ":" + databasePort + "/dataset";
    }

    public String getReadinessUrl() {
        return "http://" + serverAddress.getHostAddress() + ":" + healthPort + "/ready";
    }

    public String getLivenessUrl() {
        return "http://" + serverAddress.getHostAddress() + ":" + healthPort + "/live";
    }

    public InetAddress getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(InetAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getDatabaseAddress() {
        return databaseAddress;
    }

    public void setDatabaseAddress(String databaseAddress) {
        this.databaseAddress = databaseAddress;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public int getHealthPort() {
        return healthPort;
    }

    public void setHealthPort(int healthPort) {
        this.healthPort = healthPort;
    }

}
