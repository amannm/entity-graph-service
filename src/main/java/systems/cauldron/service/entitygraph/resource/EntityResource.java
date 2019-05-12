package systems.cauldron.service.entitygraph.resource;

import systems.cauldron.service.entitygraph.gateway.EntityGraphGateway;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.net.URI;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;

public abstract class EntityResource {

    private static Logger logger = Logger.getGlobal();

    protected static final String BASE_URI = "http://cauldron.systems/graph/";

    protected EntityGraphGateway gateway;
    protected String entityIdKey;
    protected String entityRootPath;

    public Response post(String jsonObjectString) {
        JsonObject jsonObject;
        try (JsonReader reader = Json.createReader(new StringReader(jsonObjectString))) {
            jsonObject = reader.readObject();
        } catch (Exception ex) {
            return status(Status.BAD_REQUEST).build();
        }
        String id = jsonObject.getString(entityIdKey);
        try {
            if (gateway.create(id, jsonObject)) {
                return created(URI.create(BASE_URI + entityRootPath + id)).build();
            } else {
                return status(Status.CONFLICT).build();
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "exception while creating entity", ex);
            return serverError().build();
        }
    }

    public Response put(String id, String jsonObjectString) {
        JsonObject jsonObject;
        try (JsonReader reader = Json.createReader(new StringReader(jsonObjectString))) {
            jsonObject = reader.readObject();
        } catch (Exception ex) {
            return status(Status.BAD_REQUEST).build();
        }
        try {
            if (gateway.createOrUpdate(id, jsonObject)) {
                return created(URI.create(BASE_URI + entityRootPath + id)).build();
            } else {
                return status(Status.OK).build();
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "exception while creating or updating entity", ex);
            return serverError().build();
        }
    }

    public Response get(String id) {
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
            return status(Status.NOT_FOUND).build();
        }
    }

    public Response list() {
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
            return status(Status.NOT_FOUND).build();
        }
    }

    public Response delete(String id) {
        try {
            gateway.delete(id);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "exception while deleting entity", ex);
            return serverError().build();
        }
        return noContent().build();
    }

}
