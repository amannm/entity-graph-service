package systems.cauldron.service.entitygraph.gateway.util;

import org.apache.jena.rdf.model.RDFNode;
import systems.cauldron.service.entitygraph.resource.EntityResource;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.Collections;
import java.util.Map;

public class EntityJsonBuilder {

    private static final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Collections.emptyMap());

    private final JsonObjectBuilder objectBuilder;
    private final Map<String, RDFNode> resultMap;

    public EntityJsonBuilder(String entityType, String entityId, Map<String, RDFNode> resultMap) {
        this.objectBuilder = jsonFactory.createObjectBuilder();
        this.objectBuilder.add(entityType + "Id", entityId);
        this.resultMap = resultMap;
    }

    public EntityJsonBuilder addStringProperty(String propertyName) {
        if (resultMap.containsKey(propertyName)) {
            objectBuilder.add(propertyName, resultMap.get(propertyName).asLiteral().getString());
        }
        return this;
    }

    public EntityJsonBuilder addIntegerProperty(String propertyName) {
        if (resultMap.containsKey(propertyName)) {
            objectBuilder.add(propertyName, resultMap.get(propertyName).asLiteral().getInt());
        }
        return this;
    }

    public EntityJsonBuilder addObjectProperty(String propertyName, String objectType) {
        if (resultMap.containsKey(propertyName)) {
            objectBuilder.add(propertyName, resultMap.get(propertyName).asResource().getURI().replace(EntityResource.getEntityRootPath(objectType), ""));
        }
        return this;
    }

    public JsonObject build() {
        return objectBuilder.build();
    }

}
