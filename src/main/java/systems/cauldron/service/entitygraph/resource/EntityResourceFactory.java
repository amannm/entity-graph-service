package systems.cauldron.service.entitygraph.resource;

import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.model.Resource;
import systems.cauldron.service.entitygraph.gateway.EntityGraphGateway;
import systems.cauldron.service.entitygraph.gateway.PlaceGraphGateway;
import systems.cauldron.service.entitygraph.gateway.TripGraphGateway;
import systems.cauldron.service.entitygraph.gateway.UserGraphGateway;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;

public class EntityResourceFactory {

    private static final String ROOT_PATH = "http://api.cauldron.systems/graph/";

    public static String getEntityPath(String entityType, String entityId) {
        return ROOT_PATH + entityType + "s/" + entityId;
    }

    public static String getEntityRootPath(String entityType) {
        return ROOT_PATH + entityType + "s/";
    }

    public static Resource[] getResources(Logger logger, String graphEndpointUrl) {
        return new Resource[]{
                create(new UserGraphGateway(graphEndpointUrl), logger),
                create(new PlaceGraphGateway(graphEndpointUrl), logger),
                create(new TripGraphGateway(graphEndpointUrl), logger)
        };
    }

    public static Resource create(EntityGraphGateway gateway, Logger logger) {

        final String entityIdKey = gateway.getEntityType() + "Id";
        final String entityRootPath = gateway.getEntityType() + "s/";

        final Resource.Builder resourceBuilder = Resource.builder("/" + gateway.getEntityType() + "s");

        Inflector<ContainerRequestContext, Response> noContentResponder = new Inflector<ContainerRequestContext, Response>() {

            @Override
            public Response apply(ContainerRequestContext data) {
                return Response.noContent().build();
            }
        };

        resourceBuilder.addMethod("HEAD").handledBy(noContentResponder);

        resourceBuilder.addMethod("OPTIONS").handledBy(noContentResponder);

        resourceBuilder.addMethod("GET")
                .produces(MediaType.APPLICATION_JSON_TYPE)
                .handledBy(new Inflector<ContainerRequestContext, Response>() {

                    @Override
                    public Response apply(ContainerRequestContext data) {
                        JsonArray result;
                        try {
                            result = gateway.list();
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "exception while retrieving entity", ex);
                            return serverError().build();
                        }
                        if (!result.isEmpty()) {
                            return ok(result.toString()).build();
                        } else {
                            return status(Response.Status.NOT_FOUND).build();
                        }
                    }
                });

        resourceBuilder
                .addChildResource("{id}")
                .addMethod("GET")
                .produces(MediaType.APPLICATION_JSON_TYPE)
                .handledBy(new Inflector<ContainerRequestContext, Response>() {

                    @Override
                    public Response apply(ContainerRequestContext data) {

                        Optional<String> idParam = getId(data);
                        if (idParam.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        String id = idParam.get();

                        Optional<JsonObject> result;
                        try {
                            result = gateway.read(id);
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "exception while retrieving entity", ex);
                            return serverError().build();
                        }
                        if (result.isPresent()) {
                            JsonObject jsonObject = result.get();
                            return ok(jsonObject.toString()).build();
                        } else {
                            return status(Response.Status.NOT_FOUND).build();
                        }

                    }
                });

        resourceBuilder
                .addMethod("POST")
                .consumes(MediaType.APPLICATION_JSON_TYPE)
                .handledBy(new Inflector<ContainerRequestContext, Response>() {

                    @Override
                    public Response apply(ContainerRequestContext data) {

                        Optional<JsonObject> objectResult = getEntity(data);
                        if (objectResult.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        JsonObject jsonObject = objectResult.get();

                        //TODO: additional validation?
                        if (!jsonObject.containsKey(entityIdKey)) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }

                        String id = jsonObject.getString(entityIdKey);

                        try {
                            if (gateway.create(id, jsonObject)) {
                                return created(URI.create(ROOT_PATH + entityRootPath + id)).build();
                            } else {
                                return status(Response.Status.CONFLICT).build();
                            }
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "exception while creating entity", ex);
                            return serverError().build();
                        }
                    }
                });

        resourceBuilder
                .addChildResource("{id}")
                .addMethod("PUT")
                .consumes(MediaType.APPLICATION_JSON_TYPE)
                .handledBy(new Inflector<ContainerRequestContext, Response>() {

                    @Override
                    public Response apply(ContainerRequestContext data) {

                        Optional<String> idParam = getId(data);
                        if (idParam.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        String id = idParam.get();

                        Optional<JsonObject> objectResult = getEntity(data);
                        if (objectResult.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        //TODO: additional validation?
                        JsonObject jsonObject = objectResult.get();

                        try {
                            if (gateway.createOrUpdate(id, jsonObject)) {
                                return created(URI.create(ROOT_PATH + entityRootPath + id)).build();
                            } else {
                                return status(Response.Status.OK).build();
                            }
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "exception while creating or updating entity", ex);
                            return serverError().build();
                        }
                    }
                });

        resourceBuilder
                .addChildResource("{id}")
                .addMethod("PATCH")
                .consumes(MediaType.APPLICATION_JSON_TYPE)
                .handledBy(new Inflector<ContainerRequestContext, Response>() {

                    @Override
                    public Response apply(ContainerRequestContext data) {

                        Optional<String> idParam = getId(data);
                        if (idParam.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        String id = idParam.get();

                        Optional<JsonObject> objectResult = getEntity(data);
                        if (objectResult.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        //TODO: additional validation?
                        JsonObject jsonObject = objectResult.get();

                        try {
                            if (gateway.update(id, jsonObject)) {
                                return noContent().build();
                            } else {
                                return status(Response.Status.NOT_FOUND).build();
                            }
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "exception while creating or updating entity", ex);
                            return serverError().build();
                        }
                    }
                });

        resourceBuilder
                .addChildResource("{id}")
                .addMethod("DELETE")
                .handledBy(new Inflector<ContainerRequestContext, Response>() {

                    @Override
                    public Response apply(ContainerRequestContext data) {

                        Optional<String> idParam = getId(data);
                        if (idParam.isEmpty()) {
                            return status(Response.Status.BAD_REQUEST).build();
                        }
                        String id = idParam.get();

                        try {
                            gateway.delete(id);
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "exception while deleting entity", ex);
                            return serverError().build();
                        }
                        return noContent().build();
                    }
                });


        return resourceBuilder.build();


    }

    public static Optional<JsonObject> getEntity(ContainerRequestContext data) {
        try (JsonReader reader = Json.createReader(data.getEntityStream())) {
            return Optional.of(reader.readObject());
        } catch (Exception ex) {
            return Optional.empty();
        }

    }

    public static Optional<String> getId(ContainerRequestContext data) {
        //TODO: is this right?
        MultivaluedMap<String, String> params = data.getUriInfo().getPathParameters();
        List<String> values = params.get("id");
        if (values.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(values.get(0));
    }

}
