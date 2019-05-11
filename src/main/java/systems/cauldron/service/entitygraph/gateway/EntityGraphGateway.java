package systems.cauldron.service.entitygraph.gateway;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.system.Txn;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;

import javax.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class EntityGraphGateway {

    private final String endpointUrl;

    protected final String baseUri;
    private final String entityBaseUri;

    public EntityGraphGateway(String endpointUrl, String baseUri, String entityType) {
        this.endpointUrl = endpointUrl;
        this.baseUri = baseUri;
        this.entityBaseUri = baseUri + entityType + "s/";
    }

    public void create(String entityId, JsonObject jsonObject) {
        Model model = buildModel(entityId, jsonObject);
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeWrite(conn, () -> conn.load(model));
        }
        model.close();
    }

    public Optional<JsonObject> read(String entityId) {
        String entityUri = entityBaseUri + entityId;
        Map<String, RDFNode> resultMap = new HashMap<>();
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeRead(conn, () -> {
                String queryString = String.format("SELECT ?p ?o WHERE { <%s> ?p ?o }", entityUri);
                conn.querySelect(queryString, qs -> {
                    //TODO: ensure property names not starting with a letter are parsed properly here
                    String p = qs.get("p").asResource().getLocalName();
                    RDFNode o = qs.get("o");
                    resultMap.put(p, o);
                });
            });
        }
        if (resultMap.isEmpty()) {
            return Optional.empty();
        } else {
            JsonObject jsonObject = buildJson(entityId, resultMap);
            return Optional.of(jsonObject);
        }
    }

    public void delete(String entityId) {
        String entityUri = entityBaseUri + entityId;
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeWrite(conn, () -> {
                String updateString = String.format("DELETE WHERE { <%s> ?p ?o }", entityUri);
                UpdateRequest updateRequest = UpdateFactory.create(updateString);
                conn.update(updateRequest);
            });
        }
    }

    protected abstract Model buildModel(String id, JsonObject jsonObject);

    protected abstract JsonObject buildJson(String id, Map<String, RDFNode> resultMap);

}
