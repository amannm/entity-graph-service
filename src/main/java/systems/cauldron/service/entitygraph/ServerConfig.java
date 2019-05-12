package systems.cauldron.service.entitygraph;

import java.net.InetAddress;

public class ServerConfig {
    private InetAddress serverAddress;
    private int serverPort;
    private String databaseAddress;
    private int databasePort;

    public String getServerUrl() {
        return "http://" + serverAddress.getHostAddress() + ":" + serverPort;
    }

    public String getDatabaseUrl() {
        return "http://" + databaseAddress + ":" + databasePort + "/dataset";
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
}
