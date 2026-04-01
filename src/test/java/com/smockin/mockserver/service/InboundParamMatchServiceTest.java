package com.smockin.mockserver.service;

import com.smockin.admin.dto.UserKeyValueDataDTO;
import com.smockin.admin.enums.UserModeEnum;
import com.smockin.admin.service.SmockinUserService;
import com.smockin.admin.service.UserKeyValueDataService;
import com.smockin.mockserver.exception.InboundParamMatchException;
import com.smockin.mockserver.service.enums.ParamMatchTypeEnum;
import com.smockin.utils.GeneralUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpMethod;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by mgallina.
 */
@RunWith(MockitoJUnitRunner.class)
public class InboundParamMatchServiceTest {

    private Context request;
    private String sanitizedUserCtxInboundPath;
    private long userId;

    @Mock
    private SmockinUserService smockinUserService;

    @Mock
    private UserKeyValueDataService userKeyValueDataService;

    @Spy
    @InjectMocks
    private InboundParamMatchServiceImpl inboundParamMatchServiceImpl = new InboundParamMatchServiceImpl();


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {

        sanitizedUserCtxInboundPath = "";
        userId = 1;
        request = Mockito.mock(Context.class);
    }

    @Test
    public void processParamMatch_NoToken_Test() {
        Assert.assertNull(inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}","Hello World", sanitizedUserCtxInboundPath, userId));
    }

