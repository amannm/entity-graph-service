open module systems.cauldron.service.graphql {
    requires io.helidon.webserver;
    requires io.helidon.media.jsonp.server;
    requires io.helidon.webserver.jersey;
    requires io.helidon.health;
    requires io.helidon.health.checks;
    requires java.json;
    requires java.logging;
    requires org.apache.jena.core;
    requires org.apache.jena.arq;
    requires org.apache.jena.rdfconnection;
    exports systems.cauldron.service.entitygraph;
}