package systems.cauldron.service.entitygraph;

import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;
import systems.cauldron.service.entitygraph.resource.PlaceResource;
import systems.cauldron.service.entitygraph.resource.QueryResource;
import systems.cauldron.service.entitygraph.resource.TripResource;
import systems.cauldron.service.entitygraph.resource.UserResource;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Server {

    public static void main(final String[] args) throws IOException {
        start("fuseki-docker");
    }

    static WebServer start(String databaseHostname) throws IOException {
        Logger logger = setupLogger();
        InetAddress localHost = InetAddress.getLocalHost();
        WebServer server = WebServer.create(getConfig(localHost), getRouting());
        server.context()
                .register("graphEndpointUrl", getGraphEndpointUrl(databaseHostname));
        server.start().thenAccept(ws ->
                logger.log(Level.INFO, "server started @ http://" + localHost.getHostAddress() + ":" + ws.port()));
        server.whenShutdown().thenRun(() ->
                logger.log(Level.INFO, "server stopped"));
        return server;
    }

    private static Logger setupLogger() throws IOException {
        System.setProperty("log4j.configurationFile", "logging.properties");
        LogManager.getLogManager().readConfiguration();
        return Logger.getGlobal();
    }

    private static String getGraphEndpointUrl(String databaseHostname) {
        return "http://" + databaseHostname + ":3030/dataset";
    }

    private static ServerConfiguration getConfig(InetAddress address) {
        return ServerConfiguration.builder()
                .bindAddress(address)
                .port(8080)
                .build();
    }

    private static Routing getRouting() {
        return Routing.builder()
                .register(JsonSupport.create())
                .register("/api", JerseySupport.builder()
                        .register(UserResource.class)
                        .register(PlaceResource.class)
                        .register(TripResource.class)
                        .register(QueryResource.class)
                )
                .build();
    }

}
