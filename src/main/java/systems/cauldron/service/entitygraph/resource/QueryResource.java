package systems.cauldron.service.entitygraph.resource;

import io.helidon.webserver.ServerRequest;
import org.apache.jena.query.QueryException;
import systems.cauldron.service.entitygraph.gateway.QueryGraphGateway;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/query")
public class QueryResource {

    private static Logger logger = Logger.getGlobal();

    @Inject
    private ServerRequest request;

    private QueryGraphGateway gateway;

    @PostConstruct
    public void initialize() {
        String graphEndpointUrl = request.context()
                .get("graphEndpointUrl", String.class)
                .orElseThrow();
        gateway = new QueryGraphGateway(graphEndpointUrl);
    }

    @POST
    @Consumes("application/sparql-query")
    @Produces("application/sparql-results+json")
    public Response query(String queryString) {
        StreamingOutput streamingOutput = output -> {
            try {
                gateway.query(queryString, output);
            } catch (QueryException | IllegalArgumentException ex) {
                logger.log(Level.WARNING, "provided query string is not a SPARQL SELECT query");
                throw new WebApplicationException(Response.Status.BAD_REQUEST);
            }
        };
        return Response.ok(streamingOutput).build();
    }

}
