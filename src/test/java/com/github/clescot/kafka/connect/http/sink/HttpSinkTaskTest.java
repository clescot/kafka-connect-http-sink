package com.github.clescot.kafka.connect.http.sink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.clescot.kafka.connect.http.*;
import com.github.clescot.kafka.connect.http.sink.client.HttpClient;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.sink.ErrantRecordReporter;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTaskContext;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.clescot.kafka.connect.http.sink.HttpSinkConfigDefinition.PUBLISH_TO_IN_MEMORY_QUEUE;
import static com.github.clescot.kafka.connect.http.sink.HttpSinkConfigDefinition.STATIC_REQUEST_HEADER_NAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HttpSinkTaskTest {

    @Mock
    ErrantRecordReporter errantRecordReporter;
    @Mock
    SinkTaskContext sinkTaskContext;

    @Mock
    Queue<HttpExchange> dummyQueue;

    @InjectMocks
    HttpSinkTask httpSinkTask;

    @BeforeEach
    public void setUp(){
        QueueFactory.clearRegistrations();
        MockitoAnnotations.openMocks(this);
        httpSinkTask.initialize(sinkTaskContext);
    }

    @Test
    public void test_start_with_queue_name(){
        Map<String,String> settings = Maps.newHashMap();
        settings.put(ConfigConstants.QUEUE_NAME,"dummyQueueName");
        httpSinkTask.start(settings);
    }

    @Test
    public void test_start_with_static_request_headers(){
        Map<String,String> settings = Maps.newHashMap();
        settings.put(STATIC_REQUEST_HEADER_NAMES,"param1,param2");
        settings.put("param1","value1");
        settings.put("param2","value2");
        httpSinkTask.start(settings);
    }

    @Test
    public void test_start_with_static_request_headers_without_required_parameters(){
        Assertions.assertThrows(NullPointerException.class,()->{
            HttpSinkTask wsSinkTask = new HttpSinkTask();
            Map<String,String> settings = Maps.newHashMap();
            settings.put(STATIC_REQUEST_HEADER_NAMES,"param1,param2");
            wsSinkTask.start(settings);
        });

    }


    @Test
    public void test_start_no_settings(){
        httpSinkTask.start(Maps.newHashMap());
    }


    @Test
    public void test_put_add_static_headers(){
        Map<String,String> settings = Maps.newHashMap();
        settings.put(STATIC_REQUEST_HEADER_NAMES,"param1,param2");
        settings.put("param1","value1");
        settings.put("param2","value2");
        httpSinkTask.start(settings);
        HttpClient httpClient = mock(HttpClient.class);
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        when(httpClient.call(any(HttpRequest.class))).thenReturn(dummyHttpExchange);
        httpSinkTask.setHttpClient(httpClient);
        List<SinkRecord> records = Lists.newArrayList();
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,getDummyHttpRequestAsString(),-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        records.add(sinkRecord);
        httpSinkTask.put(records);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient,times(1)).call(captor.capture());
        HttpRequest enhancedRecordBeforeHttpCall = captor.getValue();
        assertThat(enhancedRecordBeforeHttpCall.getHeaders().size()==sinkRecord.headers().size()+ httpSinkTask.getStaticRequestHeaders().size());
        assertThat(enhancedRecordBeforeHttpCall.getHeaders()).contains(Map.entry("param1",Lists.newArrayList("value1")));
        assertThat(enhancedRecordBeforeHttpCall.getHeaders()).contains(Map.entry("param2",Lists.newArrayList("value2")));
    }

    @Test
    public void test_put_nominal_case(){
        //given
        Map<String,String> settings = Maps.newHashMap();
        httpSinkTask.start(settings);

        //mock httpClient
        HttpClient httpClient = mock(HttpClient.class);
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        when(httpClient.call(any(HttpRequest.class))).thenReturn(dummyHttpExchange);
        httpSinkTask.setHttpClient(httpClient);

        //init sinkRecord
        List<SinkRecord> records = Lists.newArrayList();
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,getDummyHttpRequestAsString(),-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        records.add(sinkRecord);

        //when
        httpSinkTask.put(records);

        //then

        //no additional headers added
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient,times(1)).call(captor.capture());
        HttpRequest enhancedRecordBeforeHttpCall = captor.getValue();
        assertThat(enhancedRecordBeforeHttpCall.getHeaders().size()==sinkRecord.headers().size());

        //no records are published into the in memory queue by default
        verify(dummyQueue,never()).offer(any(HttpExchange.class));
    }

    @Test
    public void test_put_sink_record_with_null_value(){
        //given
        Map<String,String> settings = Maps.newHashMap();
        httpSinkTask.start(settings);

        //mock httpClient
        HttpClient httpClient = mock(HttpClient.class);
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        when(httpClient.call(any(HttpRequest.class))).thenReturn(dummyHttpExchange);
        httpSinkTask.setHttpClient(httpClient);

        //init sinkRecord
        List<SinkRecord> records = Lists.newArrayList();
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,null,-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        records.add(sinkRecord);

        //when
        httpSinkTask.put(records);
        //then
        verify(dummyQueue,never()).offer(any(HttpExchange.class));
    }

    @Test
    public void test_put_with_publish_to_in_memory_queue_without_consumer(){
        //given
        Map<String,String> settings = Maps.newHashMap();
        settings.put(PUBLISH_TO_IN_MEMORY_QUEUE,"true");
        httpSinkTask.start(settings);

        //mock httpClient
        HttpClient httpClient = mock(HttpClient.class);
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        when(httpClient.call(any(HttpRequest.class))).thenReturn(dummyHttpExchange);
        httpSinkTask.setHttpClient(httpClient);

        //init sinkRecord
        List<SinkRecord> records = Lists.newArrayList();
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,"myValue",-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        records.add(sinkRecord);

        //when
        //then
        Assertions.assertThrows(IllegalArgumentException.class,
                ()-> httpSinkTask.put(records));

    }



    @Test
    public void test_put_with_publish_in_memory_set_to_false(){
        Map<String,String> settings = Maps.newHashMap();
        settings.put(PUBLISH_TO_IN_MEMORY_QUEUE,"false");
        httpSinkTask.start(settings);
        HttpClient httpClient = mock(HttpClient.class);
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        when(httpClient.call(any(HttpRequest.class))).thenReturn(dummyHttpExchange);
        httpSinkTask.setHttpClient(httpClient);
        Queue<HttpExchange> queue = mock(Queue.class);
        httpSinkTask.setQueue(queue);
        List<SinkRecord> records = Lists.newArrayList();
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,getDummyHttpRequestAsString(),-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        records.add(sinkRecord);
        httpSinkTask.put(records);
        verify(httpClient,times(1)).call(any(HttpRequest.class));
        verify(queue,never()).offer(any(HttpExchange.class));
    }

    @Test
    public void test_put_with_publish_to_in_memory_queue_set_to_true_with_a_consumer(){

        //given
        Map<String,String> settings = Maps.newHashMap();
        settings.put(PUBLISH_TO_IN_MEMORY_QUEUE,"true");
        QueueFactory.registerConsumerForQueue(QueueFactory.DEFAULT_QUEUE_NAME);
        httpSinkTask.start(settings);
        HttpClient httpClient = mock(HttpClient.class);
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        when(httpClient.call(any(HttpRequest.class))).thenReturn(dummyHttpExchange);
        httpSinkTask.setHttpClient(httpClient);
        Queue<HttpExchange> queue = mock(Queue.class);
        httpSinkTask.setQueue(queue);
        List<SinkRecord> records = Lists.newArrayList();
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,getDummyHttpRequestAsString(),-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        records.add(sinkRecord);
        //when
        httpSinkTask.put(records);

        //then
        verify(httpClient,times(1)).call(any(HttpRequest.class));
        verify(queue,times(1)).offer(any(HttpExchange.class));
    }


    @Test
    public void test_http_exchange_json_serialization() throws JsonProcessingException, JSONException {
        HttpExchange dummyHttpExchange = getDummyHttpExchange();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String httpExchangeAsString = objectMapper.writeValueAsString(dummyHttpExchange);
        String expectedJSON = "" +
                "{\n" +
                "  \"durationInMillis\": 245,\n" +
                "  \"moment\": 1668388166.569457181,\n" +
                "  \"attempts\": 1,\n" +
                "  \"success\": true,\n" +
                "  \"httpResponse\": {\n" +
                "    \"statusCode\": 200,\n" +
                "    \"statusMessage\": \"OK\",\n" +
                "    \"responseBody\": \"my response\",\n" +
                "    \"responseHeaders\": {\n" +
                "      \"Content-Type\": [\"application/json\"]\n" +
                "    }\n" +
                "  },\n" +
                "  \"httpRequest\": {\n" +
                "    \"url\": \"http://www.titi.com\",\n" +
                "    \"headers\": {\n" +
                "      \"X-dummy\": [\n" +
                "        \"blabla\"\n" +
                "      ]\n" +
                "    },\n" +
                "    \"method\": \"GET\",\n" +
                "    \"bodyAsString\": \"stuff\",\n" +
                "    \"bodyAsByteArray\": \"\",\n" +
                "    \"bodyAsMultipart\": [],\n" +
                "    \"bodyType\": \"STRING\"\n" +
                "  }\n" +
                "}";

        JSONAssert.assertEquals(expectedJSON, httpExchangeAsString,
                new CustomComparator(JSONCompareMode.LENIENT,
                        new Customization("moment", (o1, o2) -> true),
                        new Customization("durationInMillis", (o1, o2) -> true)
                ));


    }


    @Test
    public void test_buildHttpRequest_null_sink_record(){
        //when
        //then
        Assertions.assertThrows(ConnectException.class,()->httpSinkTask.buildHttpRequest(null));
    }
    @Test
    public void test_buildHttpRequest_null_value_sink_record(){
        //when
        List<Header> headers = Lists.newArrayList();
        SinkRecord sinkRecord = new SinkRecord("myTopic",0, Schema.STRING_SCHEMA,"key",Schema.STRING_SCHEMA,null,-1,System.currentTimeMillis(), TimestampType.CREATE_TIME,headers);
        //then
        Assertions.assertThrows(ConnectException.class,()->httpSinkTask.buildHttpRequest(sinkRecord));
    }


    private HttpExchange getDummyHttpExchange() {
        Map<String, List<String>> requestHeaders = Maps.newHashMap();
        requestHeaders.put("X-dummy",Lists.newArrayList("blabla"));
        HttpRequest httpRequest = new HttpRequest("http://www.titi.com","GET","STRING","stuff",null,null);
        httpRequest.setHeaders(requestHeaders);
        HttpResponse httpResponse = new HttpResponse(200,"OK","my response");
        Map<String,List<String>> responseHeaders = Maps.newHashMap();
        responseHeaders.put("Content-Type",Lists.newArrayList("application/json"));
        httpResponse.setResponseHeaders(responseHeaders);
        return new HttpExchange(
                httpRequest,
              httpResponse,
                245L,
                OffsetDateTime.now(ZoneId.of("UTC")),
                new AtomicInteger(1),
                true
        );
    }


    private String getDummyHttpRequestAsString(){
        return "{\n" +
                "  \"url\": \"http://www.stuff.com\",\n" +
                "  \"headers\": {},\n" +
                "  \"method\": \"GET\",\n" +
                "  \"bodyAsString\": \"stuff\",\n" +
                "  \"bodyAsByteArray\": null,\n" +
                "  \"bodyAsMultipart\": null,\n" +
                "  \"bodyType\": \"STRING\"\n" +
                "}";
    }

}