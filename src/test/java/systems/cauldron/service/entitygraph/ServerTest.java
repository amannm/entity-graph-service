package systems.cauldron.service.entitygraph;

import io.helidon.webserver.WebServer;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public class ServerTest {

    private static WebServer webServer;
    private static ServerConfig config;

    @BeforeAll
    public static void startServer() throws Exception {
        allowMethods("PATCH");
        config = new ServerConfig();
        config.setServerAddress(InetAddress.getLocalHost());
        config.setServerPort(getRandomAvailablePort());
        config.setDatabaseAddress(InetAddress.getLocalHost().getHostAddress());
        config.setDatabasePort(getRandomAvailablePort());
        config.setHealthPort(getRandomAvailablePort());
        webServer = Server.start(config);
        while (!webServer.isRunning()) {
            Thread.sleep(1000);
        }
    }

    private static int getRandomAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
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
                .port(config.getDatabasePort())
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
    public void testHealth() throws Exception {
        assertHealthyOverall(config.getLivenessUrl());
        assertHealthyOverall(config.getReadinessUrl());
    }

    @Test
    public void testUserEntity() throws Exception {
        testEntity("user", "name", Collections.singleton("name"));
    }

    @Test
    public void testPlaceEntity() throws Exception {
        testEntity("place", "name", Collections.singleton("name"));
    }

    @Test
    public void testTripEntity() throws Exception {
        testEntity("trip", "purpose", Stream.of("userId", "departureDateTime").collect(Collectors.toSet()));
    }

    private void testEntity(String entityType, String modifiableStringPropertyKey, Set<String> summaryFields) throws IOException {
        String entityIdKey = entityType + "Id";
        String entityTypePlural = entityType + "s";
        String entityEndpointPath = "/" + entityTypePlural;
        String entityResourceFilePath = entityEndpointPath + ".json";
        String apiRoot = config.getApiUrl() + entityEndpointPath;
        List<JsonObject> objects = loadObjects(entityResourceFilePath);
        for (JsonObject object : objects) {
            assertPostability(apiRoot, entityIdKey, object);
            assertPutability(apiRoot, entityIdKey, object, modifiableStringPropertyKey);
            assertPatchability(apiRoot, entityIdKey, object, modifiableStringPropertyKey);
        }
        loadEntities(entityTypePlural);
        List<JsonObject> jsonObjects = loadSummaryObjects(entityResourceFilePath, entityIdKey, summaryFields);
        assertEntityListing(apiRoot, entityIdKey, jsonObjects);
    }

    @Test
    public void testListAllQuery() throws Exception {
        loadEntities("users", "places", "trips");
        String queryEndpoint = config.getApiUrl() + "/query";
        String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
        JsonObject jsonObject = executeQuery(queryEndpoint, queryString);
        System.out.println(jsonObject.toString());
        assertFalse(jsonObject.getJsonObject("results").getJsonArray("bindings").isEmpty());
    }

    @Test
    public void testConnectivityQuery() throws Exception {
        loadEntities("users", "places", "trips");
        String queryEndpoint = config.getApiUrl() + "/query";
        String queryString = "SELECT DISTINCT ?city WHERE { " +
                "?place <http://cauldron.systems/graph#city> ?city . " +
                "?trip <http://cauldron.systems/graph#destination> ?place . " +
                "?trip <http://cauldron.systems/graph#userId> ?user . " +
                "?user <http://cauldron.systems/graph#name> 'Tony Stark' }";
        JsonObject jsonObject = executeQuery(queryEndpoint, queryString);
        System.out.println(jsonObject.toString());
        assertFalse(jsonObject.getJsonObject("results").getJsonArray("bindings").isEmpty());
    }


    private void loadEntities(String... resourceNames) {
        for (String resourceName : resourceNames) {
            String entityRoot = config.getApiUrl() + "/" + resourceName;
            loadObjects("/" + resourceName + ".json").forEach(e -> {
                try {
                    createEntity(entityRoot, e);
                } catch (IOException ex) {
                    fail(ex);
                }
            });
        }
    }

    private List<JsonObject> loadObjects(String resourceName) {
        try (JsonReader reader = Json.createReader(this.getClass().getResourceAsStream(resourceName))) {
            return reader.readArray().stream().map(v -> (JsonObject) v).collect(Collectors.toList());
        }
    }

    private List<JsonObject> loadSummaryObjects(String resourceName, String idField, Set<String> summaryFields) {
        try (JsonReader reader = Json.createReader(this.getClass().getResourceAsStream(resourceName))) {
            return reader.readArray().stream().map(v -> (JsonObject) v).map(o -> {
                JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
                objectBuilder.add(idField, o.get(idField));
                summaryFields.forEach(f -> objectBuilder.add(f, o.get(f)));
                return objectBuilder.build();
            }).collect(Collectors.toList());
        }
    }

    private void assertPostability(String entityRoot, String idKey, JsonObject entity) throws IOException {
        String entityId = entity.getString(idKey);
        String testLocation = entityRoot + "/" + entityId;
        assertEquals(201, createEntity(entityRoot, entity));
        assertEntityExists(testLocation, entity);
        assertEquals(409, createEntity(entityRoot, entity));
        deleteEntity(testLocation);
        assertEntityNotExists(testLocation);
    }

    private void assertPutability(String entityRoot, String idKey, JsonObject entity, String keyToModify) throws IOException {
        String entityId = entity.getString(idKey);
        String testLocation = entityRoot + "/" + entityId;
        assertEquals(201, createOrUpdateEntity(testLocation, entity));
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
        assertEquals(200, createOrUpdateEntity(testLocation, modified));
        assertEntityExists(testLocation, modified);
        deleteEntity(testLocation);
        assertEntityNotExists(testLocation);
    }

    private void assertPatchability(String entityRoot, String idKey, JsonObject entity, String keyToModify) throws IOException {
        String entityId = entity.getString(idKey);
        String testLocation = entityRoot + "/" + entityId;
        assertEquals(201, createOrUpdateEntity(testLocation, entity));
        assertEntityExists(testLocation, entity);
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        JsonObjectBuilder patchObjectBuilder = Json.createObjectBuilder();
        entity.forEach((k, v) -> {
            if (keyToModify.equals(k)) {
                String modifiedValue = entity.getString(k) + "test";
                objectBuilder.add(k, modifiedValue);
                patchObjectBuilder.add(k, modifiedValue);
            } else {
                objectBuilder.add(k, v);
            }
        });
        JsonObject modified = objectBuilder.build();
        JsonObject patch = patchObjectBuilder.build();
        assertEquals(204, updateEntity(testLocation, patch));
        assertEntityExists(testLocation, modified);
        deleteEntity(testLocation);
        assertEntityNotExists(testLocation);
        assertEquals(404, updateEntity(testLocation, patch));
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

    public static int updateEntity(String urlString, JsonObject object) throws IOException {
        URL url = tryConstructUrl(urlString);
        byte[] body = object.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PATCH");
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
        assertEquals(200, connection.getResponseCode());
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
        assertEquals(200, connection.getResponseCode());
        try (JsonReader reader = Json.createReader(connection.getInputStream())) {
            JsonObject actual = reader.readObject();
            assertEquals(expected, actual);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void assertHealthyOverall(String urlString) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        assertEquals(200, connection.getResponseCode());
        try (JsonReader reader = Json.createReader(connection.getInputStream())) {
            JsonObject actual = reader.readObject();
            String outcome = actual.getString("outcome");
            assertEquals("UP", outcome);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    public static void assertEntityNotExists(String urlString) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        assertEquals(404, connection.getResponseCode());
    }

    public static void assertEntityListing(String urlString, String idKey, List<JsonObject> expected) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        assertEquals(200, connection.getResponseCode());
        try (JsonReader reader = Json.createReader(connection.getInputStream())) {
            Set<JsonObject> actual = reader.readArray().stream().map(JsonValue::asJsonObject).collect(Collectors.toSet());
            HashSet<JsonObject> extraItems = new HashSet<>(actual);
            extraItems.removeAll(expected);
            assertEquals(Collections.emptySet(), extraItems, "extra items in returned collection");
            HashSet<JsonObject> missingItems = new HashSet<>(expected);
            missingItems.removeAll(actual);
            assertEquals(extractConflictingEntities(idKey, expected), missingItems, "conflicting items in returned collection");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void deleteEntity(String urlString) throws IOException {
        URL url = tryConstructUrl(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("DELETE");
        assertEquals(204, connection.getResponseCode());
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

    //HACK: https://stackoverflow.com/questions/25163131/httpurlconnection-invalid-http-method-patch
    private static void allowMethods(String... methods) {
        try {
            Field methodsField = HttpURLConnection.class.getDeclaredField("methods");
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(methodsField, methodsField.getModifiers() & ~Modifier.FINAL);
            methodsField.setAccessible(true);
            String[] oldMethods = (String[]) methodsField.get(null);
            Set<String> methodsSet = new LinkedHashSet<>(Arrays.asList(oldMethods));
            methodsSet.addAll(Arrays.asList(methods));
            String[] newMethods = methodsSet.toArray(new String[0]);
            methodsField.set(null, newMethods);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

}
