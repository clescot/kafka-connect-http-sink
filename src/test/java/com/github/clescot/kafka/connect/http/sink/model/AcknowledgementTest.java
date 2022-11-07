package com.github.clescot.kafka.connect.http.sink.model;


import com.github.clescot.kafka.connect.http.source.Acknowledgement;
import com.google.common.collect.Maps;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.clescot.kafka.connect.http.sink.client.HttpClient.SUCCESS;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


@RunWith(Enclosed.class)
public class AcknowledgementTest {
    public static class TestAcknowledgement {

        @Test
        public void test_nominal_case() {
            Acknowledgement acknowledgement = new Acknowledgement(
                    "dfsdfsd",
                    "sd897osdmsdg",
                    200,
                    "toto",
                    Maps.newHashMap(),
                    "nfgnlksdfnlnskdfnlsf",
                    "http://toto:8081",
                    Maps.newHashMap(),
                    "PUT",
                    "",
                    100,
                    OffsetDateTime.now(),
                    new AtomicInteger(2),
                    SUCCESS);
            Acknowledgement acknowledgement1 = new Acknowledgement("dfsdfsd",
                    "sd897osdmsdg",
                    200,
                    "toto",
                    Maps.newHashMap(),
                    "nfgnlksdfnlnskdfnlsf",
                    "http://toto:8081",
                    Maps.newHashMap(),
                    "PUT",
                    "",
                    100,
                    OffsetDateTime.now(),
                    new AtomicInteger(2),
                    SUCCESS);
            acknowledgement1.equals(acknowledgement);
        }

        @Test
        public void test_nominal_case_detail() {
            Acknowledgement acknowledgement = new Acknowledgement(
                    "sdfsfsdf5555",
                    "sd897osdmsdg",
                    200,
                    "toto",
                    Maps.newHashMap(),
                    "nfgnlksdfnlnskdfnlsf",
                    "http://toto:8081",
                    Maps.newHashMap(),
                    "PUT",
                    "",
                    100,
                    OffsetDateTime.now(),
                    new AtomicInteger(2),
                    SUCCESS
            );
            assertThat(acknowledgement.getResponseBody()).isEqualTo("nfgnlksdfnlnskdfnlsf");
            assertThat(acknowledgement.getCorrelationId()).isEqualTo("sdfsfsdf5555");
            assertThat(acknowledgement.getStatusCode()).isEqualTo(200);
        }
    }
}

