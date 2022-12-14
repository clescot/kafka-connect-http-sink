package com.github.clescot.kafka.connect.http.sink.client;

import com.github.clescot.kafka.connect.http.HttpExchange;
import com.github.clescot.kafka.connect.http.HttpRequest;
import com.github.clescot.kafka.connect.http.HttpResponse;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public interface HttpClient<Req, Res> {
    boolean FAILURE = false;
    int SERVER_ERROR_STATUS_CODE = 500;
    String BLANK_RESPONSE_CONTENT = "";
    String UTC_ZONE_ID = "UTC";
    boolean SUCCESS = true;
    int ONE_HTTP_REQUEST = 1;
    Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);



    default HttpExchange buildHttpExchange(HttpRequest httpRequest,
                                           HttpResponse httpResponse,
                                           Stopwatch stopwatch,
                                           OffsetDateTime now,
                                           AtomicInteger attempts,
                                           boolean success) {
        Preconditions.checkNotNull(httpRequest, "'httpRequest' is null");
        return HttpExchange.Builder.anHttpExchange()
                //request
                .withHttpRequest(httpRequest)
                //response
                .withHttpResponse(httpResponse)
                //technical metadata
                //time elapsed during http call
                .withDuration(stopwatch.elapsed(TimeUnit.MILLISECONDS))
                //at which moment occurs the beginning of the http call
                .at(now)
                .withAttempts(attempts)
                .withSuccess(success)
                .build();
    }



    <Response> Response buildRequest(HttpRequest httpRequest);

    default   HttpExchange call(HttpRequest httpRequest, AtomicInteger attempts) throws HttpException {


        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            Req request = buildRequest(httpRequest);
            LOGGER.info("request: {}", request.toString());
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of(UTC_ZONE_ID));
            Res response = nativeCall(request);
            LOGGER.info("response: {}", response);
            stopwatch.stop();
            HttpResponse httpResponse = buildResponse(response);
            LOGGER.info("duration: {}", stopwatch);
            return buildHttpExchange(httpRequest, httpResponse, stopwatch, now, attempts,httpResponse.getStatusCode()<400?SUCCESS:FAILURE);
        } catch (HttpException e) {
            LOGGER.error("Failed to call web service {} ", e.getMessage());
            throw new HttpException(e.getMessage());
        } finally {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }
        }
    }


    HttpResponse buildResponse(Res response);
    Res nativeCall(Req request);
}
