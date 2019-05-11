package systems.cauldron.service.entitygraph.gateway;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import systems.cauldron.service.entitygraph.gateway.util.EntityJsonBuilder;
import systems.cauldron.service.entitygraph.gateway.util.EntityModelBuilder;

import javax.json.JsonObject;
import java.util.Map;

public class PlaceGraphGateway extends EntityGraphGateway {

    public static final String ENTITY_TYPE = "place";

    public PlaceGraphGateway(String baseUri, String graphEndpointUrl) {
        super(graphEndpointUrl, baseUri, ENTITY_TYPE);
    }

    @Override
    protected Model buildModel(String id, JsonObject jsonObject) {
        EntityModelBuilder builder = new EntityModelBuilder(baseUri, ENTITY_TYPE, id, jsonObject);
        builder.addStringProperty("name")
                .addIntegerProperty("zipcode")
                .addStringProperty("city")
                .addStringProperty("state")
                .addStringProperty("county")
                .addStringProperty("country");
        return builder.build();
    }

    @Override
    protected JsonObject buildJson(String id, Map<String, RDFNode> resultMap) {
        EntityJsonBuilder builder = new EntityJsonBuilder(baseUri, ENTITY_TYPE, id, resultMap);
        builder.addStringProperty("name")
                .addIntegerProperty("zipcode")
                .addStringProperty("city")
                .addStringProperty("state")
                .addStringProperty("county")
                .addStringProperty("country");
        return builder.build();
    }

}
