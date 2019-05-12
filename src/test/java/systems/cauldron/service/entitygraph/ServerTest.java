package systems.cauldron.service.entitygraph;

import io.helidon.webserver.WebServer;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServerTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startServer() throws Exception {
        webServer = Server.start();
        while (!webServer.isRunning()) {
            Thread.sleep(1 * 1000);
        }
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private Dataset dataset;
    private FusekiServer fusekiServer;

    @BeforeEach
    public void startGraphStore() {
        dataset = DatasetFactory.createTxnMem();
        fusekiServer = FusekiServer.create()
                .add("/dataset", dataset)
                .build();
        fusekiServer.start();
    }

    @AfterEach
    public void stopGraphStore() {
        if (fusekiServer != null) {
            fusekiServer.stop();
        }
        if (dataset != null) {
            dataset.close();
        }
    }

    @Test
    public void testUserEntity() throws Exception {
        testEntity("user", "name");
    }

    @Test
    public void testPlaceEntity() throws Exception {
        testEntity("place", "name");
    }

    @Test
    public void testTripEntity() throws Exception {
        testEntity("trip", "purpose");
    }

    private void testEntity(String entityType, String modifiableStringPropertyKey) throws IOException {
        String entityIdKey = entityType + "Id";
        String entityTypePlural = entityType + "s";
        String entityEndpointPath = "/" + entityTypePlural;
        String entityResourceFilePath = entityEndpointPath + ".json";
        String apiRoot = getApiEndpoint() + entityEndpointPath;
        List<JsonObject> objects = loadObjects(entityResourceFilePath);
        for (JsonObject object : objects) {
            assertPostability(apiRoot, entityIdKey, object);
            assertPutability(apiRoot, entityIdKey, object, modifiableStringPropertyKey);
        }
        loadEntities(entityTypePlural);
        List<JsonObject> jsonObjects = loadObjects(entityResourceFilePath);
        String queryEndpoint = getApiEndpoint() + entityEndpointPath;
        assertEntityListing(queryEndpoint, entityIdKey, jsonObjects);
    }

    @Test
    public void testListAllQuery() throws Exception {
        loadEntities("users", "places", "trips");
        String queryEndpoint = getApiEndpoint() + "/query";
        String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
        JsonObject jsonObject = executeQuery(queryEndpoint, queryString);
        System.out.println(jsonObject.toString());
        Assertions.assertFalse(jsonObject.getJsonObject("results").getJsonArray("bindings").isEmpty());
    }

    @Test
    public void testConnectivityQuery() throws Exception {
        loadEntities("users", "places", "trips");
        String queryEndpoint = getApiEndpoint() + "/query";
        String queryString = "SELECT DISTINCT ?city WHERE { " +
                "?place <http://cauldron.systems/graph/city> ?city . " +
                "?trip <http://cauldron.systems/graph/destination> ?place . " +
                "?trip <http://cauldron.systems/graph/userId> ?user . " +
                "?user <http://cauldron.systems/graph/name> 'Tony Stark' }";
        JsonObject jsonObject = executeQuery(queryEndpoint, queryString);
        System.out.println(jsonObject.toString());
        Assertions.assertFalse(jsonObject.getJsonObject("results").getJsonArray("bindings").isEmpty());
    }

    private String getApiEndpoint() {
        return "http://" + webServer.configuration().bindAddress().getHostAddress() + ":" + webServer.port() + "/api";
    }

    private void loadEntities(String... resourceNames) {
        for (String resourceName : resourceNames) {
            String entityRoot = getApiEndpoint() + "/" + resourceName;
            loadObjects("/" + resourceName + ".json").forEach(e -> {
                try {
                    createEntity(entityRoot, e);
                } catch (IOException ex) {
                    Assertions.fail(ex);
                }
            });
        }
    }

    private List<JsonObject> loadObjects(String resourceName) {
        try (JsonReader reader = Json.createReader(this.getClass().getResourceAsStream(resourceName))) {
            return reader.readArray().stream().map(v -> (JsonObject) v).collect(Collectors.toList());
        }
    }

    private void assertPostability(String entityRoot, String idKey, JsonObject entity) throws IOException {
        String entityId = entity.getString(idKey);
        String testLocation = entityRoot + "/" + entityId;
        createEntity(entityRoot, entity);
        assertEntityExists(testLocation, entity);
        deleteEntity(testLocation);
        assertEntityNotExists(testLocation);
    }

    private void assertPutability(String entityRoot, String idKey, JsonObject entity, String keyToModify) throws IOException {
        String entityId = entity.getString(idKey);
        String testLocation = entityRoot + "/" + entityId;
        createOrUpdateEntity(testLocation, entity);
        assertEntityExists(testLocation, entity);
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        entity.forEach((k, v) -> {
            if (keyToModify.equals(k)) {
                String modifiedValue = entity.getString(k) + "test";
                objectBuilder.add(k, modifiedValue);
            } else {
                objectBuilder.add(k, v);
            }
        });
        JsonObject modified = objectBuilder.build();
        createOrUpdateEntity(testLocation, modified);
        assertEntityExists(testLocation, modified);
        deleteEntity(testLocation);
        assertEntityNotExists(testLocation);
    }

    public static int createEntity(String urlString, JsonObject object) throws IOException {
        URL url = tryConstructUrl(urlString);
        byte[] body = object.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
        connection.setDoOutput(true);
        try (DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream())) {
            dataOutputStream.write(body);
        }
        return connection.getResponseCode();
    }

    public static int createOrUpdateEntity(String urlString, JsonObject object) throws IOException {
        URL url = tryConstructUrl(urlString);
        byte[] body = object.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
        connection.setDoOutput(true);
        try (DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream())) {
            dataOutputStream.write(body);
        }
        return connection.getResponseCode();
    }

    public static JsonObject executeQuery(String urlString, String queryString) throws IOException {
        URL url = tryConstructUrl(urlString);
        byte[] body = queryString.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/sparql-query");
        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
        connection.setDoOutput(true);
        try (DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream())) {
            dataOutputStream.write(body);
        }
        Assertions.assertEquals(200, connection.getResponseCode());
        try (JsonReader jsonReader = Json.createReader(connection.getInputStream())) {
            return jsonReader.readObject();
        }
    }

    public static void assertEntityExists(String urlString, JsonObject expected) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        Assertions.assertEquals(200, connection.getResponseCode());
        try (JsonReader reader = Json.createReader(connection.getInputStream())) {
            JsonObject actual = reader.readObject();
            Assertions.assertEquals(expected, actual);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void assertEntityNotExists(String urlString) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        Assertions.assertEquals(404, connection.getResponseCode());
    }

    public static void assertEntityListing(String urlString, String idKey, List<JsonObject> expected) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        Assertions.assertEquals(200, connection.getResponseCode());
        try (JsonReader reader = Json.createReader(connection.getInputStream())) {
            Set<JsonObject> actual = reader.readArray().stream().map(JsonValue::asJsonObject).collect(Collectors.toSet());
            HashSet<JsonObject> extraItems = new HashSet<>(actual);
            extraItems.removeAll(expected);
            Assertions.assertEquals(Collections.emptySet(), extraItems, "extra items in returned collection");
            HashSet<JsonObject> missingItems = new HashSet<>(expected);
            missingItems.removeAll(actual);
            Assertions.assertEquals(extractConflictingEntities(idKey, expected), missingItems, "conflicting items in returned collection");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void deleteEntity(String urlString) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("DELETE");
        Assertions.assertEquals(204, connection.getResponseCode());
    }

    private static URL tryConstructUrl(String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<JsonObject> extractConflictingEntities(String idKey, List<JsonObject> objects) {
        Set<JsonObject> results = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (JsonObject object : objects) {
            String key = object.getString(idKey);
            if (ids.contains(key)) {
                results.add(object);
            } else {
                ids.add(key);
            }
        }
        return results;
    }

}
