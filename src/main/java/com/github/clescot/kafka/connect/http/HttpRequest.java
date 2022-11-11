package com.github.clescot.kafka.connect.http;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import javax.annotation.Nullable;
import java.util.*;

public class HttpRequest {


    public static final String REQUEST_ID = "requestId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TIMEOUT_IN_MS = "timeoutInMs";

    public static final String RETRIES = "retries";
    public static final String RETRY_DELAY_IN_MS = "retryDelayInMs";
    public static final String RETRY_MAX_DELAY_IN_MS = "retryMaxDelayInMs";
    public static final String RETRY_DELAY_FACTOR = "retryDelayFactor";
    public static final String RETRY_JITTER = "retryJitter";
    public static final String URL = "url";
    public static final String METHOD = "method";
    public static final String HEADERS = "headers";

    //only one 'body' field must be set
    public static final String BODY_TYPE = "bodyType";
    public static final String BODY_AS_STRING = "bodyAsString";
    public static final String BODY_AS_BYTE_ARRAY = "bodyAsByteArray";
    public static final String BODY_AS_MULTIPART = "bodyAsMultipart";
    public static final int VERSION = 1;

    //metadata
    private String requestId;
    private String correlationId;

    //connection
    private Long timeoutInMs;

    //retry policy
    private Integer retries;
    private Long retryDelayInMs;
    private Long retryMaxDelayInMs;
    private Double retryDelayFactor;
    private Long retryJitter;

    //request
    private String url;
    private Map<String, List<String>> headers = Maps.newHashMap();
    private String method;
    private String bodyAsString;
    private byte[] bodyAsByteArray = new byte[0];
    private List<byte[]> bodyAsMultipart = Lists.newArrayList();
    private BodyType bodyType;


    public static final Schema SCHEMA = SchemaBuilder
            .struct()
            .name(HttpRequest.class.getName())
            .version(VERSION)
            //meta-data outside of the request
            .field(REQUEST_ID, Schema.OPTIONAL_STRING_SCHEMA)
            .field(CORRELATION_ID, Schema.OPTIONAL_STRING_SCHEMA)
            //connection (override the default one set in the Sink Connector)
            .field(TIMEOUT_IN_MS, Schema.OPTIONAL_INT64_SCHEMA)
            //retry policy (override the default one set in the Sink Connector)
            .field(RETRIES, Schema.OPTIONAL_INT32_SCHEMA)
            .field(RETRY_DELAY_IN_MS, Schema.OPTIONAL_INT64_SCHEMA)
            .field(RETRY_MAX_DELAY_IN_MS, Schema.OPTIONAL_INT64_SCHEMA)
            .field(RETRY_DELAY_FACTOR, Schema.OPTIONAL_FLOAT64_SCHEMA)
            .field(RETRY_JITTER, Schema.OPTIONAL_INT64_SCHEMA)
            //request
            .field(HEADERS, SchemaBuilder.map(Schema.STRING_SCHEMA, SchemaBuilder.array(Schema.STRING_SCHEMA)).build())
            .field(URL, Schema.STRING_SCHEMA)
            .field(METHOD, Schema.STRING_SCHEMA)
            .field(BODY_TYPE, Schema.STRING_SCHEMA)
            .field(BODY_AS_STRING, Schema.OPTIONAL_STRING_SCHEMA)
            .field(BODY_AS_BYTE_ARRAY, Schema.OPTIONAL_BYTES_SCHEMA)
            .field(BODY_AS_MULTIPART, SchemaBuilder.array(Schema.OPTIONAL_BYTES_SCHEMA));

    public HttpRequest(String url,
                       Map<String, List<String>> headers,
                       String method,
                       @Nullable String bodyAsString,
                       @Nullable byte[] bodyAsByteArray,
                       @Nullable List<byte[]> bodyAsMultipart) {
        this.url = url;
        this.headers = Optional.ofNullable(headers).orElse(Maps.newHashMap());
        this.method = method;
        this.bodyAsString = bodyAsString;
        this.bodyAsByteArray = bodyAsByteArray != null ? bodyAsByteArray : this.bodyAsByteArray;
        this.bodyAsMultipart = bodyAsMultipart != null ? bodyAsMultipart : this.bodyAsMultipart;
        if (bodyAsString != null && bodyAsByteArray == null && bodyAsMultipart == null) {
            this.bodyType = BodyType.STRING;
        } else if (bodyAsString == null && bodyAsByteArray != null && bodyAsMultipart == null) {
            this.bodyType = BodyType.BYTE_ARRAY;
        } else if (bodyAsString == null && bodyAsByteArray == null && bodyAsMultipart != null) {
            this.bodyType = BodyType.MULTIPART;
        }


    }


    /**
     * only for json deserialization
     */
    protected HttpRequest() {
    }

