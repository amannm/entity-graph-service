package systems.cauldron.service.entitygraph.resource;

import io.helidon.webserver.ServerRequest;
import systems.cauldron.service.entitygraph.gateway.UserGraphGateway;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/users")
public class UserResource extends EntityResource {

    @Inject
    private ServerRequest request;

    @PostConstruct
    public void initialize() {
        String graphEndpointUrl = request.context()
                .get("graphEndpointUrl", String.class)
                .orElseThrow();
        gateway = new UserGraphGateway(BASE_URI, graphEndpointUrl);
        entityIdKey = UserGraphGateway.ENTITY_TYPE + "Id";
        entityRootPath = UserGraphGateway.ENTITY_TYPE + "s/";
    }

    @POST
    @Consumes("application/json")
    public Response post(String jsonObjectString) {
        return super.post(jsonObjectString);
    }

    @PUT
    @Path("{id}")
    @Consumes("application/json")
    public Response put(@PathParam("id") String id, String jsonObjectString) {
        return super.put(id, jsonObjectString);
    }

    @GET
    @Produces("application/json")
    @Path("{id}")
    public Response get(@PathParam("id") String id) {
        return super.get(id);
    }

    @GET
    @Produces("application/json")
    public Response list() {
        return super.list();
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        return super.delete(id);
    }

}
