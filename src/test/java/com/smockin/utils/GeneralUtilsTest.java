package com.smockin.utils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by mgallina on 08/08/17.
 */
public class GeneralUtilsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void generateUUID_Populated_Test() {
        Assert.assertNotNull(GeneralUtils.generateUUID());
    }

    @Test
    public void generateUUID_Distinct_Test() {
        Assert.assertNotEquals(GeneralUtils.generateUUID(), GeneralUtils.generateUUID());
    }

    @Test
    public void getCurrentDate_Populated_Test() {
        Assert.assertNotNull(GeneralUtils.getCurrentDate());
    }

    @Test
    public void findHeaderIgnoreCaseTest() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("name", "Bob");
        headerMap.put("Age", "21");
        Mockito.when(ctx.headerMap()).thenReturn(headerMap);
        Mockito.when(ctx.header("name")).thenReturn("Bob");
        Mockito.when(ctx.header("Age")).thenReturn("21");

        // Test
        final String nameResult = GeneralUtils.findHeaderIgnoreCase(ctx, "NAME");

        // Assertions
        Assert.assertNotNull(nameResult);
        Assert.assertEquals("Bob", nameResult);

        // Test
        final String ageResult = GeneralUtils.findHeaderIgnoreCase(ctx, "age");

        // Assertions
        Assert.assertNotNull(ageResult);
        Assert.assertEquals("21", ageResult);
    }

    @Test
    public void findRequestParamIgnoreCaseTest() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.GET);
        Map<String, List<String>> paramMap = new HashMap<>();
        paramMap.put("name", List.of("Bob"));
        paramMap.put("Age", List.of("21"));
        Mockito.when(ctx.queryParamMap()).thenReturn(paramMap);
        Mockito.when(ctx.queryParam("name")).thenReturn("Bob");
        Mockito.when(ctx.queryParam("Age")).thenReturn("21");

        // Test
        final String nameResult = GeneralUtils.extractRequestParamByName(ctx, "NAME");

        // Assertions
        Assert.assertNotNull(nameResult);
        Assert.assertEquals("Bob", nameResult);

        // Test
        final String ageResult = GeneralUtils.extractRequestParamByName(ctx, "age");

        // Assertions
        Assert.assertNotNull(ageResult);
        Assert.assertEquals("21", ageResult);
    }

    @Test
    public void findPathVarIgnoreCase1Test() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.path()).thenReturn("/person/Bob");

        // Test
        final String nameResult = GeneralUtils.findPathVarIgnoreCase(ctx.path(), "/person/{name}", "NAME");

        // Assertions
        Assert.assertNotNull(nameResult);
        Assert.assertEquals("Bob", nameResult);
    }

    @Test
    public void findPathVarIgnoreCase2Test() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.path()).thenReturn("/person/21");

        // Test
        final String ageResult = GeneralUtils.findPathVarIgnoreCase(ctx.path(), "/person/{age}", "agE");

        // Assertions
        Assert.assertNotNull(ageResult);
        Assert.assertEquals("21", ageResult);
    }

    @Test
    public void prefixPath_AddPrefix_Test() {
        Assert.assertEquals("/xxx", GeneralUtils.prefixPath("xxx"));
    }

    @Test
    public void prefixPath_PrefixAlreadyExists_Test() {
        Assert.assertEquals("/xxx", GeneralUtils.prefixPath("/xxx"));
    }

    @Test
    public void prefixPath_Null_Test() {
        Assert.assertNull(GeneralUtils.prefixPath(null));
    }

    @Test
    public void prefixPath_Blank_Test() {
        Assert.assertNull(GeneralUtils.prefixPath(""));
    }

    @Test
    public void prefixPath_Empty_Test() {
        Assert.assertNull(GeneralUtils.prefixPath("   "));
    }

    @Test
    public void exactVersionNo_FullInfoTest() {
        Assert.assertEquals(121, GeneralUtils.exactVersionNo("1.2.1-SNAPSHOT"));
    }

    @Test
    public void exactVersionNo_FullInfoCaseTest() {
        Assert.assertEquals(124, GeneralUtils.exactVersionNo("1.2.4-SnapShot"));
    }

    @Test
    public void exactVersionNo_PartialInfoTest() {
        Assert.assertEquals(152, GeneralUtils.exactVersionNo("1.5.2"));
    }

    @Test
    public void exactVersionNo_NullTest() {

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("versionNo is not defined");

        GeneralUtils.exactVersionNo(null);
    }

    @Test
    public void exactVersionNo_InvalidFormatTest() {

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("extracted versionNo is not a valid number: Mambo no 5!");

        GeneralUtils.exactVersionNo("Mambo no 5!");
    }

    @Test
    public void deserialiseJSON_SimpleParse_Test() {

        // Test
        final Map<String, ?> jsonObj = GeneralUtils.deserialiseJSONToMap("{ \"name\" : \"bob\", \"age\" : 21 }");

        // Assertions
        Assert.assertNotNull(jsonObj);
        Assert.assertTrue(jsonObj.containsKey("name"));
        Assert.assertTrue(jsonObj.containsKey("age"));
        Assert.assertEquals("bob", jsonObj.get("name"));
        Assert.assertEquals(21, jsonObj.get("age"));
    }

    @Test
    public void deserialiseJSON_InvalidJson_Test() {
        Assert.assertNull(GeneralUtils.deserialiseJSONToMap("{ \"name\" :, \"age\" : 21 }"));
    }

    @Test
    public void deserialiseJSON_Null_Test() {
        Assert.assertNull(GeneralUtils.deserialiseJSONToMap(null));
    }

    @Test
    public void removeAllLineBreaks_Test() {
        final String lb = System.getProperty("line.separator");
        Assert.assertEquals("{ \"name\" :\"John Smith\", \"age\":21}", GeneralUtils.removeAllLineBreaks("{"+lb+" \"name\" :\"John Smith\","+lb+" \"age\":21"+lb+"}"));
    }

    @Test
    public void removeAllLineBreaks_NoChange_Test() {
        Assert.assertEquals("{ \"name\" :\"Max\", \"age\" : 21 }", GeneralUtils.removeAllLineBreaks("{ \"name\" :\"Max\", \"age\" : 21 }"));
    }

    @Test
    public void removeAllLineBreaks_Null_Test() {
        Assert.assertNull(GeneralUtils.removeAllLineBreaks(null));
    }

    @Test
    public void removeAllLineBreaks_Blank_Test() {
        Assert.assertEquals("", GeneralUtils.removeAllLineBreaks(""));
    }

    @Test
    public void findAllPathVars_1Arg_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob", "/hello/{name}");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(1, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("name"));
        Assert.assertEquals("bob", pathVars.get("name"));
    }

    @Test
    public void findAllPathVars_2Args_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob/foo/bar", "/hello/{name}/foo/{res}");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(2, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("name"));
        Assert.assertTrue(pathVars.containsKey("res"));
        Assert.assertEquals("bob", pathVars.get("name"));
        Assert.assertEquals("bar", pathVars.get("res"));
    }

    @Test
    public void findAllPathVars_wildcard1Arg_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob", "/hello/*");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(1, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("*1"));
        Assert.assertEquals("bob", pathVars.get("*1"));
    }

    @Test
    public void findAllPathVars_wildcard1ArgCaseInsensitive_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob", "/hello/{nAmE}");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(1, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("name"));
        Assert.assertEquals("bob", pathVars.get("name"));
    }

    @Test
    public void findAllPathVars_wildcard1ArgOpen_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob/world", "/hello/*");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(1, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("*1"));
        Assert.assertEquals("bob", pathVars.get("*1"));
    }

    @Test
    public void findAllPathVars_wildcard2Args_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob/foo/bar", "/hello/*/foo/*");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(2, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("*1"));
        Assert.assertTrue(pathVars.containsKey("*3"));
        Assert.assertEquals("bob", pathVars.get("*1"));
        Assert.assertEquals("bar", pathVars.get("*3"));
    }

    @Test
    public void findAllPathVars_NoArgs_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/world", "/hello/world");

        Assert.assertNotNull(pathVars);
        Assert.assertTrue(pathVars.isEmpty());
    }

    @Test
    public void findAllPathVars_ArgsSegmentsMismatch1_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/bob/world", "/hello/{name}");

        Assert.assertNotNull(pathVars);
        Assert.assertEquals(1, pathVars.size());
        Assert.assertTrue(pathVars.containsKey("name"));
        Assert.assertEquals("bob", pathVars.get("name"));
    }

    @Test
    public void findAllPathVars_ArgsSegmentsMismatch2_Test() {

        final Map<String, String> pathVars = GeneralUtils.findAllPathVars("/hello/world", "/hello/world/{name}");

        Assert.assertNotNull(pathVars);
        Assert.assertTrue(pathVars.isEmpty());
    }

    @Test
    public void extractRequestParamByNameTest() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.PUT);
        Map<String, List<String>> paramMap = new HashMap<>();
        paramMap.put("name", List.of("bob"));
        Mockito.when(ctx.queryParamMap()).thenReturn(paramMap);
        Mockito.when(ctx.queryParam("name")).thenReturn("bob");

        // Test
        final String result = GeneralUtils.extractRequestParamByName(ctx, "name");

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals("bob", result);

    }

    @Test
    public void extractRequestParamByName_nullValue_Test() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.GET);
        Map<String, List<String>> paramMap = new HashMap<>();
        paramMap.put("name", List.of());
        Mockito.when(ctx.queryParamMap()).thenReturn(paramMap);
        Mockito.when(ctx.queryParam("name")).thenReturn(null);

        // Test
        final String result = GeneralUtils.extractRequestParamByName(ctx, "name");

        // Assertions
        Assert.assertNull(result);

    }

    @Test
    public void extractRequestParamByName_emptyMap_Test() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.GET);
        Mockito.when(ctx.queryParamMap()).thenReturn(new HashMap<>());

        // Test
        final String result = GeneralUtils.extractRequestParamByName(ctx, "name");

        // Assertions
        Assert.assertNull(result);

    }

    @Test
    public void extractAllRequestParamsTest() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.POST);
        Map<String, List<String>> paramMap = new HashMap<>();
        paramMap.put("name", List.of("bob"));
        paramMap.put("age", List.of("27"));
        Mockito.when(ctx.queryParamMap()).thenReturn(paramMap);
        Mockito.when(ctx.queryParam("name")).thenReturn("bob");
        Mockito.when(ctx.queryParam("age")).thenReturn("27");

        // Test
        final Map<String, String> results = GeneralUtils.extractAllRequestParams(ctx);

        // Assertions
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());
        Assert.assertEquals("bob", results.get("name"));
        Assert.assertEquals("27", results.get("age"));

    }

    @Test
    public void extractAllRequestParams_nullValues_Test() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.PATCH);
        Map<String, List<String>> paramMap = new HashMap<>();
        paramMap.put("name", List.of());
        paramMap.put("age", List.of());
        Mockito.when(ctx.queryParamMap()).thenReturn(paramMap);
        Mockito.when(ctx.queryParam("name")).thenReturn(null);
        Mockito.when(ctx.queryParam("age")).thenReturn(null);

        // Test
        final Map<String, String> results = GeneralUtils.extractAllRequestParams(ctx);

        // Assertions
        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());
        Assert.assertNull(results.get("name"));
        Assert.assertNull(results.get("age"));

    }

    @Test
    public void extractAllRequestParams_emptyMap_Test() {

        // Setup
        final Context ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.method()).thenReturn(HandlerType.GET);
        Mockito.when(ctx.queryParamMap()).thenReturn(new HashMap<>());

        // Test
        final Map<String, String> results = GeneralUtils.extractAllRequestParams(ctx);

        // Assertions
        Assert.assertNotNull(results);
        Assert.assertEquals(0, results.size());

    }

    @Test
    public void deserialiseJSONToListTest() {

        // Test
        final List<Map<String, ?>> result = GeneralUtils.deserialiseJSONToList("[{\"fruit\":{\"name\":\"pear\"}},{\"fruit\":{\"name\":\"apple\"}}]");

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());
        Assert.assertNotNull(result.get(0));
        Assert.assertNotNull(result.get(1));
        Assert.assertNotNull(result.get(0).get("fruit"));
        Assert.assertNotNull(result.get(1).get("fruit"));
        Assert.assertTrue(result.get(0).get("fruit") instanceof Map);
        Assert.assertTrue(result.get(1).get("fruit") instanceof Map);
        Assert.assertTrue(((Map)result.get(0).get("fruit")).get("name") != null);
        Assert.assertTrue(((Map)result.get(1).get("fruit")).get("name") != null);
        Assert.assertEquals("pear", ((Map)result.get(0).get("fruit")).get("name"));
        Assert.assertEquals("apple", ((Map)result.get(1).get("fruit")).get("name"));

    }

    @Test
    public void deserialiseJSONToListEmptyTest() {

        // Test
        final List<Map<String, ?>> result = GeneralUtils.deserialiseJSONToList("[]");

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());

    }

    @Test
    public void deserialiseJSONToListNullTest() {

        // Test
        final List<Map<String, ?>> result = GeneralUtils.deserialiseJSONToList(null);

        // Assertions
        Assert.assertNull(result);

    }

    @Test
    public void deserialiseJSONToListBlankTest() {

        // Test
        final List<Map<String, ?>> result = GeneralUtils.deserialiseJSONToList(" ");

        // Assertions
        Assert.assertNull(result);

    }

    @Test
    public void removeJsCommentsTest() {

        // Setup
        final String jsSrc = "function doSomething(a,b) {\n"
                + "  var c = a;\n"
                + "  // hide this line\n"
                + "  var d = b; // hide this half of the line\n"
                + "  var e = c+d;\n"
                + "} // end of function";

        // Test
        final String result = GeneralUtils.removeJsComments(jsSrc);

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals("function doSomething(a,b) {\n" +
                "  var c = a;\n" +
                "  var d = b; \n" +
                "  var e = c+d;\n" +
                "}", result);
    }

    @Test
    public void removeJsComments_noCommentsPresent_Test() {

        // Setup
        final String jsSrc = "function doSomething(a,b) {\n"
                + "  var c = a;\n"
                + "  var d = b;\n"
                + "  var e = c+d;\n"
                + "}";

        // Test
        final String result = GeneralUtils.removeJsComments(jsSrc);

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals("function doSomething(a,b) {\n" +
                "  var c = a;\n" +
                "  var d = b;\n" +
                "  var e = c+d;\n" +
                "}", result);
    }

    @Test
    public void removeJsComments_singleLine_Test() {

        // Setup
        final String jsSrc = "function doSomething(a,b) { var c = a; var d = b; var e = c+d; } // end of line";

        // Test
        final String result = GeneralUtils.removeJsComments(jsSrc);

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals("function doSomething(a,b) { var c = a; var d = b; var e = c+d; }", result);
    }

    @Test
    public void removeJsComments_nullInput_Test() {

        // Test & Assertions
        Assert.assertNull(GeneralUtils.removeJsComments(null));
    }

    @Test
    public void removeJsComments_BlankInput_Test() {

        // Test
        final String result = GeneralUtils.removeJsComments("");

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals("", result);
    }

}
