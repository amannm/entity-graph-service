package systems.cauldron.service.entitygraph;

import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;
import org.eclipse.microprofile.health.HealthCheckResponse;
import systems.cauldron.service.entitygraph.resource.CorsFilter;
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
        ServerConfig config = new ServerConfig();
        config.setServerAddress(InetAddress.getLocalHost());
        config.setServerPort(8080);
        config.setDatabaseAddress("fuseki-docker");
        config.setDatabasePort(3030);
        config.setHealthPort(8081);
        start(config);
    }

    static WebServer start(ServerConfig config) throws IOException {
        Logger logger = setupLogger();
        WebServer server = WebServer.builder(getRouting())
                .config(getConfig(config))
                .addNamedRouting("health", getHealthRouting())
                .build();
        server.context().register("databaseUrl", config.getDatabaseUrl());
        server.start().thenAccept(ws -> logger.log(Level.INFO, "server started @ " + config.getServerUrl()));
        server.whenShutdown().thenRun(() -> logger.log(Level.INFO, "server stopped"));
        return server;
    }

    private static Logger setupLogger() throws IOException {
        System.setProperty("log4j.configurationFile", "logging.properties");
        LogManager.getLogManager().readConfiguration();
        return Logger.getGlobal();
    }

    private static ServerConfiguration getConfig(ServerConfig config) {
        return ServerConfiguration.builder()
                .bindAddress(config.getServerAddress())
                .port(config.getServerPort())
                .addSocket("health", SocketConfiguration.builder().port(config.getHealthPort()).build())
                .build();
    }

    private static Routing getHealthRouting() {
        //TODO: implement a true database connectivity check
        return Routing.builder()
                .register(JsonSupport.create())
                .register(HealthSupport.builder()
                        .webContext("/live")
                        .add(HealthChecks.healthChecks())
                        .build())
                .register(HealthSupport.builder()
                        .webContext("/ready")
                        .add(() -> HealthCheckResponse.named("database").up().build())
                        .build())
                .build();
    }

    private static Routing getRouting() {
        return Routing.builder()
                .register(JsonSupport.create())
                .register("/graph", JerseySupport.builder()
                        .register(CorsFilter.class)
                        .register(UserResource.class)
                        .register(PlaceResource.class)
                        .register(TripResource.class)
                        .register(QueryResource.class)
                )
                .build();
    }

}