    public void setBodyType(BodyType bodyType) {
        this.bodyType = bodyType;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Long getTimeoutInMs() {
        return timeoutInMs;
    }

    public void setTimeoutInMs(long timeoutInMs) {
        this.timeoutInMs = timeoutInMs;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public Long getRetryDelayInMs() {
        return retryDelayInMs;
    }

    public void setRetryDelayInMs(Long retryDelayInMs) {
        this.retryDelayInMs = retryDelayInMs;
    }

    public Long getRetryMaxDelayInMs() {
        return retryMaxDelayInMs;
    }

    public void setRetryMaxDelayInMs(Long retryMaxDelayInMs) {
        this.retryMaxDelayInMs = retryMaxDelayInMs;
    }

    public Double getRetryDelayFactor() {
        return retryDelayFactor;
    }

    public void setRetryDelayFactor(Double retryDelayFactor) {
        this.retryDelayFactor = retryDelayFactor;
    }

    public Long getRetryJitter() {
        return retryJitter;
    }

    public void setRetryJitter(Long retryJitter) {
        this.retryJitter = retryJitter;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public String getBodyAsString() {
        return bodyAsString;
    }

    public byte[] getBodyAsByteArray() {
        return bodyAsByteArray;
    }

    public List<byte[]> getBodyAsMultipart() {
        return bodyAsMultipart;
    }

    public BodyType getBodyType() {
        return bodyType;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpRequest that = (HttpRequest) o;
        return timeoutInMs == that.timeoutInMs && retries == that.retries && retryDelayInMs == that.retryDelayInMs && retryMaxDelayInMs == that.retryMaxDelayInMs && retryDelayFactor == that.retryDelayFactor && retryJitter == that.retryJitter && Objects.equals(requestId, that.requestId) && Objects.equals(correlationId, that.correlationId) && url.equals(that.url) && Objects.equals(headers, that.headers) && method.equals(that.method) && Objects.equals(bodyAsString, that.bodyAsString) && Arrays.equals(bodyAsByteArray, that.bodyAsByteArray) && Objects.equals(bodyAsMultipart, that.bodyAsMultipart);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(requestId, correlationId, timeoutInMs, retries, retryDelayInMs, retryMaxDelayInMs, retryDelayFactor, retryJitter, url, headers, method, bodyAsString, bodyAsMultipart);
        result = 31 * result + Arrays.hashCode(bodyAsByteArray);
        return result;
    }

    @Override
    public String toString() {
        return "HttpRequest{" +
                "requestId='" + requestId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", timeoutInMs=" + timeoutInMs +
                ", retries=" + retries +
                ", retryDelayInMs=" + retryDelayInMs +
                ", retryMaxDelayInMs=" + retryMaxDelayInMs +
                ", retryDelayFactor=" + retryDelayFactor +
                ", retryJitter=" + retryJitter +
                ", url='" + url + '\'' +
                ", headers=" + headers +
                ", method='" + method + '\'' +
                ", bodyType='" + bodyType.name() + '\'' +
                ", stringBody='" + bodyAsString + '\'' +
                ", byteArrayBody=" + Arrays.toString(bodyAsByteArray) +
                ", multipartBody=" + bodyAsMultipart +
                '}';
    }

    public Struct toStruct() {
        Struct struct =
                new Struct(SCHEMA)
                        .put(REQUEST_ID, requestId)
                        .put(CORRELATION_ID, correlationId)
                        .put(TIMEOUT_IN_MS, timeoutInMs)
                        .put(RETRIES, retries)
                        .put(RETRY_DELAY_IN_MS, retryDelayInMs)
                        .put(RETRY_MAX_DELAY_IN_MS, retryMaxDelayInMs)
                        .put(RETRY_DELAY_FACTOR, retryDelayFactor)
                        .put(RETRY_JITTER, retryJitter)
                        .put(URL, url)
                        .put(HEADERS, headers)
                        .put(METHOD, method)
                        .put(BODY_TYPE, bodyType.name())
                        .put(BODY_AS_STRING, bodyAsString)
                        .put(BODY_AS_BYTE_ARRAY, bodyAsByteArray)
                        .put(BODY_AS_MULTIPART, bodyAsMultipart);
        return struct;
    }


    public static final class Builder {

        private Struct struct;

        private Builder() {
        }

        public static Builder anHttpRequest() {
            return new Builder();
        }

        public Builder withStruct(Struct struct) {
            this.struct = struct;
            return this;
        }


        public HttpRequest build() {
            //request
            String url = struct.getString(URL);
            Map<String, List<String>> headers = struct.getMap(HEADERS);
            String method = struct.getString(METHOD);
            String stringBody = struct.getString(BODY_AS_STRING);
            byte[] byteArrayBody = struct.getBytes(BODY_AS_BYTE_ARRAY);
            List<byte[]> multipartBody = struct.getArray(BODY_AS_BYTE_ARRAY);

            HttpRequest httpRequest = new HttpRequest(
                    url,
                    headers,
                    method,
                    stringBody,
                    byteArrayBody,
                    multipartBody
            );

            //metadata
            String requestId = struct.getString(REQUEST_ID);
            String correlationId = struct.getString(CORRELATION_ID);

            //connection
            long timeoutInMs = struct.getInt64(TIMEOUT_IN_MS);

            //retry policy
            int retries = struct.getInt32(RETRIES);
            long retryDelayInMs = struct.getInt64(RETRY_DELAY_IN_MS);
            long retryMaxDelayInMs = struct.getInt64(RETRY_MAX_DELAY_IN_MS);
            double retryDelayFactor = struct.getFloat64(RETRY_DELAY_FACTOR);
            long retryJitter = struct.getInt64(RETRY_JITTER);
            if (retries >= 0) {
                httpRequest.setRetries(retries);
            }
            if (retryDelayInMs >= 0) {
                httpRequest.setRetryDelayInMs(retryDelayInMs);
            }
            if (retryMaxDelayInMs >= 0) {
                httpRequest.setRetryMaxDelayInMs(retryMaxDelayInMs);
            }
            if (retryDelayFactor >= 0) {
                httpRequest.setRetryDelayFactor(retryDelayFactor);
            }
            if (retryJitter >= 0) {
                httpRequest.setRetryJitter(retryJitter);
            }
            return httpRequest;
        }
    }

    private enum BodyType {
        STRING,
        BYTE_ARRAY,
        MULTIPART

    }
}
