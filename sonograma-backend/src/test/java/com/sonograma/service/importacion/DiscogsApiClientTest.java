package com.sonograma.service.importacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DiscogsApiClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolvesMasterMainReleaseAndCachesRepeatedIds() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/masters/500", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{\"main_release\":600}");
        });
        server.createContext("/releases/600", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, """
                    {"title":"Album","year":2020,"artists":[{"name":"Artist"}],
                     "genres":["Electronic"],"labels":[{"name":"Label"}]}
                    """);
        });
        server.start();

        DiscogsApiClient client = client();
        var first = client.fetch("master", 500);
        var second = client.fetch("master", 500);

        assertThat(first.success()).isTrue();
        assertThat(first.masterId()).isEqualTo(500L);
        assertThat(first.resolvedReleaseId()).isEqualTo(600L);
        assertThat(second.cacheHit()).isTrue();
        assertThat(requests).hasValue(2);
    }

    @Test
    void doesNotCacheRateLimitAsPermanentFailure() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/releases/700", exchange -> {
            int call = requests.incrementAndGet();
            if (call == 1) {
                exchange.getResponseHeaders().add("Retry-After", "1");
                respond(exchange, 429, "{}");
            } else {
                respond(exchange, 200, """
                        {"title":"Recovered","artists":[{"name":"Artist"}]}
                        """);
            }
        });
        server.start();

        DiscogsApiClient client = client();
        var limited = client.fetch("release", 700);
        var recovered = client.fetch("release", 700);

        assertThat(limited.rateLimited()).isTrue();
        assertThat(limited.retryAfterMs()).isEqualTo(1_000);
        assertThat(recovered.success()).isTrue();
        assertThat(requests).hasValue(2);
    }

    private DiscogsApiClient client() {
        DiscogsApiClient client = new DiscogsApiClient(
                new ObjectMapper(),
                HttpClient.newHttpClient(),
                2
        );
        client.configureForTest("http://localhost:" + server.getAddress().getPort(), "", 0L, 2);
        return client;
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
