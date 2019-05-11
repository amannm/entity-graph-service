package systems.cauldron.service.entitygraph.resource;

import io.helidon.webserver.ServerRequest;
import systems.cauldron.service.entitygraph.gateway.TripGraphGateway;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/trips")
public class TripResource extends EntityResource {

    @Inject
    private ServerRequest request;

    @PostConstruct
    public void initialize() {
        String graphEndpointUrl = request.context()
                .get("graphEndpointUrl", String.class)
                .orElseThrow();
        gateway = new TripGraphGateway(BASE_URI, graphEndpointUrl);
        entityIdKey = TripGraphGateway.ENTITY_TYPE + "Id";
        entityRootPath = TripGraphGateway.ENTITY_TYPE + "s/";
    }

    @POST
    @Consumes("application/json")
    public Response post(String jsonObjectString) {
        return super.post(jsonObjectString);
    }

    @GET
    @Produces("application/json")
    @Path("{id}")
    public Response get(@PathParam("id") String id) {
        return super.get(id);
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        return super.delete(id);
    }

}
