package com.smockin.mockserver.engine;

import com.smockin.admin.enums.UserModeEnum;
import com.smockin.admin.exception.ValidationException;
import com.smockin.admin.persistence.enums.RestMethodEnum;
import com.smockin.admin.service.SmockinUserService;
import com.smockin.admin.websocket.LiveLoggingHandler;
import com.smockin.mockserver.dto.*;
import com.smockin.mockserver.exception.MockServerException;
import com.smockin.mockserver.service.*;
import com.smockin.utils.GeneralUtils;
import com.smockin.utils.LiveLoggingUtils;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by mgallina.
 */
@Service
@Transactional(readOnly = true)
public class MockedRestServerEngine {

    private final Logger logger = LoggerFactory.getLogger(MockedRestServerEngine.class);

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private HttpProxyService proxyService;

    @Autowired
    private MockOrderingCounterService mockOrderingCounterService;

    @Autowired
    private InboundParamMatchService inboundParamMatchService;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private ServerSideEventService serverSideEventService;

    @Autowired
    private MockedRestServerEngineUtils mockedRestServerEngineUtils;

    @Autowired
    private LiveLoggingHandler liveLoggingHandler;

    @Autowired
    private SmockinUserService smockinUserService;

    @Autowired
    private ProxyMappingCache proxyMappingCache;


    private static final String SPARK_WILDCARD_PATH = "/*";
    private Javalin app;

    // Server state
    private final Object serverStateMonitor = new Object();
    private MockServerState serverState = new MockServerState(false, 0);

    // Live logging response blocker
    // TODO find a smarter way to do this (i.e BlockingQueue...)
    private final Object responseBlockingMonitor = new Object();
    private Map<String, Optional<LiveloggingUserOverrideResponse>> responseAmendments = new HashMap<>();
    private List<BlockedPathToRelease> userCallsToRelease = new ArrayList<>();
    private AtomicBoolean liveBlockingModeEnabled = new AtomicBoolean();
    private AtomicReference<List<LiveBlockPath>> liveBlockPathsRef = new AtomicReference<>(new ArrayList<>());
    private AtomicBoolean proxyModeEnabled = new AtomicBoolean();


    public void start(final MockedServerConfigDTO config,
                      final List<ProxyForwardConfigCacheDTO> allProxyForwardConfig) throws MockServerException {

        logger.debug("start called");

        initServerConfig(config);

        final boolean isMultiUserMode = UserModeEnum.ACTIVE.equals(smockinUserService.getUserMode());

        updateProxyMode(config.isProxyMode());

        // Define all web socket routes first as the Spark framework requires this
        buildWebSocketEndpoints(isMultiUserMode);

        // Handle Cross-Origin Resource Sharing (CORS) support
        handleCORS(config);

        proxyMappingCache.init(allProxyForwardConfig);

        // Next handle all HTTP RESTFul web service routes
        buildGlobalHttpEndpointsHandler(isMultiUserMode);

        applyTrafficLogging(config.isProxyMode());

        initServer(config.getPort());
    }

    public MockServerState getCurrentState() throws MockServerException {
        synchronized (serverStateMonitor) {
            return serverState;
        }
    }

    public void shutdown() throws MockServerException {

        try {

            serverSideEventService.interruptAndClearAllHeartBeatThreads();

            app.stop();

            synchronized (serverStateMonitor) {
                serverState.setRunning(false);
            }

            clearState();

        } catch (Throwable ex) {
            throw new MockServerException(ex);
        }

    }

    void initServer(final int port) throws MockServerException {
        logger.debug("initServer called");

        try {

            clearState();

            app.start(port);

            synchronized (serverStateMonitor) {
                serverState.setRunning(true);
                serverState.setPort(port);
            }

        } catch (Throwable ex) {
            throw new MockServerException(ex);
        }

    }

    void initServerConfig(final MockedServerConfigDTO config) {
        logger.debug("initServerConfig called");

        if (logger.isDebugEnabled())
            logger.debug(config.toString());

        app = Javalin.create(javalinConfig -> {
            javalinConfig.jetty.defaultPort = config.getPort();
            javalinConfig.jetty.modifyServer(server -> {
                final org.eclipse.jetty.util.thread.QueuedThreadPool threadPool =
                        new org.eclipse.jetty.util.thread.QueuedThreadPool(
                                config.getMaxThreads(), config.getMinThreads(), config.getTimeOutMillis());
                server.addBean(threadPool);
            });
        });
    }

