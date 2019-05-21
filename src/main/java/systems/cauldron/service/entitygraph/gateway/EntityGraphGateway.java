package systems.cauldron.service.entitygraph.gateway;

import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.sparql.util.FmtUtils;
import org.apache.jena.system.Txn;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import systems.cauldron.service.entitygraph.resource.EntityResourceFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class EntityGraphGateway {

    private final String endpointUrl;

    public static final String NAMESPACE_PREFIX = "http://cauldron.systems/graph#";

    private final String entityRootPath;
    private final String entityTypeUri;
    private final String filterConditions;

    public EntityGraphGateway(String endpointUrl, String entityType, Set<String> summaryFields) {
        this.endpointUrl = endpointUrl;
        this.entityTypeUri = NAMESPACE_PREFIX + entityType;
        this.entityRootPath = EntityResourceFactory.getEntityRootPath(entityType);
        this.filterConditions = summaryFields.stream()
                .map(fieldName -> String.format("?p = <%s%s>", NAMESPACE_PREFIX, fieldName))
                .collect(Collectors.joining(" || "));
    }

    public boolean create(String entityId, JsonObject jsonObject) {
        boolean conflict;
        String entityPath = entityRootPath + entityId;
        Model model = buildModel(entityId, jsonObject);
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            conn.begin(ReadWrite.WRITE);
            try {
                String queryString = String.format("ASK { <%s> ?p ?o }", entityPath);
                conflict = conn.queryAsk(queryString);
                if (!conflict) {
                    conn.load(model);
                } else {
                    conn.abort();
                }
                conn.commit();
            } finally {
                conn.end();
            }
        }
        model.close();
        return !conflict;
    }

    public boolean createOrUpdate(String entityId, JsonObject jsonObject) {
        boolean isUpdate;
        String entityPath = entityRootPath + entityId;
        Model model = buildModel(entityId, jsonObject);
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            conn.begin(ReadWrite.WRITE);
            try {
                String queryString = String.format("ASK { <%s> ?p ?o }", entityPath);
                isUpdate = conn.queryAsk(queryString);
                if (isUpdate) {
                    String updateString = String.format("DELETE WHERE { <%s> ?p ?o }", entityPath);
                    UpdateRequest updateRequest = UpdateFactory.create(updateString);
                    conn.update(updateRequest);
                }
                conn.load(model);
                conn.commit();
            } finally {
                conn.end();
            }
        }
        model.close();
        return !isUpdate;
    }

    public boolean update(String entityId, JsonObject jsonObject) {
        boolean patchable;
        String entityPath = entityRootPath + entityId;
        Model model = buildModel(entityId, jsonObject);
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            conn.begin(ReadWrite.WRITE);
            try {
                String queryString = String.format("ASK { <%s> ?p ?o }", entityPath);
                patchable = conn.queryAsk(queryString);
                if (patchable) {
                    model.listStatements().forEachRemaining(statement -> {
                        String predicate = FmtUtils.stringForRDFNode(statement.getPredicate());
                        String object = FmtUtils.stringForRDFNode(statement.getObject());
                        String updateString = String.format("DELETE { ?s ?p ?o } INSERT { ?s ?p %s } WHERE { ?s ?p ?o . <%s> %s ?o }", object, entityPath, predicate);
                        UpdateRequest updateRequest = UpdateFactory.create(updateString);
                        conn.update(updateRequest);
                    });
                    conn.commit();
                } else {
                    conn.abort();
                }
            } finally {
                conn.end();
            }
        }
        model.close();
        return patchable;
    }

    public Optional<JsonObject> read(String entityId) {
        String entityPath = entityRootPath + entityId;
        Map<String, RDFNode> resultMap = new HashMap<>();
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeRead(conn, () -> {
                String queryString = String.format("SELECT ?p ?o WHERE { <%s> ?p ?o }", entityPath);
                conn.querySelect(queryString, qs -> {
                    String p = getPropertyLocalName(qs.get("p"));
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

    public JsonArray list() {
        Map<String, Map<String, RDFNode>> resultMap = new HashMap<>();
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeRead(conn, () -> {
                String queryString = String.format("SELECT ?s ?p ?o WHERE { ?s ?p ?o . ?s a <%s> . FILTER ( %s ) }", entityTypeUri, filterConditions);
                conn.querySelect(queryString, qs -> {
                    String s = getResourceLocalName(qs.get("s"));
                    Map<String, RDFNode> entityPropertyMap = resultMap.computeIfAbsent(s, x -> new HashMap<>());
                    String p = getPropertyLocalName(qs.get("p"));
                    RDFNode o = qs.get("o");
                    entityPropertyMap.put(p, o);
                });
            });
        }
        JsonArrayBuilder results = Json.createArrayBuilder();
        resultMap.entrySet().stream()
                .map(e -> buildJson(e.getKey(), e.getValue()))
                .forEach(results::add);
        return results.build();
    }

    public void delete(String entityId) {
        String entityPath = entityRootPath + entityId;
        try (RDFConnection conn = RDFConnectionFactory.connect(endpointUrl)) {
            Txn.executeWrite(conn, () -> {
                String updateString = String.format("DELETE WHERE { <%s> ?p ?o }", entityPath);
                UpdateRequest updateRequest = UpdateFactory.create(updateString);
                conn.update(updateRequest);
            });
        }
    }

    protected abstract Model buildModel(String id, JsonObject jsonObject);

    protected abstract JsonObject buildJson(String id, Map<String, RDFNode> resultMap);

    public abstract String getEntityType();

    private String getResourceLocalName(RDFNode node) {
        return node.asResource().getURI().replace(entityRootPath, "");
    }

    private String getPropertyLocalName(RDFNode node) {
        return node.asResource().getURI().replace(NAMESPACE_PREFIX, "");
    }

}
