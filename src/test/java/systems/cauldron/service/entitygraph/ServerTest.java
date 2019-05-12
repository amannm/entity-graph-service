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
        String apiRoot = getApiEndpoint() + "/users";
        List<JsonObject> objects = loadObjects("/users.json");
        for (JsonObject object : objects) {
            assertCrudability(apiRoot, object.getString("userId"), object);
        }
        loadEntities("users");
        List<JsonObject> jsonObjects = loadObjects("/users.json");
        String queryEndpoint = getApiEndpoint() + "/users";
        assertEntityListing(queryEndpoint, "userId", jsonObjects);
    }

    @Test
    public void testPlaceEntity() throws Exception {
        String apiRoot = getApiEndpoint() + "/places";
        List<JsonObject> objects = loadObjects("/places.json");
        for (JsonObject object : objects) {
            assertCrudability(apiRoot, object.getString("placeId"), object);
        }
        loadEntities("places");
        List<JsonObject> jsonObjects = loadObjects("/places.json");
        String queryEndpoint = getApiEndpoint() + "/places";
        assertEntityListing(queryEndpoint, "placeId", jsonObjects);
    }

    @Test
    public void testTripEntity() throws Exception {
        String apiRoot = getApiEndpoint() + "/trips";
        List<JsonObject> objects = loadObjects("/trips.json");
        for (JsonObject object : objects) {
            assertCrudability(apiRoot, object.getString("tripId"), object);
        }
        loadEntities("trips");
        List<JsonObject> jsonObjects = loadObjects("/trips.json");
        String queryEndpoint = getApiEndpoint() + "/trips";
        assertEntityListing(queryEndpoint, "tripId", jsonObjects);
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

    private void assertCrudability(String entityRoot, String entityId, JsonObject entity) throws IOException {
        String testLocation = entityRoot + "/" + entityId;
        createEntity(entityRoot, entity);
        assertEntityExists(testLocation, entity);
        deleteEntity(testLocation);
        assertEntityNotExists(testLocation);
    }

    public static void createEntity(String urlString, JsonObject object) throws IOException {
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
        int responseCode = connection.getResponseCode();
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
            Assertions.assertEquals(actual, expected);
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
