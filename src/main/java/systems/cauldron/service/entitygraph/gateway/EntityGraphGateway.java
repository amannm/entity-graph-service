package systems.cauldron.service.entitygraph.gateway;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.system.Txn;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class EntityGraphGateway {

    private final String endpointUrl;

    protected final String baseUri;
    private final String entityBaseUri;
    private final String entityTypeUri;

    public EntityGraphGateway(String endpointUrl, String baseUri, String entityType) {
        this.endpointUrl = endpointUrl;
        this.baseUri = baseUri;
        this.entityTypeUri = baseUri + entityType;
        this.entityBaseUri = baseUri + entityType + "s/";
    }

    public boolean create(String entityId, JsonObject jsonObject) {
        boolean conflict;
        String entityUri = entityBaseUri + entityId;
        Model model = buildModel(entityId, jsonObject);
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            conn.begin(ReadWrite.WRITE);
            try {
                String queryString = String.format("ASK { <%s> ?p ?o }", entityUri);
                conflict = conn.queryAsk(queryString);
                if (!conflict) {
                    conn.load(model);
                }
                conn.commit();
            } finally {
                conn.end();
            }
        }
        model.close();
        return !conflict;
    }

    public Optional<JsonObject> read(String entityId) {
        String entityUri = entityBaseUri + entityId;
        Map<String, RDFNode> resultMap = new HashMap<>();
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeRead(conn, () -> {
                String queryString = String.format("SELECT ?p ?o WHERE { <%s> ?p ?o }", entityUri);
                conn.querySelect(queryString, qs -> {
                    String p = qs.get("p").asResource().getURI().replace(baseUri, "");
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

    public JsonArray read() {
        Map<String, Map<String, RDFNode>> resultMap = new HashMap<>();
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeRead(conn, () -> {
                //TODO: figure out the correct SPARQL statement to filter things on the database instead of here
                String queryString = String.format("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");
                conn.querySelect(queryString, qs -> {
                    String s = qs.get("s").asResource().getURI();
                    if (s.startsWith(entityTypeUri)) {
                        s = s.replace(entityTypeUri + "s/", "");
                        Map<String, RDFNode> entityResultMap = resultMap.computeIfAbsent(s, x -> new HashMap<>());
                        String p = qs.get("p").asResource().getURI().replace(baseUri, "");
                        RDFNode o = qs.get("o");
                        entityResultMap.put(p, o);
                    }
                });
            });
        }
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        resultMap.keySet().stream().map(id -> buildJson(id, resultMap.get(id))).forEach(arrayBuilder::add);
        return arrayBuilder.build();
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
