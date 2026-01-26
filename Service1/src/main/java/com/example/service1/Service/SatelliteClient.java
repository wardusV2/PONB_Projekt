package com.example.service1.Service;

import com.example.mainservice.DTO.ServiceMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SatelliteClient {
    public SatelliteClient() {
    }
    public SatelliteClient(String serviceName, double weight, StompSession session) {
        this.serviceName = serviceName;
        this.weight = weight;
        this.session = session;
    }

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${satellite.name:Service1}")
    private String serviceName;

    @Value("${satellite.weight:2.0}")
    private double weight;

    private StompSession session;
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String JS_DB_URL =
            "http://localhost:3001/history/1/favorite-category";

    @PostConstruct
    public void connect() {
        logger.info("{} starting connection to MainService...", serviceName);

        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(java.util.List.of(
                        new WebSocketTransport(new StandardWebSocketClient())
                ))
        );
        client.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<StompSession> futureSession = client.connectAsync(
                "ws://localhost:8080/main-ws",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        logger.info("{} CONNECTED to MainService", serviceName);
                    }
                }
        );

        futureSession.thenAccept(stompSession -> {
            this.session = stompSession;

            stompSession.subscribe("/topic/main-broadcast", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Object.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    logger.info("{} RECEIVED: {}", serviceName, payload);
                }
            });

            ScheduledExecutorService scheduler =
                    Executors.newSingleThreadScheduledExecutor();

            scheduler.scheduleAtFixedRate(() -> {
                if (stompSession.isConnected()) {
                    try {
                        int msgNum = messageCounter.incrementAndGet();
                        String bestCategory = fetchBestCategoryFromJsDb();

                        String content =
                                "MOST_WATCHED_CATEGORY=" + bestCategory;

                        ServiceMessage message =
                                new ServiceMessage(serviceName, content, weight);

                        logger.info("Sending message #{}: {}", msgNum, content);

                        stompSession.send("/app/from-service", message);

                    } catch (Exception e) {
                        logger.error("Error while sending message: {}", e.getMessage());
                    }
                }
            }, 15, 15, TimeUnit.SECONDS);

        });
    }

    /**
     * Pobiera kategorię z JS bazy (REST API)
     */
    private String fetchBestCategoryFromJsDb() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(JS_DB_URL))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String json = response.body();

            // Proste parsowanie JSON bez bibliotek:
            // {"userId":1,"mostWatchedCategory":10}
            String key = "\"mostWatchedCategory\":";
            int index = json.indexOf(key) + key.length();

            return json.substring(index)
                    .replaceAll("[^0-9]", "");

        } catch (Exception e) {
            logger.error("JS DB unavailable: {}", e.getMessage());
            return "ERROR";
        }
    }
}
