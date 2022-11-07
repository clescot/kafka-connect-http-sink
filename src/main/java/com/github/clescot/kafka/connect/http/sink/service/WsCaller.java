package com.github.clescot.kafka.connect.http.sink.service;

import com.github.clescot.kafka.connect.http.sink.model.Acknowledgement;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.header.Headers;
import org.apache.kafka.connect.sink.SinkRecord;
import org.asynchttpclient.*;
import org.asynchttpclient.proxy.ProxyServer;
import org.asynchttpclient.proxy.ProxyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WsCaller {

    private final static Logger LOGGER = LoggerFactory.getLogger(WsCaller.class);
    public static final String BODY = "body";
    public static final int SERVER_ERROR_STATUS_CODE = 500;
    public static final String HEADER_PREFIX = "header-";
    public static final String PROXY_PREFIX = "proxy-";
    public static final String REALM_PREFIX = "realm-";
    public static final String WS_PREFIX = "ws-";
    private static final String WS_PROXY_HOST = "host";
    private static final String WS_PROXY_PORT = "port";
    private static final String WS_PROXY_SECURED_PORT = "secured-port";
    private static final String WS_PROXY_NON_PROXY_HOSTS = "non-proxy-hosts";
    private static final String WS_PROXY_TYPE = "type";
    private static final String WS_REALM_PRINCIPAL_NAME = "principal-name";
    private static final String WS_REALM_ALGORITHM = "algorithm";
    private static final String WS_REALM_CHARSET = "charset";
    private static final String WS_REALM_LOGIN_CONTEXT_NAME = "login-context-name";
    private static final String WS_REALM_METHOD = "method";
    private static final String WS_REALM_NC = "nc";
    private static final String WS_REALM_NONCE = "nonce";
    private static final String WS_REALM_NTLM_DOMAIN = "ntlm-domain";
    private static final String WS_REALM_NTLM_HOST = "ntlm-host";
    private static final String WS_REALM_OMIT_QUERY = "omit-query";
    private static final String WS_REALM_OPAQUE = "opaque";
    private static final String WS_REALM_QOP = "qop";
    private static final String WS_REALM_SERVICE_PRINCIPAL_NAME = "service-principal-name";
    private static final String WS_REALM_NAME = "name";
    private static final String WS_REALM_USE_PREEMPTIVE_AUTH = "use-preemptive-auth";
    private static final String WS_REALM_AUTH_SCHEME = "auth-scheme";
    public static final String HTTP_PROXY_TYPE = "HTTP";
    public static final String UTF_8 = "UTF-8";
    public static final String UNKNOWN_WS_ID = "UNKNOWN_WS_ID";
    private static final String UNKNOWN_URI = "UNKNOWN_URI";
    private static final String UNKNOWN_METHOD = "UNKNOWN_METHOD";
    public static final String UTC_ZONE_ID = "UTC";


    private AsyncHttpClient asyncHttpClient;
    public static final String BLANK_REQUEST_CONTENT = "";
    public static final String BLANK_RESPONSE_CONTENT = "";
    public static final String WS_SUCCESS_CODE = "success-code";
    public static final String WS_REQUEST_TIMEOUT_IN_MS = "request-timeout-in-ms";
    public static final String WS_READ_TIMEOUT_IN_MS = "read-timeout-in-ms";
    private static final String WS_REALM_PASS = "password";
    public static final String WS_METHOD = "method";
    public static final String WS_CORRELATION_ID = "correlation-id";
    public static final String WS_URL = "url";
    public static final String WS_ID = "ws-ws-id";
    public static final String WS_RETRIES = "retries";
    public static final String WS_RETRY_DELAY_IN_MS = "retry-delay-in-ms";
    public static final String WS_RETRY_MAX_DELAY_IN_MS = "retry-max-delay-in-ms";
    public static final String WS_RETRY_DELAY_FACTOR = "retry-delay-factor";
    public static final String WS_RETRY_JITTER = "retry-jitter";
    private Map<String,Pattern> httpSuccessCodesPatterns = Maps.newHashMap();
    private WsCallerAsyncCompletionHandler asyncCompletionHandler = new WsCallerAsyncCompletionHandler();

    public WsCaller(AsyncHttpClient asyncHttpClient) {
        this.asyncHttpClient = asyncHttpClient;
    }

    /**
     * Les paramètres présents dans les headers kafka permettent de piloter
     * la mécanique d'appels de web service. Ils commencent tous par 'ws-'.
     * Cela comprend
     * 1- les paramètres de pilotage de la politique de réessais, comme :
     * le nombre de reessais, le délai entre les tentatives.
     * 2- L'identifiant unique généré (correlation id), permettant de faire un suivi des multiples appels
     * qu'engendre un évenement métier.
     * 3- les paramètres proxy
     * 4- les paramètres d'authentification
     * 5- les paramètres propres à l'appel http, que sont l'url, la méthode HTTP, les headers http de l'appel (commencant par 'ws-headers-',
     * le timeout d'établisssement de la connexion
     *
     * Le corps de la requête http (encodé en avro), est la valeur du message Kafka.
     * 6- les paramètres propres à la réponse, tel le timeout de lecture de la réponse, la regex de succès de la réponse,
     *
     * @param sinkRecord
     * @return
     */
    public Acknowledgement call(SinkRecord sinkRecord) {

        Struct value = (Struct) sinkRecord.value();
        Headers headerKafka = sinkRecord.headers();

        //properties which was 'ws-key' in headerKafka and are now 'key' : 'ws-' prefix is removed
        Map<String, String> wsProperties= extractWsProperties(headerKafka);
        String wsId = Optional.ofNullable(wsProperties.get(WS_ID)).orElse(UNKNOWN_WS_ID);

        //define RetryPolicy
        int retries = Integer.parseInt(Optional.ofNullable(wsProperties.get(WS_RETRIES)).orElse("3"));
        long retryDelayInMs = Long.parseLong(Optional.ofNullable(wsProperties.get(WS_RETRY_DELAY_IN_MS)).orElse("500"));
        long retryMaxDelayInMs = Long.parseLong(Optional.ofNullable(wsProperties.get(WS_RETRY_MAX_DELAY_IN_MS)).orElse("300000"));
        double retryDelayFactor = Double.parseDouble(Optional.ofNullable(wsProperties.get(WS_RETRY_DELAY_FACTOR)).orElse("2"));
        long retryJitterInMs = Long.parseLong(Optional.ofNullable(wsProperties.get(WS_RETRY_JITTER)).orElse("100"));
        RetryPolicy retryPolicy = new RetryPolicy()
                //we retry only if the error comes from the WS server (server-side technical error)
                .retryOn(RestClientException.class)
                .withBackoff(retryDelayInMs,retryMaxDelayInMs,TimeUnit.MILLISECONDS,retryDelayFactor)
                .withJitter(retryJitterInMs,TimeUnit.MILLISECONDS)
                .withMaxRetries(retries);

        Acknowledgement acknowledgement;
        String correlationId = wsProperties.get(WS_CORRELATION_ID);
        Preconditions.checkNotNull(correlationId,"'correlationId' is required but null");
        AtomicInteger attempts = new AtomicInteger();
        try {
            acknowledgement = Failsafe.with(retryPolicy)
                    .onRetry((result, failure, context) -> LOGGER.warn("Retry ws call result:{}, failure:{}, context{}",result,failure, context))
                    .onFailure((object,throwable,executionContext) -> LOGGER.warn("ws call failed ! object:{},throwable:{},context:{}",object,throwable,executionContext))
                    .onAbort((object,throwable,executionContext) -> LOGGER.warn("ws call aborted ! object:{},throwable:{},context:{}",object,throwable,executionContext))
                    .get(() -> {
                String body = (String) value.get(BODY);
                attempts.addAndGet(1);
                return callOnceWs(wsId,wsProperties, body,attempts);
            });
        } catch (RestClientException e) {
            LOGGER.error("Failed to call web service after {} retries with error {} ", retries, e.getMessage());
            return setAcknowledgement(
                    correlationId,
                    wsId,
                    Optional.ofNullable(wsProperties.get(WS_URL)).orElse(UNKNOWN_URI),
                    Lists.newArrayList(),
                    Optional.ofNullable(wsProperties.get(WS_METHOD)).orElse(UNKNOWN_METHOD),
                    BLANK_REQUEST_CONTENT,
                    Lists.newArrayList(),
                    BLANK_RESPONSE_CONTENT,
                    SERVER_ERROR_STATUS_CODE,
                    String.valueOf(e.getMessage()),
                    Stopwatch.createUnstarted(), OffsetDateTime.now(ZoneId.of(UTC_ZONE_ID)), attempts);
        }
        return acknowledgement;
    }

    protected Map<String, String> extractWsProperties(Headers headerKafka) {
        Map<String, String> map = Maps.newHashMap();
        for (Header headerkafka : headerKafka) {
            if(headerkafka.key().startsWith(WS_PREFIX)) {
                map.put(headerkafka.key().substring(WS_PREFIX.length()), headerkafka.value().toString());
            }
        }
        return map;
    }

    protected Acknowledgement callOnceWs(String wsId, Map<String, String> wsProperties, String body, AtomicInteger attempts) throws RestClientException {
        Request request = buildRequest(wsProperties, body);
        LOGGER.info("request: {}",request.toString());
        LOGGER.info("body: {}", request.getStringData()!=null?request.getStringData():"");
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of(UTC_ZONE_ID));
            ListenableFuture<Response> responseListenableFuture = asyncHttpClient.executeRequest(request, asyncCompletionHandler);
            Response response = responseListenableFuture.get();
            stopwatch.stop();
            LOGGER.info("duration: {}",stopwatch);
            LOGGER.info("response  : {}",response.toString());
            return getAcknowledgement(wsId,wsProperties,request, response,stopwatch, now,attempts);
        } catch (RestClientException | InterruptedException | ExecutionException e) {
            LOGGER.error("Failed to call web service {} ", e.getMessage());
            throw new RestClientException(e.getMessage());
        }finally {
            if(stopwatch.isRunning()){
                stopwatch.stop();
            }
        }

    }

    private Acknowledgement getAcknowledgement(String wsId, Map<String, String> wsProperties, Request request, Response response, Stopwatch stopwatch, OffsetDateTime now, AtomicInteger attempts) {
        Preconditions.checkNotNull(request,"request cannot  be null");
        Preconditions.checkNotNull(response,"response cannot  be null");
        String correlationId;

        int responseStatusCode = handleRetriesBasedOnStatusCode(wsProperties, response);
        correlationId = wsProperties.get(WS_CORRELATION_ID);
        List<Map.Entry<String, String>> requestEntries = request.getHeaders()!=null?request.getHeaders().entries():Lists.newArrayList();
        List<Map.Entry<String, String>> responseEntries = response.getHeaders()!=null?response.getHeaders().entries():Lists.newArrayList();
        return setAcknowledgement(
                correlationId,
                wsId,
                request.getUri().toString(),
                requestEntries!=null?requestEntries:Lists.newArrayList(),
                request.getMethod(),
                request.getStringData(),
                responseEntries!=null?responseEntries:Lists.newArrayList(),
                response.getResponseBody(),
                responseStatusCode,
                response.getStatusText(),
                stopwatch,
                now,
                attempts
        );
    }

    private int handleRetriesBasedOnStatusCode(Map<String, String> wsProperties, Response response) {
        int responseStatusCode = response.getStatusCode();
        //by default, we don't resend any http call with a response between 100 and 499
        // 1xx is for protocol information (100 continue for example),
        // 2xx is for success,
        // 3xx is for redirection
        //4xx is for a client error
        //5xx is for a server error
        //only 5xx by default, trigger a resend
        String wsSuccessCode = "^[1-4][0-9][0-9]$";
        if (wsProperties.get(WS_SUCCESS_CODE) != null) {
            wsSuccessCode = wsProperties.get(WS_SUCCESS_CODE);
        }
        if(this.httpSuccessCodesPatterns.get(wsSuccessCode)==null){
            //Pattern.compile should be reused for performance, but wsSuccessCode can change....
            Pattern httpSuccessPattern = Pattern.compile(wsSuccessCode);
            httpSuccessCodesPatterns.put(wsSuccessCode,httpSuccessPattern);
        }
        Pattern pattern = httpSuccessCodesPatterns.get(wsSuccessCode);
        Matcher matcher = pattern.matcher("" + responseStatusCode);
        /*
        *  WS Server status code returned
        *  3 cases can arises:
        *  * a success occurs : the status code returned from the ws server is matching the regexp => no retries
        *  * a functional error occurs: the status code returned from the ws server is not matching the regexp, but is lower than 500 => no retries
        *  * a technical error occurs from the WS server : the status code returned from the ws server does not match the regexp AND is equals or higher than 500 : retries are done
        */
        if(!matcher.matches()&&responseStatusCode>=500){
            throw new RestClientException("response status code:"+responseStatusCode+" does not match regex "+ pattern.pattern());
        }
        return responseStatusCode;
    }

    private Request buildRequest(Map<String, String> wsProperties, String body) {
        Preconditions.checkNotNull(wsProperties,"'ws properties' are required but null");
        Preconditions.checkNotNull(body,"'body' is required but null");
        String url = wsProperties.get(WS_URL);
        Preconditions.checkNotNull(url,"'url' is required but null");
        String method = wsProperties.get(WS_METHOD);
        Preconditions.checkNotNull(method,"'method' is required but null");

        String correlationId = wsProperties.get(WS_CORRELATION_ID);
        Preconditions.checkNotNull(correlationId,"'correlation-id' is required but null");


        //extract http headers
        Map<String, List<String>> httpHeaders = Maps.newHashMap();
        wsProperties.entrySet().stream().filter(entry -> entry.getKey().startsWith(HEADER_PREFIX))
                .forEach(entry-> httpHeaders.put(entry.getKey().substring(HEADER_PREFIX.length()), Lists.newArrayList(entry.getValue())));

        RequestBuilder requestBuilder = new RequestBuilder()
                .setUrl(url)
                .setHeaders(httpHeaders)
                .setMethod(method)
                .setBody(body);

        //extract proxy headers
        Map<String, String> proxyHeaders = Maps.newHashMap();
        wsProperties.entrySet().stream().filter(entry -> entry.getKey().startsWith(PROXY_PREFIX))
                .forEach(entry-> proxyHeaders.put(entry.getKey().substring(PROXY_PREFIX.length()), entry.getValue()));

        defineProxyServer(requestBuilder, proxyHeaders);

        //extract proxy headers
        Map<String, String> realmHeaders = Maps.newHashMap();
        wsProperties.entrySet().stream().filter(entry -> entry.getKey().startsWith(PROXY_PREFIX))
                .forEach(entry-> realmHeaders.put(entry.getKey().substring(REALM_PREFIX.length()), entry.getValue()));

        defineRealm(requestBuilder, proxyHeaders);
        //retry stuff
        int requestTimeoutInMillis;
        if (wsProperties.get(WS_REQUEST_TIMEOUT_IN_MS) != null) {
            requestTimeoutInMillis = Integer.parseInt(wsProperties.get(WS_REQUEST_TIMEOUT_IN_MS));
            requestBuilder.setRequestTimeout(requestTimeoutInMillis);
        }

        int readTimeoutInMillis;
        if (wsProperties.get(WS_READ_TIMEOUT_IN_MS) != null) {
            readTimeoutInMillis = Integer.parseInt(wsProperties.get(WS_READ_TIMEOUT_IN_MS));
            requestBuilder.setReadTimeout(readTimeoutInMillis);
        }
        Request request = requestBuilder.build();
        return request;
    }

    private void defineProxyServer(RequestBuilder requestBuilder, Map<String, String> proxyHeaders) {
        //proxy stuff
        String proxyHost = proxyHeaders.get(WS_PROXY_HOST);
        if(proxyHost!=null) {


            int proxyPort = Integer.parseInt(proxyHeaders.get(WS_PROXY_PORT));
            ProxyServer.Builder proxyBuilder = new ProxyServer.Builder(proxyHost, proxyPort);

            String securedPortAsString = proxyHeaders.get(WS_PROXY_SECURED_PORT);
            if (securedPortAsString != null) {
                int securedproxyPort = Integer.parseInt(securedPortAsString);
                proxyBuilder.setSecuredPort(securedproxyPort);
            }

            String nonProxyHosts = proxyHeaders.get(WS_PROXY_NON_PROXY_HOSTS);

            if (isNotNullOrEmpty(nonProxyHosts)) {
                List<String> nonProxyHostAsList = Lists.newArrayList(nonProxyHosts.split(","));
                proxyBuilder.setNonProxyHosts(nonProxyHostAsList);
            }
            proxyBuilder.setProxyType(ProxyType.valueOf(Optional.ofNullable(proxyHeaders.get(WS_PROXY_TYPE)).orElse(HTTP_PROXY_TYPE)));

            requestBuilder.setProxyServer(proxyBuilder.build());
        }
    }

    private void defineRealm(RequestBuilder requestBuilder, Map<String, String> realmHeaders) {
        String principals = realmHeaders.get(WS_REALM_PRINCIPAL_NAME);
        String passwords = realmHeaders.get(WS_REALM_PASS);
        if(isNotNullOrEmpty(principals)
        && isNotNullOrEmpty(passwords)){
            Realm.Builder realmBuilder = new Realm.Builder(principals,passwords);

            realmBuilder.setAlgorithm(realmHeaders.get(WS_REALM_ALGORITHM));

            realmBuilder.setCharset(Charset.forName(Optional.ofNullable(realmHeaders.get(WS_REALM_CHARSET)).orElse(UTF_8)));

            realmBuilder.setLoginContextName(realmHeaders.get(WS_REALM_LOGIN_CONTEXT_NAME));

            String authSchemeAsString = Optional.ofNullable(realmHeaders.get(WS_REALM_AUTH_SCHEME)).orElse(null);
            Realm.AuthScheme authScheme =authSchemeAsString!=null?Realm.AuthScheme.valueOf(authSchemeAsString):null;

            realmBuilder.setScheme(authScheme);

            realmBuilder.setMethodName(Optional.ofNullable(realmHeaders.get(WS_REALM_METHOD)).orElse("GET"));

            realmBuilder.setNc(realmHeaders.get(WS_REALM_NC));

            realmBuilder.setNonce((realmHeaders.get(WS_REALM_NONCE)));

            realmBuilder.setNtlmDomain(Optional.ofNullable(realmHeaders.get(WS_REALM_NTLM_DOMAIN)).orElse(System.getProperty("http.auth.ntlm.domain")));

            realmBuilder.setNtlmHost(Optional.ofNullable(realmHeaders.get(WS_REALM_NTLM_HOST)).orElse("localhost"));

            realmBuilder.setOmitQuery(Boolean.parseBoolean(realmHeaders.get(WS_REALM_OMIT_QUERY)));

            realmBuilder.setOpaque(realmHeaders.get(WS_REALM_OPAQUE));

            realmBuilder.setQop(realmHeaders.get(WS_REALM_QOP));

            realmBuilder.setServicePrincipalName(realmHeaders.get(WS_REALM_SERVICE_PRINCIPAL_NAME));

            realmBuilder.setRealmName(realmHeaders.get(WS_REALM_NAME));

            realmBuilder.setUsePreemptiveAuth(Boolean.parseBoolean(realmHeaders.get(WS_REALM_USE_PREEMPTIVE_AUTH)));

            requestBuilder.setRealm(realmBuilder.build());
        }
    }

    private boolean isNotNullOrEmpty(String field){
        return field!=null && !field.isEmpty();
    }


    protected Acknowledgement setAcknowledgement(String correlationId,
                                                 String wsId,
                                                 String requestUri,
                                                 List<Map.Entry<String, String>> requestHeaders,
                                                 String method,
                                                 String requestBody,
                                                 List<Map.Entry<String, String>> responseHeaders,
                                                 String responseBody,
                                                 int responseStatusCode,
                                                 String responseStatusMessage,
                                                 Stopwatch stopwatch,
                                                 OffsetDateTime now,
                                                 AtomicInteger attempts) {
        Preconditions.checkNotNull(correlationId,"'correlationId' is null");
        Preconditions.checkNotNull(wsId,"'wsId' is null");
        Preconditions.checkNotNull(requestUri,"'requestUri' is null");
        Preconditions.checkNotNull(requestHeaders,"'requestHeaders' is null");
        Preconditions.checkNotNull(method,"'method' is null");
        Preconditions.checkNotNull(requestBody,"'requestBody' is null");
        Preconditions.checkNotNull(responseHeaders,"'responseHeaders' is null");
        Preconditions.checkNotNull(responseBody,"'responseBody' is null");
        Preconditions.checkState(responseStatusCode>0,"code is lower than 1");
        Preconditions.checkNotNull(responseStatusMessage,"responseStatusMessage is null");
        return Acknowledgement.AcknowledgementBuilder.anAcknowledgement()
                //functional metadata
                .withWsId(wsId)
                .withCorrelationId(correlationId)
                //request
                .withRequestUri(requestUri)
                .withRequestHeaders(requestHeaders)
                .withMethod(method)
                .withRequestBody(requestBody)
                //response
                .withResponseHeaders(responseHeaders)
                .withResponseBody(responseBody)
                .withStatusCode(responseStatusCode)
                .withStatusMessage(responseStatusMessage)
                //technical metadata
                //time elapsed during http call
                .withDuration(stopwatch.elapsed(TimeUnit.MILLISECONDS))
                //at which moment occurs the beginning of the http call
                .at(now)
                .withAttempts(attempts)
                .build();
    }

    public void setAsyncHttpClient(AsyncHttpClient asyncHttpClient) {
        this.asyncHttpClient = asyncHttpClient;
    }
}