    void buildWebSocketEndpoints(final boolean isMultiUserMode) {

        app.ws(SPARK_WILDCARD_PATH, ws -> {
            ws.onConnect(ctx -> webSocketService.registerSession(ctx.session, isMultiUserMode));
            ws.onClose(ctx -> webSocketService.removeSession(ctx.session));
            ws.onMessage(ctx -> {
                if (com.smockin.mockserver.service.enums.WebSocketCommandEnum.SMOCKIN_ID.name().equals(ctx.message())) {
                    ctx.session.getRemote().sendString(webSocketService.getExternalId(ctx.session));
                    return;
                }
                webSocketService.respondToMessage(ctx.session, ctx.message());
            });
        });
    }

    private void applyTrafficLogging(final boolean isUsingProxyMode) {

        // Live logging filter
        app.before(ctx -> {

            if (ctx.req().getHeader(webSocketService.WS_SEC_WEBSOCKET_KEY) != null) {
                return;
            }

            final String traceId = GeneralUtils.generateUUID();

            ctx.attribute(GeneralUtils.LOG_REQ_ID, traceId);
            ctx.res().addHeader(GeneralUtils.LOG_REQ_ID, traceId);

            final Map<String, String> reqHeaders = ctx.headerMap().keySet()
                    .stream()
                    .collect(Collectors.toMap(h -> h, h -> ctx.header(h)));

            reqHeaders.put(GeneralUtils.LOG_REQ_ID, traceId);

            liveLoggingHandler.broadcast(
                    LiveLoggingUtils.buildLiveLogInboundDTO(
                        ctx.attribute(GeneralUtils.LOG_REQ_ID),
                        ctx.method().name(),
                        ctx.path(),
                        reqHeaders,
                        ctx.body(),
                        isUsingProxyMode,
                        GeneralUtils.extractAllRequestParams(ctx)));
        });

        app.after(ctx -> {

            if (ctx.req().getHeader(webSocketService.WS_SEC_WEBSOCKET_KEY) != null
                    || serverSideEventService.SSE_EVENT_STREAM_HEADER.equals(ctx.res().getHeader(HttpHeaders.CONTENT_TYPE))) {
                return;
            }

            final Map<String, String> respHeaders = mockedRestServerEngineUtils.extractResponseHeadersAsMap(ctx);

            respHeaders.put(GeneralUtils.LOG_REQ_ID, ctx.attribute(GeneralUtils.LOG_REQ_ID));

            liveLoggingHandler.broadcast(
                    LiveLoggingUtils.buildLiveLogOutboundDTO(
                        ctx.attribute(GeneralUtils.LOG_REQ_ID),
                        ctx.path(),
                        ctx.res().getStatus(),
                        respHeaders,
                        ctx.result(),
                        isUsingProxyMode));
        });

    }

    void buildGlobalHttpEndpointsHandler(final boolean isMultiUserMode) {
        logger.debug("buildGlobalHttpEndpointsHandler called");

        // HEAD
        app.head(GeneralUtils.PATH_WILDCARD, ctx -> {
            processResponse(ctx, isMultiUserMode, proxyModeEnabled.get());
            checkForAndHandleBlockSwapAndMock(ctx, isMultiUserMode, proxyModeEnabled.get());
        });

        // GET
        app.get(GeneralUtils.PATH_WILDCARD, ctx -> {
            if (isWebSocketUpgradeRequest(ctx)) {
                ctx.status(HttpStatus.OK.value());
                return;
            }

            final String responseBody = processResponse(ctx, isMultiUserMode, proxyModeEnabled.get());

            final Optional<String> amendmentOpt =
                    checkForAndHandleBlockSwapAndMock(ctx, isMultiUserMode, proxyModeEnabled.get());

            ctx.result((amendmentOpt.isPresent()) ? amendmentOpt.get() : (responseBody != null ? responseBody : ""));
        });

        // POST
        app.post(GeneralUtils.PATH_WILDCARD, ctx -> {
            final String responseBody = processResponse(ctx, isMultiUserMode, proxyModeEnabled.get());

            final Optional<String> amendmentOpt =
                    checkForAndHandleBlockSwapAndMock(ctx, isMultiUserMode, proxyModeEnabled.get());

            ctx.result((amendmentOpt.isPresent()) ? amendmentOpt.get() : (responseBody != null ? responseBody : ""));
        });

        // PUT
        app.put(GeneralUtils.PATH_WILDCARD, ctx -> {
            final String responseBody = processResponse(ctx, isMultiUserMode, proxyModeEnabled.get());

            final Optional<String> amendmentOpt =
                    checkForAndHandleBlockSwapAndMock(ctx, isMultiUserMode, proxyModeEnabled.get());

            ctx.result((amendmentOpt.isPresent()) ? amendmentOpt.get() : (responseBody != null ? responseBody : ""));
        });

        // DELETE
        app.delete(GeneralUtils.PATH_WILDCARD, ctx -> {
            final String responseBody = processResponse(ctx, isMultiUserMode, proxyModeEnabled.get());

            final Optional<String> amendmentOpt =
                    checkForAndHandleBlockSwapAndMock(ctx, isMultiUserMode, proxyModeEnabled.get());

            ctx.result((amendmentOpt.isPresent()) ? amendmentOpt.get() : (responseBody != null ? responseBody : ""));
        });

        // PATCH
        app.patch(GeneralUtils.PATH_WILDCARD, ctx -> {
            final String responseBody = processResponse(ctx, isMultiUserMode, proxyModeEnabled.get());

            final Optional<String> amendmentOpt =
                    checkForAndHandleBlockSwapAndMock(ctx, isMultiUserMode, proxyModeEnabled.get());

            ctx.result((amendmentOpt.isPresent()) ? amendmentOpt.get() : (responseBody != null ? responseBody : ""));
        });

    }

