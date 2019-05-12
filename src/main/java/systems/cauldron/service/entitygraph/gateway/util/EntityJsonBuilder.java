package systems.cauldron.service.entitygraph.gateway.util;

import org.apache.jena.rdf.model.RDFNode;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.Collections;
import java.util.Map;

public class EntityJsonBuilder {

    private static final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(Collections.emptyMap());

    private final String entityBaseUri;
    private final JsonObjectBuilder objectBuilder;
    private final Map<String, RDFNode> resultMap;

    public EntityJsonBuilder(String entityBaseUri, String entityType, String entityId, Map<String, RDFNode> resultMap) {
        this.entityBaseUri = entityBaseUri;
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
            objectBuilder.add(propertyName, resultMap.get(propertyName).asResource().getURI().replace(entityBaseUri + objectType + "s/", ""));
        }
        return this;
    }

    public JsonObject build() {
        return objectBuilder.build();
    }

}
