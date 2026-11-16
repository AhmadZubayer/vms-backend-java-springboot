package com.example.stark_vms_server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.stark_vms_server.entity.User;
import com.example.stark_vms_server.repository.UserRepository;
import com.example.stark_vms_server.repository.VehicleRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointVerificationTests {

    private static final String TEST_USER_EMAIL = "endpoint-verify-user";
    private static final String TEST_PASSWORD = "endpoint-verify-password";
    private static final String TEST_PLATE = "VERIFY-0001";
    private static final String OTHER_PLATE = "VERIFY-0002";
    private static final long MISSING_ID = 999_999_999L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper json;

    private final List<String> results = new ArrayList<>();

    @Test
    void everyEndpointBehavesAsExpected() throws Exception {
        users.save(new User(TEST_USER_EMAIL, passwords.encode(TEST_PASSWORD)));
        Long createdId = null;

        try {
            MockHttpServletResponse health = call(get("/api/health"));
            check("GET /api/health", 200, health);
            record("health reports the server and the database",
                    "UP".equals(tree(health).path("data").path("status").asString())
                            && tree(health).path("data").path("databaseConnected").asBoolean(),
                    "health=" + tree(health).path("data"));
            check("GET /api/vehicles without token", 401, call(get("/api/vehicles")));
            check("GET /api/auth/me without token", 401, call(get("/api/auth/me")));

            MockHttpServletResponse badLogin = call(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("userEmail", TEST_USER_EMAIL, "password", "wrong-password"))));
            check("POST /api/auth/login wrong password", 401, badLogin);

            MockHttpServletResponse login = call(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("userEmail", TEST_USER_EMAIL, "password", TEST_PASSWORD))));
            check("POST /api/auth/login", 200, login);

            String token = tree(login).path("data").path("accessToken").asString();
            record("login returns a token", !token.isEmpty(), "tokenPresent=" + !token.isEmpty());
            record("login tokenType is Bearer",
                    "Bearer".equals(tree(login).path("data").path("tokenType").asString()),
                    "tokenType=" + tree(login).path("data").path("tokenType").asString());
            record("login is not cacheable",
                    String.valueOf(login.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store"),
                    "Cache-Control=" + login.getHeader(HttpHeaders.CACHE_CONTROL));

            MockHttpServletResponse me = call(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/auth/me", 200, me);
            record("me returns the signed-in user email",
                    TEST_USER_EMAIL.equals(tree(me).path("data").path("userEmail").asString()),
                    "userEmail=" + tree(me).path("data").path("userEmail").asString());

            check("GET /api/auth/me with a bad token", 401,
                    call(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")));

            MockHttpServletResponse created = call(post("/api/vehicles")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(vehiclePayload(TEST_PLATE, "ACTIVE"))));
            check("POST /api/vehicles", 201, created);
            createdId = tree(created).path("data").path("id").asLong();
            record("create returns a Location header",
                    created.getHeader(HttpHeaders.LOCATION) != null,
                    "Location=" + created.getHeader(HttpHeaders.LOCATION));
            record("create wraps the body in data",
                    tree(created).has("data"), "keys=" + tree(created).propertyNames());

            check("POST /api/vehicles duplicate plate", 409,
                    call(post("/api/vehicles")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(vehiclePayload(TEST_PLATE.toLowerCase(), "ACTIVE")))));

            MockHttpServletResponse invalidBody = call(post("/api/vehicles")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("vehicleNumber", "", "brand", "Toyota"))));
            check("POST /api/vehicles invalid body", 400, invalidBody);
            record("body validation lists field errors",
                    tree(invalidBody).path("errors").isArray()
                            && !tree(invalidBody).path("errors").isEmpty(),
                    "errors=" + tree(invalidBody).path("errors"));

            check("GET /api/vehicles/{id}", 200,
                    call(get("/api/vehicles/" + createdId).header(HttpHeaders.AUTHORIZATION, bearer(token))));
            check("GET /api/vehicles/{missing}", 404,
                    call(get("/api/vehicles/" + MISSING_ID).header(HttpHeaders.AUTHORIZATION, bearer(token))));

            MockHttpServletResponse list = call(get("/api/vehicles")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/vehicles", 200, list);
            record("list is paged",
                    tree(list).path("data").path("page").has("totalElements"),
                    "page=" + tree(list).path("data").path("page"));

            MockHttpServletResponse searched = call(get("/api/vehicles")
                    .param("q", "VERIFY-000")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/vehicles?q=", 200, searched);
            record("search finds the created vehicle",
                    tree(searched).path("data").path("content").toString().contains(TEST_PLATE),
                    "found=" + tree(searched).path("data").path("page").path("totalElements"));

            MockHttpServletResponse caseInsensitive = call(get("/api/vehicles")
                    .param("q", "verify-000")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            record("search ignores case",
                    caseInsensitive.getStatus() == 200
                            && tree(caseInsensitive).path("data").path("content").toString().contains(TEST_PLATE),
                    "status=" + caseInsensitive.getStatus());

            MockHttpServletResponse filtered = call(get("/api/vehicles")
                    .param("status", "active")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/vehicles?status=active", 200, filtered);
            record("status filter returns only that status",
                    !tree(filtered).path("data").path("content").toString().contains("INACTIVE"),
                    "statuses=" + shorten(tree(filtered).path("data").path("content").toString()));
            check("GET /api/vehicles?status=bogus", 400,
                    call(get("/api/vehicles").param("status", "bogus")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))));

            MockHttpServletResponse stats = call(get("/api/vehicles/stats")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/vehicles/stats", 200, stats);
            record("stats totals every status",
                    tree(stats).path("data").path("total").asLong() >= 1
                            && tree(stats).path("data").path("byStatus").has("RETIRED"),
                    "stats=" + shorten(tree(stats).path("data").toString()));

            check("GET /api/vehicles?sort=status,desc", 200,
                    call(get("/api/vehicles").param("sort", "status,desc")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))));
            check("GET /api/vehicles?sort=bogus", 400,
                    call(get("/api/vehicles").param("sort", "bogus")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))));

            MockHttpServletResponse longTerm = call(get("/api/vehicles")
                    .param("q", "x".repeat(65))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/vehicles?q= too long", 400, longTerm);
            record("query-param validation lists field errors",
                    longTerm.getContentAsString().contains("\"errors\""),
                    "body=" + shorten(longTerm.getContentAsString()));

            MockHttpServletResponse clamped = call(get("/api/vehicles").param("size", "500")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)));
            check("GET /api/vehicles?size=500", 200, clamped);
            record("size is clamped to 100",
                    tree(clamped).path("data").path("page").path("size").asInt() == 100,
                    "size=" + tree(clamped).path("data").path("page").path("size"));

            check("PUT /api/vehicles/{id}", 200,
                    call(put("/api/vehicles/" + createdId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(vehiclePayload(OTHER_PLATE, "MAINTENANCE")))));
            check("PUT /api/vehicles/{missing}", 404,
                    call(put("/api/vehicles/" + MISSING_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(vehiclePayload("VERIFY-0003", "ACTIVE")))));

            MockHttpServletResponse spec = call(get("/v3/api-docs"));
            check("GET /v3/api-docs", 200, spec);
            record("spec documents the vehicle routes",
                    tree(spec).path("paths").has("/api/vehicles")
                            && tree(spec).path("paths").has("/api/auth/login"),
                    "paths=" + tree(spec).path("paths").propertyNames());
            record("spec declares the bearer scheme",
                    tree(spec).path("components").path("securitySchemes").has("bearerAuth"),
                    "schemes=" + tree(spec).path("components").path("securitySchemes").propertyNames());

            MockHttpServletResponse docs = call(get("/docs"));
            record("GET /docs redirects to Swagger UI",
                    docs.getStatus() / 100 == 3
                            && String.valueOf(docs.getHeader(HttpHeaders.LOCATION)).contains("swagger-ui"),
                    "status=" + docs.getStatus() + " Location=" + docs.getHeader(HttpHeaders.LOCATION));

            check("DELETE /api/vehicles/{id}", 204,
                    call(delete("/api/vehicles/" + createdId).header(HttpHeaders.AUTHORIZATION, bearer(token))));
            check("DELETE /api/vehicles/{id} again", 404,
                    call(delete("/api/vehicles/" + createdId).header(HttpHeaders.AUTHORIZATION, bearer(token))));
            createdId = null;

        } finally {
            if (createdId != null) {
                vehicles.deleteById(createdId);
            }
            users.deleteById(TEST_USER_EMAIL);
        }

        System.out.println("=== endpoint verification ===");
        results.forEach(System.out::println);

        assertThat(results.stream().filter(line -> line.startsWith("FAIL")).toList())
                .as("failing endpoint checks")
                .isEmpty();
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private void check(String name, int expected, MockHttpServletResponse response) {
        record(name, response.getStatus() == expected,
                "expected=" + expected + " actual=" + response.getStatus());
    }

    private void record(String name, boolean passed, String detail) {
        results.add((passed ? "PASS " : "FAIL ") + name + "  [" + detail + "]");
    }

    private JsonNode tree(MockHttpServletResponse response) throws Exception {
        String content = response.getContentAsString();
        return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
    }

    private String body(Map<String, ?> payload) {
        return json.writeValueAsString(payload);
    }

    private static Map<String, String> vehiclePayload(String plate, String status) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vehicleNumber", plate);
        payload.put("brand", "Toyota");
        payload.put("company", "Square");
        payload.put("model", "Corolla");
        payload.put("type", "SEDAN");
        payload.put("status", status);
        return payload;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String shorten(String value) {
        return value.length() > 160 ? value.substring(0, 160) + "..." : value;
    }
}