    String processResponse(final Context ctx,
                   final boolean isMultiUserMode,
                   final boolean isProxyMode) {

        return mockedRestServerEngineUtils.loadMockedResponse(ctx, isMultiUserMode, isProxyMode)
                .orElseGet(() ->
                        handleNotFoundResponse(ctx));
    }

    Optional<String> checkForAndHandleBlockSwapAndMock(final Context ctx,
                                                       final boolean isMultiUserMode,
                                                       final boolean isProxyMode)
            throws InterruptedException {

        if (blockLoggingResponse(ctx, isProxyMode)) {

            logger.debug("Endpoint match made. Blocking response...");

            synchronized (responseBlockingMonitor) {

                while (true) {

                    // Wait for response amendment for this request (by traceId)
                    responseBlockingMonitor.wait();

                    final String traceId = ctx.attribute(GeneralUtils.LOG_REQ_ID);

                    // Release request if liveBlocking is disabled at any stage
                    if (!liveBlockingModeEnabled.get()) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Releasing blocked request with traceId: " + traceId + " as blocking mode has been disabled");
                        }
                        break;
                    }

                    if (isMultiUserMode) {

                        if (userCallsToRelease
                                .stream()
                                .anyMatch(p -> {

                                    if (p.getMethod().isPresent()) {

                                        return ctx.method().name().equalsIgnoreCase(p.getMethod().get().name())
                                                && StringUtils.equals(ctx.path(), p.getPathPattern());
                                    }

                                    // Is Admin
                                    if (GeneralUtils.URL_PATH_SEPARATOR.equals(p.getPathPattern())) {
                                        // Nasty solution to a problem of how do we identify the call is to an admin's mock...
                                        // TODO This will improve when we update isInboundPathMultiUserPath to use a cache instead.
                                        final String userCtxPathSegment = mockedRestServerEngineUtils.extractMultiUserCtxPathSegment(ctx.path());
                                        return !mockedRestServerEngineUtils.isInboundPathMultiUserPath(userCtxPathSegment);
                                    }

                                    return StringUtils.startsWith(ctx.path(), p.getPathPattern());
                                })
                        ) {
                            break;
                        }

                    }

                    if (!responseAmendments.containsKey(traceId)) {
                        // No amendment found so continue waiting...
                        continue;
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug("Releasing blocked request with traceId: " + traceId + " as response provided");
                    }

                    final Optional<LiveloggingUserOverrideResponse> responseAmendmentOpt
                            = responseAmendments.get(traceId);

                    // Could be no amendment is provided (in which case this request will default to the original response)
                    if (responseAmendmentOpt.isPresent()) {

                        final String body = amendResponse(responseAmendmentOpt, ctx);

                        return Optional.of(body);
                    }

                    break;
                }
            }

        }

        return Optional.empty();
    }

    private String amendResponse(final Optional<LiveloggingUserOverrideResponse> responseAmendmentOpt,
                               final Context ctx) {

        final LiveloggingUserOverrideResponse responseAmendment = responseAmendmentOpt.get();

        if (!responseAmendment.getResponseHeaders().isEmpty()) {

            final HttpServletResponse httpServletResponse = ctx.res();

            responseAmendment
                .getResponseHeaders()
                .entrySet()
                .forEach(h -> {
                    if (httpServletResponse.containsHeader(h.getKey())) {
                        httpServletResponse.setHeader(h.getKey(), h.getValue());
                    } else {
                        httpServletResponse.addHeader(h.getKey(), (h.getValue() != null) ? h.getValue() : ""); // Allow for blank header values
                    }
                });

            // Clear all other headers, which may have been removed or renamed
            httpServletResponse
                .getHeaderNames()
                .stream()
                .forEach(h -> {
                    if (!responseAmendment.getResponseHeaders().containsKey(h)) {
                        httpServletResponse.setHeader(h, null);
                    }
                });

        }

        ctx.status(responseAmendment.getStatus());
        ctx.result(responseAmendment.getBody());

        return responseAmendment.getBody();
    }

    private String handleNotFoundResponse(final Context ctx) {

        ctx.status(HttpStatus.NOT_FOUND.value());

        return "";
    }

    private boolean isWebSocketUpgradeRequest(final Context ctx) {

        final Map<String, String> headerMap = ctx.headerMap();

        return headerMap.containsKey(HttpHeaders.UPGRADE)
                && headerMap.containsKey("Sec-WebSocket-Key")
                && "websocket".equalsIgnoreCase(ctx.header(HttpHeaders.UPGRADE));
    }

    boolean blockLoggingResponse(final Context ctx,
                          final boolean proxyMode) {

        if (logger.isDebugEnabled()) {
            logger.debug("liveBlockEnabled: " + liveBlockingModeEnabled.get());
            logger.debug("inbound method: " + ctx.method().name());
            logger.debug("inbound path: " + ctx.path());
        }

        if (this.liveBlockingModeEnabled.get()) {

            // Check if this call matches any endpoints that should be blocked...
            if (liveBlockPathsRef.get()
                    .stream()
                    .anyMatch(p ->
                            p.getMethod().name().equalsIgnoreCase(ctx.method().name())
                                    && GeneralUtils.matchPaths(p.getPath(), ctx.path()))) {

                // If so then send response details to live logging console via WS...
                liveLoggingHandler.broadcast(
                        LiveLoggingUtils.buildLiveLogInterceptedResponseDTO(
                                ctx.attribute(GeneralUtils.LOG_REQ_ID),
                                ctx.path(),
                                ctx.res().getStatus(),
                                mockedRestServerEngineUtils.extractResponseHeadersAsMap(ctx),
                                ctx.result(),
                                proxyMode));

                return true;
            }

        }

        return false;
    }

    void clearState() {

        // Proxy related state
        webSocketService.clearSession();
        proxyService.clearAllSessions();
        mockOrderingCounterService.clearState();
        serverSideEventService.clearState();

    }

    void handleCORS(final MockedServerConfigDTO config) {

        final String enableCors = config.getNativeProperties().get(GeneralUtils.ENABLE_CORS_PARAM);

        if (!Boolean.TRUE.toString().equalsIgnoreCase(enableCors)) {
            return;
        }

        app.options(SPARK_WILDCARD_PATH, ctx -> {

            final String accessControlRequestHeaders = ctx.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);

            if (accessControlRequestHeaders != null) {
                ctx.header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, accessControlRequestHeaders);
            }

            final String accessControlRequestMethod = ctx.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);

            if (accessControlRequestMethod != null) {

                ctx.header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, accessControlRequestMethod);
            }

            ctx.result(HttpStatus.OK.name());
        });

        app.before(ctx ->
            ctx.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, GeneralUtils.PATH_WILDCARD));

    }

    public void updateProxyMode(final boolean enable) {
        proxyModeEnabled.set(enable);
    }

    public void releaseBlockedLiveLoggingResponse(final String traceId,
                                                  final Optional<LiveloggingUserOverrideResponse> responseAmendmentOpt) {

        if (logger.isDebugEnabled())
            logger.debug("Adding amended response for blocked request with traceId " + traceId);

        synchronized (responseBlockingMonitor) {

            responseAmendments.put(traceId, responseAmendmentOpt);
            responseBlockingMonitor.notifyAll(); // wake up all waiting threads, so they can check if their is a response amendment.
        }

    }

    public void updateLiveBlockingMode(final boolean liveBlockEnabled) {

        if (logger.isDebugEnabled())
            logger.debug("Live blocking enabled: " + liveBlockEnabled);

        liveBlockingModeEnabled.set(liveBlockEnabled);

        if (!liveBlockingModeEnabled.get()) {

            logger.debug("releasing all outstanding blocked requests...");

            synchronized (responseBlockingMonitor) {
                responseBlockingMonitor.notifyAll(); // wake up all waiting threads, so they can check if their is a response amendment.
            }

        }

    }

    public void notifyBlockedLiveLoggingCalls(final Optional<RestMethodEnum> method, final String userCtxOrFullPath) {


        if (logger.isDebugEnabled()) {
            logger.debug("releasing all outstanding blocked requests for user ctx path: " + method + userCtxOrFullPath);
        }

        final BlockedPathToRelease blockedPathToRelease = new BlockedPathToRelease(method, userCtxOrFullPath);

        // Release any callers currently blocked on this endpoint.
        synchronized (responseBlockingMonitor) {

            if (logger.isDebugEnabled())
                logger.debug("Adding: " + blockedPathToRelease + " to userCallsToRelease");

            userCallsToRelease.add(blockedPathToRelease);

            if (logger.isDebugEnabled())
                logger.debug("userCallsToRelease size : " + userCallsToRelease.size());

            responseBlockingMonitor.notifyAll(); // wake up all waiting threads, so they can check if their is a response amendment.

        }

        // Assume all blocked callers are released after 8 secs and remove entry...
        Executors.newScheduledThreadPool(1)
            .schedule(() -> {
                synchronized (responseBlockingMonitor) {

                    if (logger.isDebugEnabled())
                        logger.debug("Removing: " + blockedPathToRelease + " from userCallsToRelease");

                    userCallsToRelease.remove(blockedPathToRelease);

                    if (logger.isDebugEnabled())
                        logger.debug("userCallsToRelease size : " + userCallsToRelease.size());

                }
        }, 8000, TimeUnit.MILLISECONDS);

    }


    public void addPathToLiveBlocking(final RestMethodEnum method,
                                      final String path,
                                      final String ownerUserId) throws ValidationException {

        if (logger.isDebugEnabled())
            logger.debug("Adding blocking rule for: " + method.name() + " " + path);

        if (liveBlockPathsRef.get().contains(new LiveBlockPath(method, path, ownerUserId))) {
            throw new ValidationException("This endpoint is already being blocked");
        }

        liveBlockPathsRef.get().add(new LiveBlockPath(method, path, ownerUserId));

        if (logger.isDebugEnabled())
            logger.debug("blocking rule size: " + liveBlockPathsRef.get().size());

    }

    public void removePathFromLiveBlocking(final RestMethodEnum method,
                                           final String path,
                                           final String ownerUserId) {

        if (logger.isDebugEnabled())
            logger.debug("Removing blocking rule for: " + method.name() + " " + path);

//        liveBlockPathsRef.get().remove(new LiveBlockPath(method, path, ownerUserId));

        liveBlockPathsRef.compareAndSet(
                liveBlockPathsRef.get(),
                liveBlockPathsRef.get()
                        .stream()
                        .filter(p ->
                                !(StringUtils.equalsIgnoreCase(p.getPath(), path)
                                    && p.getMethod().equals(method)
                                    && StringUtils.equalsIgnoreCase(p.getOwnerUserId(), ownerUserId)))
                        .collect(Collectors.toList()));

        if (logger.isDebugEnabled())
            logger.debug("blocking rule size: " + liveBlockPathsRef.get().size());

    }

    public long countLiveBlockingPathsForUser(final RestMethodEnum method,
                                           final String path,
                                           final String ownerUserId) {

            return liveBlockPathsRef.get()
                    .stream()
                    .filter(p ->
                            StringUtils.equalsIgnoreCase(p.getPath(), path)
                                && p.getMethod().equals(method)
                                && StringUtils.equalsIgnoreCase(p.getOwnerUserId(), ownerUserId))
                    .count();
    }

    public void clearAllPathsFromLiveBlocking() {

        logger.debug("clearing down all blocking rules...");

        liveBlockPathsRef.get().clear();
    }

    public void clearAllPathsFromLiveBlockingForUser(final String ownerUserId) {

        if (logger.isDebugEnabled())
            logger.debug("clearing down all blocking rules for user: " + ownerUserId);

        liveBlockPathsRef.compareAndSet(
                liveBlockPathsRef.get(),
                liveBlockPathsRef.get()
                        .stream()
                        .filter(p ->
                            !StringUtils.equalsIgnoreCase(p.getOwnerUserId(), ownerUserId))
                        .collect(Collectors.toList()));


        if (logger.isDebugEnabled())
            logger.debug("blocking rule size: " + liveBlockPathsRef.get().size());

        if (liveBlockPathsRef.get().isEmpty()) {
            updateLiveBlockingMode(false);
        }

    }

}
