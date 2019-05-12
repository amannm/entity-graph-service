package systems.cauldron.service.entitygraph.gateway;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import systems.cauldron.service.entitygraph.gateway.util.EntityJsonBuilder;
import systems.cauldron.service.entitygraph.gateway.util.EntityModelBuilder;

import javax.json.JsonObject;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TripGraphGateway extends EntityGraphGateway {

    public static final String ENTITY_TYPE = "trip";

    public TripGraphGateway(String baseUri, String graphEndpointUrl) {
        super(graphEndpointUrl, baseUri, ENTITY_TYPE, Stream.of("userId", "departureDateTime").collect(Collectors.toSet()));
    }

    @Override
    protected Model buildModel(String id, JsonObject jsonObject) {
        EntityModelBuilder builder = new EntityModelBuilder(baseUri, ENTITY_TYPE, id, jsonObject);
        builder.addObjectProperty("userId", "user")
                .addObjectProperty("origin", "place")
                .addObjectProperty("destination", "place")
                .addTimestampProperty("departureDateTime")
                .addTimestampProperty("arrivalDateTime")
                .addStringProperty("purpose");
        return builder.build();
    }

    @Override
    protected JsonObject buildJson(String id, Map<String, RDFNode> resultMap) {
        EntityJsonBuilder builder = new EntityJsonBuilder(baseUri, ENTITY_TYPE, id, resultMap);
        builder.addObjectProperty("userId", "user")
                .addObjectProperty("origin", "place")
                .addObjectProperty("destination", "place")
                .addStringProperty("departureDateTime")
                .addStringProperty("arrivalDateTime")
                .addStringProperty("purpose");
        return builder.build();
    }

}