    @Test
    public void processParamMatch_InvalidToken_Test() {

        // Test
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + "Foo";

        // Assertions
        Assert.assertNull(inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId));
    }

    @Test
    public void processParamMatch_InvalidTokenWithBrackets_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + "Foo()";

        // Test & Assertions
        Assert.assertNull(inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId));
    }

    @Test
    public void processParamMatch_Empty_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + "(  )";

        // Test & Assertions
        Assert.assertNull(inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId));
    }

    @Test
    public void processParamMatch_Blank_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + "()";

        // Test & Assertions
        Assert.assertNull(inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId));

    }

    @Test
    public void processParamMatch_header_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(name)";

        Mockito.when(request.header("name")).thenReturn("Roger");
        Map<String, String> headerMap1 = new HashMap<>();
        headerMap1.put("name", "Roger");
        Mockito.when(request.headerMap()).thenReturn(headerMap1);

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger", result);
    }

    @Test
    public void processParamMatch_headerCase_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(NAME)";

        Mockito.when(request.header("name")).thenReturn("Roger");
        Map<String, String> headerMap2 = new HashMap<>();
        headerMap2.put("name", "Roger");
        Mockito.when(request.headerMap()).thenReturn(headerMap2);

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger", result);
    }

    @Test
    public void processParamMatch_headerNoMatch_Test() {

        // Test
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(name)";
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello ", result);
    }

    @Test
    public void processParamMatch_reqParam_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestParameter.name() +"(name)";

        Mockito.when(request.method()).thenReturn(HandlerType.GET);
        Mockito.when(request.queryParam("name")).thenReturn("Roger");
        Map<String, List<String>> paramMap1 = new HashMap<>();
        paramMap1.put("name", List.of("Roger"));
        Mockito.when(request.queryParamMap()).thenReturn(paramMap1);

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger", result);
    }

    @Test
    public void processParamMatch_reqParamCase_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestParameter.name() +"(NAME)";

        Mockito.when(request.method()).thenReturn(HandlerType.GET);
        Mockito.when(request.queryParam("name")).thenReturn("Roger");
        Map<String, List<String>> paramMap2 = new HashMap<>();
        paramMap2.put("name", List.of("Roger"));
        Mockito.when(request.queryParamMap()).thenReturn(paramMap2);

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger", result);
    }

    @Test
    public void processParamMatch_reqParamNoMatch_Test() {

        // Setup
        Mockito.when(request.method()).thenReturn(HandlerType.GET);
        Mockito.when(request.queryParamMap()).thenReturn(new HashMap<>());

        // Test
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestParameter.name() +"(name)";
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello ", result);
    }

    @Test
    public void processParamMatch_pathVar_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.pathVar.name() +"(name)";

        sanitizedUserCtxInboundPath = "/person/Roger";

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger", result);
    }

    @Test
    public void processParamMatch_pathVarCase_Test() {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.pathVar.name() +"(NAME)";

        sanitizedUserCtxInboundPath = "/person/Roger";

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger", result);
    }

    @Test
    public void processParamMatch_pathVarNoMatch_Test() {

        // Test
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.pathVar.name() +"(name)";
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello ", result);
    }

    @Test
    public void enrichWithInboundParamMatches_multiMatchesAndSpaces_Test() throws InboundParamMatchException {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"('name'), you are " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(GenDer) and are "  + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(\"age\") years old";

        Mockito.when(request.header("name")).thenReturn("Roger");
        Mockito.when(request.header("age")).thenReturn("21");
        Mockito.when(request.header("gender")).thenReturn("Male");
        Map<String, String> headerMap3 = new HashMap<>();
        headerMap3.put("name", "Roger");
        headerMap3.put("age", "21");
        headerMap3.put("gender", "Male");
        Mockito.when(request.headerMap()).thenReturn(headerMap3);

        Mockito.when(smockinUserService.getUserMode()).thenReturn(UserModeEnum.INACTIVE);

        // Test
        final String result = inboundParamMatchServiceImpl.enrichWithInboundParamMatches(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger, you are Male and are 21 years old", result);
    }

    @Test
    public void enrichWithInboundParamMatches_partialMatch_Test() throws InboundParamMatchException {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(name), you are " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(age) years old";

        Mockito.when(request.header("name")).thenReturn("Roger");
        Map<String, String> headerMap4 = new HashMap<>();
        headerMap4.put("name", "Roger");
        Mockito.when(request.headerMap()).thenReturn(headerMap4);

        // Test
        final String result = inboundParamMatchServiceImpl.enrichWithInboundParamMatches(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Hello Roger, you are  years old", result);
    }

    @Test
    public void enrichWithInboundParamMatches_withNoMadeUpToken_Test() throws InboundParamMatchException {

        // Setup
        final String responseBody = "Hello " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader.name() +"(name), you are " + ParamMatchTypeEnum.PARAM_PREFIX + "FOO(age) years old";

        Mockito.when(request.header("name")).thenReturn("Roger");
        Map<String, String> headerMap5 = new HashMap<>();
        headerMap5.put("name", "Roger");
        Mockito.when(request.headerMap()).thenReturn(headerMap5);

        // Test
        final String result = inboundParamMatchServiceImpl.enrichWithInboundParamMatches(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertNotNull(result);
        Assert.assertEquals("Hello Roger, you are " + ParamMatchTypeEnum.PARAM_PREFIX + "FOO(age) years old", result);
    }

    @Test
    public void processParamMatch_isoDate_Test() {

        // Setup
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        final String responseBody = "The date is " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.isoDate.name();

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        final String remainder = result.replaceAll("The date is ", "");

        try {
            Assert.assertNotNull(new SimpleDateFormat(GeneralUtils.ISO_DATE_FORMAT).parse(remainder));
        } catch (Throwable ex) {
            Assert.fail();
        }
    }

    @Test
    public void processParamMatch_isoDateTime_Test() {

        // Setup
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        final String responseBody = "The date and time is " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.isoDatetime.name();

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        final String remainder = result.replaceAll("The date and time is ", "");

        try {
            Assert.assertNotNull(new SimpleDateFormat(GeneralUtils.ISO_DATETIME_FORMAT).parse(remainder));
        } catch (Throwable ex) {
            Assert.fail();
        }
    }

    @Test
    public void processParamMatch_uuid_Test() {

        // Setup
        final String responseBody = "Your ID is " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.uuid.name();

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        final String remainder = result.replaceAll("Your ID is ", "");

        try {
            Assert.assertNotNull(UUID.fromString(remainder));
        } catch (Throwable ex) {
            Assert.fail();
        }
    }

    @Test
    public void processParamMatch_randomNumber_Test() {

        // Setup
        final String responseBody = "Your number is " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.randomNumber.name() + "(1,3)";

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        final String remainder = result.replaceAll("Your number is ", "");
        Assert.assertTrue(NumberUtils.isDigits(remainder));
        Assert.assertTrue((Integer.valueOf(remainder) == 1) || (Integer.valueOf(remainder) == 2) || (Integer.valueOf(remainder) == 3));
    }

    @Test
    public void processParamMatch_randomNumberZero_Test() {

        // Setup
        final String responseBody = "Your number is " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.randomNumber.name() + "(0,0)";

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        final String remainder = result.replaceAll("Your number is ", "");
        Assert.assertTrue(NumberUtils.isDigits(remainder));
        Assert.assertEquals(Integer.valueOf(0), Integer.valueOf(remainder));
    }

    @Test
    public void processParamMatch_randomNumberNoParams_Test() {

        // Assertions
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("randomNumber is missing args");

        // Setup
        final String responseBody = "Your number is " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.randomNumber.name() + "()";

        // Test
        inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

    }

    @Test
    public void processParamMatch_kvpMatch_Test() {

        // Setup
        final String responseBody = "I say " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(Hello)";

        // Mock
        Mockito.when(userKeyValueDataService.loadByKey(Mockito.anyString(), Mockito.anyLong()))
            .thenReturn(new UserKeyValueDataDTO(GeneralUtils.generateUUID(), "Hello", "Bonjour"));

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("I say Bonjour", result);
    }

    @Test
    public void processParamMatch_kvpNoMatch_Test() {

        // Setup
        final String responseBody = "I say "+ ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(Hello)";

        // Mock
        Mockito.when(userKeyValueDataService.loadByKey(Mockito.anyString(), Mockito.anyLong()))
            .thenReturn(null);

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("I say ", result);
    }

    @Test
    public void processParamMatch_kvpNestedRequestBodyMatch_Test() {

        // Setup
        final String responseBody = "I say " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(" + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestBody + ")";

        // Mock
        Mockito.when(request.body()).thenReturn("greeting");
        Mockito.when(userKeyValueDataService.loadByKey(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(new UserKeyValueDataDTO(GeneralUtils.generateUUID(), "greeting", "Good day!"));

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("I say Good day!", result);
    }

    @Test
    public void processParamMatch_kvpNestedRequestParamMatch_Test() {

        // Setup
        final String responseBody = "Watcha " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(" + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestParameter + "(name)" + ")";

        // Mock
        Mockito.when(request.method()).thenReturn(HandlerType.GET);
        Map<String, List<String>> paramMap3 = new HashMap<>();
        paramMap3.put("name", List.of("Max"));
        Mockito.when(request.queryParamMap()).thenReturn(paramMap3);
        Mockito.when(request.queryParam(Mockito.anyString()))
                .thenReturn("Max");
        Mockito.when(userKeyValueDataService.loadByKey(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(new UserKeyValueDataDTO(GeneralUtils.generateUUID(), "max", "Your name is Max"));

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Watcha Your name is Max", result);
    }

    @Test
    public void processParamMatch_kvpNestedPathVarMatch_Test() {

        // Setup
        final String responseBody = "Watcha " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(" + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.pathVar + "(name)" + ")";

        // Mock
        sanitizedUserCtxInboundPath = "/person/max";
        Mockito.when(userKeyValueDataService.loadByKey(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(new UserKeyValueDataDTO(GeneralUtils.generateUUID(), "max", "Your name is Max"));

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person/{name}", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Watcha Your name is Max", result);
    }

    @Test
    public void processParamMatch_kvpNestedRequestHeaderMatch_Test() {

        // Setup
        final String responseBody = "Watcha " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(" + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.requestHeader + "(name)" + ")";

        // Mock
        Map<String, String> headerMap6 = new HashMap<>();
        headerMap6.put("name", "Max");
        Mockito.when(request.headerMap()).thenReturn(headerMap6);
        Mockito.when(request.header(Mockito.anyString()))
                .thenReturn("Max");
        Mockito.when(userKeyValueDataService.loadByKey(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(new UserKeyValueDataDTO(GeneralUtils.generateUUID(), "max", "Your name is Max"));

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Watcha Your name is Max", result);
    }

    @Test
    public void processParamMatch_kvpNestedInvalidParam_Test() {

        // Setup
        final String responseBody = "Watcha " + ParamMatchTypeEnum.PARAM_PREFIX + ParamMatchTypeEnum.lookUpKvp +"(" + ParamMatchTypeEnum.PARAM_PREFIX + "XXX(name)" + ")";

        // Test
        final String result = inboundParamMatchServiceImpl.processParamMatch(request, "/person", responseBody, sanitizedUserCtxInboundPath, userId);

        // Assertions
        Assert.assertEquals("Watcha ", result);
    }

}
