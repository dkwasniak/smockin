package com.smockin.mockserver.engine;

import com.smockin.SmockinTestUtils;
import com.smockin.admin.persistence.dao.RestfulMockDAO;
import com.smockin.admin.persistence.dao.SmockinUserDAO;
import com.smockin.admin.persistence.entity.*;
import com.smockin.admin.persistence.enums.*;
import com.smockin.mockserver.dto.MockedServerConfigDTO;
import com.smockin.mockserver.dto.ProxyForwardConfigCacheDTO;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for MockedRestServerEngine.
 *
 * These tests start a real Spark HTTP server, seed the H2 database with mock
 * definitions, and verify the engine serves the expected responses over HTTP.
 *
 * Phase A of SparkJava-to-Javalin migration: tests are written against
 * SparkJava to establish a green baseline before migration.
 */
@RunWith(SpringRunner.class)
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.smockin")
@EnableJpaRepositories("com.smockin.admin.persistence.dao")
@EntityScan("com.smockin.admin.persistence.entity")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MockServerIntegrationTest {

    @Autowired
    private MockedRestServerEngine engine;

    @Autowired
    private RestfulMockDAO restfulMockDAO;

    @Autowired
    private SmockinUserDAO smockinUserDAO;

    private SmockinUser user;
    private String baseUrl;
    private HttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        SmockinUser testUser = SmockinTestUtils.buildSmockinUser();
        testUser.setUsername("integration-test-" + System.nanoTime());
        user = smockinUserDAO.saveAndFlush(testUser);

        // --- A2: Basic REST mocks ---

        // GET /test -> 200, JSON
        RestfulMock getMock = SmockinTestUtils.buildRestfulMock(
                "/test", RestMockTypeEnum.SEQ, 1, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder getDef = new RestfulMockDefinitionOrder(
                getMock, 200, "application/json", "{\"message\":\"hello\"}", 1, 0, false, 0, 0);
        getMock.getDefinitions().add(getDef);
        restfulMockDAO.saveAndFlush(getMock);

        // POST /test-post -> 200
        RestfulMock postMock = SmockinTestUtils.buildRestfulMock(
                "/test-post", RestMockTypeEnum.SEQ, 2, RestMethodEnum.POST, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder postDef = new RestfulMockDefinitionOrder(
                postMock, 200, "application/json", "{\"created\":true}", 1, 0, false, 0, 0);
        postMock.getDefinitions().add(postDef);
        restfulMockDAO.saveAndFlush(postMock);

        // PUT /test-put -> 200
        RestfulMock putMock = SmockinTestUtils.buildRestfulMock(
                "/test-put", RestMockTypeEnum.SEQ, 3, RestMethodEnum.PUT, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder putDef = new RestfulMockDefinitionOrder(
                putMock, 200, "application/json", "{\"updated\":true}", 1, 0, false, 0, 0);
        putMock.getDefinitions().add(putDef);
        restfulMockDAO.saveAndFlush(putMock);

        // DELETE /test-delete -> 200
        RestfulMock deleteMock = SmockinTestUtils.buildRestfulMock(
                "/test-delete", RestMockTypeEnum.SEQ, 4, RestMethodEnum.DELETE, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder deleteDef = new RestfulMockDefinitionOrder(
                deleteMock, 200, "application/json", "{\"deleted\":true}", 1, 0, false, 0, 0);
        deleteMock.getDefinitions().add(deleteDef);
        restfulMockDAO.saveAndFlush(deleteMock);

        // PATCH /test-patch -> 200
        RestfulMock patchMock = SmockinTestUtils.buildRestfulMock(
                "/test-patch", RestMockTypeEnum.SEQ, 5, RestMethodEnum.PATCH, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder patchDef = new RestfulMockDefinitionOrder(
                patchMock, 200, "application/json", "{\"patched\":true}", 1, 0, false, 0, 0);
        patchMock.getDefinitions().add(patchDef);
        restfulMockDAO.saveAndFlush(patchMock);

        // Custom headers: GET /test-headers -> 200 with custom response headers
        RestfulMock headersMock = SmockinTestUtils.buildRestfulMock(
                "/test-headers", RestMockTypeEnum.SEQ, 6, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder headersDef = new RestfulMockDefinitionOrder(
                headersMock, 200, "application/json", "{\"headers\":true}", 1, 0, false, 0, 0);
        headersDef.getResponseHeaders().put("X-Custom-Header", "custom-value");
        headersDef.getResponseHeaders().put("X-Another-Header", "another-value");
        headersMock.getDefinitions().add(headersDef);
        restfulMockDAO.saveAndFlush(headersMock);

        // Custom status code: GET /test-created -> 201
        RestfulMock createdMock = SmockinTestUtils.buildRestfulMock(
                "/test-created", RestMockTypeEnum.SEQ, 7, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionOrder createdDef = new RestfulMockDefinitionOrder(
                createdMock, 201, "application/json", "{\"status\":\"created\"}", 1, 0, false, 0, 0);
        createdMock.getDefinitions().add(createdDef);
        restfulMockDAO.saveAndFlush(createdMock);

        // --- A3: Rule engine mocks ---

        // Rule match on header: GET /test-rule-header
        RestfulMock ruleHeaderMock = SmockinTestUtils.buildRestfulMock(
                "/test-rule-header", RestMockTypeEnum.RULE, 8, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionRule headerRule = new RestfulMockDefinitionRule(
                ruleHeaderMock, 1, 200, "application/json", "{\"matched\":\"header\"}", 0, false);
        RestfulMockDefinitionRuleGroup headerGroup = new RestfulMockDefinitionRuleGroup(headerRule, 1);
        RestfulMockDefinitionRuleGroupCondition headerCondition = new RestfulMockDefinitionRuleGroupCondition(
                headerGroup, "X-Custom", RuleDataTypeEnum.TEXT, RuleComparatorEnum.EQUALS,
                "test-value", RuleMatchingTypeEnum.REQUEST_HEADER, false);
        headerGroup.getConditions().add(headerCondition);
        headerRule.getConditionGroups().add(headerGroup);
        ruleHeaderMock.getRules().add(headerRule);
        restfulMockDAO.saveAndFlush(ruleHeaderMock);

        // Rule match on query param: GET /test-rule-param
        RestfulMock ruleParamMock = SmockinTestUtils.buildRestfulMock(
                "/test-rule-param", RestMockTypeEnum.RULE, 9, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionRule paramRule = new RestfulMockDefinitionRule(
                ruleParamMock, 1, 200, "application/json", "{\"matched\":\"param\"}", 0, false);
        RestfulMockDefinitionRuleGroup paramGroup = new RestfulMockDefinitionRuleGroup(paramRule, 1);
        RestfulMockDefinitionRuleGroupCondition paramCondition = new RestfulMockDefinitionRuleGroupCondition(
                paramGroup, "status", RuleDataTypeEnum.TEXT, RuleComparatorEnum.EQUALS,
                "active", RuleMatchingTypeEnum.REQUEST_PARAM, false);
        paramGroup.getConditions().add(paramCondition);
        paramRule.getConditionGroups().add(paramGroup);
        ruleParamMock.getRules().add(paramRule);
        restfulMockDAO.saveAndFlush(ruleParamMock);

        // Rule match on request body: POST /test-rule-body
        RestfulMock ruleBodyMock = SmockinTestUtils.buildRestfulMock(
                "/test-rule-body", RestMockTypeEnum.RULE, 10, RestMethodEnum.POST, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionRule bodyRule = new RestfulMockDefinitionRule(
                ruleBodyMock, 1, 200, "application/json", "{\"matched\":\"body\"}", 0, false);
        RestfulMockDefinitionRuleGroup bodyGroup = new RestfulMockDefinitionRuleGroup(bodyRule, 1);
        RestfulMockDefinitionRuleGroupCondition bodyCondition = new RestfulMockDefinitionRuleGroupCondition(
                bodyGroup, null, RuleDataTypeEnum.TEXT, RuleComparatorEnum.CONTAINS,
                "expected-content", RuleMatchingTypeEnum.REQUEST_BODY, false);
        bodyGroup.getConditions().add(bodyCondition);
        bodyRule.getConditionGroups().add(bodyGroup);
        ruleBodyMock.getRules().add(bodyRule);
        restfulMockDAO.saveAndFlush(ruleBodyMock);

        // Rule no-match fallback: GET /test-rule-nomatch
        RestfulMock ruleNoMatchMock = SmockinTestUtils.buildRestfulMock(
                "/test-rule-nomatch", RestMockTypeEnum.RULE, 11, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockDefinitionRule noMatchRule = new RestfulMockDefinitionRule(
                ruleNoMatchMock, 1, 200, "application/json", "{\"should\":\"not-match\"}", 0, false);
        RestfulMockDefinitionRuleGroup noMatchGroup = new RestfulMockDefinitionRuleGroup(noMatchRule, 1);
        RestfulMockDefinitionRuleGroupCondition noMatchCondition = new RestfulMockDefinitionRuleGroupCondition(
                noMatchGroup, "X-Impossible", RuleDataTypeEnum.TEXT, RuleComparatorEnum.EQUALS,
                "impossible-value-12345", RuleMatchingTypeEnum.REQUEST_HEADER, false);
        noMatchGroup.getConditions().add(noMatchCondition);
        noMatchRule.getConditionGroups().add(noMatchGroup);
        ruleNoMatchMock.getRules().add(noMatchRule);
        // Default definition (fallback when no rules match)
        RestfulMockDefinitionOrder noMatchDefault = new RestfulMockDefinitionOrder(
                ruleNoMatchMock, 404, "text/plain", "", 1, 0, false, 0, 0);
        ruleNoMatchMock.getDefinitions().add(noMatchDefault);
        restfulMockDAO.saveAndFlush(ruleNoMatchMock);

        // --- A4: JavaScript mock ---

        // CUSTOM_JS: GET /test-js
        RestfulMock jsMock = SmockinTestUtils.buildRestfulMock(
                "/test-js", RestMockTypeEnum.CUSTOM_JS, 12, RestMethodEnum.GET, RecordStatusEnum.ACTIVE, user);
        RestfulMockJavaScriptHandler jsHandler = new RestfulMockJavaScriptHandler();
        jsHandler.setSyntax(
                "function handleResponse(request, response) { " +
                "response.body = 'computed: ' + request.path; " +
                "response.status = 200; " +
                "response.contentType = 'text/plain'; " +
                "return response; " +
                "}");
        jsHandler.setRestfulMock(jsMock);
        jsMock.setJavaScriptHandler(jsHandler);
        restfulMockDAO.saveAndFlush(jsMock);

        // Start the mock server on a random free port
        int port = findFreePort();
        Map<String, String> nativeProps = new HashMap<>();
        nativeProps.put("ENABLE_CORS", "true");
        MockedServerConfigDTO config = new MockedServerConfigDTO(
                ServerTypeEnum.RESTFUL, port, 10, 1, 30000, false, false, nativeProps);
        engine.start(config, new ArrayList<>());
        baseUrl = "http://localhost:" + port;
        httpClient = HttpClient.newHttpClient();
    }

    @After
    public void tearDown() throws Exception {
        try {
            engine.shutdown();
        } catch (Exception e) {
            // ignore if engine was never started
        }

        restfulMockDAO.deleteAll();
        restfulMockDAO.flush();

        if (user != null) {
            smockinUserDAO.delete(user);
            smockinUserDAO.flush();
        }
    }

    // ========================================================================
    // A2 - Basic REST
    // ========================================================================

    @Test
    public void testGetEndpointReturnsConfiguredResponse() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"message\":\"hello\"}", response.body());
        Assert.assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    public void testPostEndpointReturnsConfiguredResponse() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-post"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":\"test\"}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"created\":true}", response.body());
    }

    @Test
    public void testPutEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-put"))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"data\":\"update\"}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"updated\":true}", response.body());
    }

    @Test
    public void testDeleteEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-delete"))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"deleted\":true}", response.body());
    }

    @Test
    public void testPatchEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-patch"))
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"data\":\"patch\"}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"patched\":true}", response.body());
    }

    @Test
    public void testNotFoundReturns404() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/nonexistent"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(404, response.statusCode());
    }

    @Test
    public void testCustomResponseHeaders() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-headers"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"headers\":true}", response.body());
        Assert.assertEquals("custom-value", response.headers().firstValue("X-Custom-Header").orElse(null));
        Assert.assertEquals("another-value", response.headers().firstValue("X-Another-Header").orElse(null));
    }

    @Test
    public void testCustomStatusCode() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-created"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(201, response.statusCode());
        Assert.assertEquals("{\"status\":\"created\"}", response.body());
    }

    // ========================================================================
    // A3 - Rule engine
    // ========================================================================

    @Test
    public void testRuleMatchOnHeader() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-rule-header"))
                .GET()
                .header("X-Custom", "test-value")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"matched\":\"header\"}", response.body());
    }

    @Test
    public void testRuleMatchOnQueryParam() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-rule-param?status=active"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"matched\":\"param\"}", response.body());
    }

    @Test
    public void testRuleMatchOnRequestBody() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-rule-body"))
                .POST(HttpRequest.BodyPublishers.ofString("this has expected-content in it"))
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("{\"matched\":\"body\"}", response.body());
    }

    @Test
    public void testRuleNoMatchFallback() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-rule-nomatch"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(404, response.statusCode());
    }

    // ========================================================================
    // A4 - JavaScript
    // ========================================================================

    @Test
    public void testJavaScriptMockReturnsComputedResponse() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-js"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("computed: /test-js", response.body());
        Assert.assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/plain"));
    }

    // ========================================================================
    // A5 - CORS
    // ========================================================================

    @Test
    public void testCorsOptionsReturnsCorrectHeaders() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-Custom-Header")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("GET",
                response.headers().firstValue("Access-Control-Allow-Methods").orElse(null));
        Assert.assertEquals("X-Custom-Header",
                response.headers().firstValue("Access-Control-Allow-Headers").orElse(null));
    }

    @Test
    public void testCorsAllowOriginOnAllResponses() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(200, response.statusCode());
        Assert.assertEquals("*",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    // ========================================================================
    // Utility
    // ========================================================================

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
