package com.example.service7.Service;

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
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SERVICE7
 *
 * Algorytm: Połączenie najczęściej subskrybowanej i polubionej kategorii
 * Endpointy: /history/1/favorite-subscribed-category + /history/1/favorite-liked-category
 * Port: 8088
 * Waga: 0.9
 *
 * Wstrzykiwany błąd:
 * CRASH (20%) - Losowe wyłączanie się przy 20% prawdopodobieństwie (System.exit)
 */

@Component
public class SatelliteClient {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);
    private static final Random random = new Random();

    @Value("${satellite.name:Service7}")
    private String serviceName;

    @Value("${satellite.weight:0.9}")
    private double weight;

    // Prawdopodobieństwo błędu
    @Value("${fault.injection.crash:0.2}")
    private double crashProbability;

    private StompSession session;
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String JS_DB_URL_SUBSCRIBED =
            "http://localhost:3001/history/1/favorite-subscribed-category";
    private static final String JS_DB_URL_LIKED =
            "http://localhost:3001/history/1/favorite-liked-category";

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
                "ws://localhost:8081/main-ws",
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

                        // BŁĄD: Symulacja crash - zatrzymanie aplikacji
                        if (random.nextDouble() < crashProbability) {
                            logger.error("💥 FAULT INJECTION: SERVICE CRASH - Shutting down application!");
                            System.exit(1); // zatrzymanie aplikacji
                        }

                        String combinedCategory = fetchCombinedCategoryFromJsDb();

                        String content = "COMBINED_SUBSCRIBED_LIKED_CATEGORY=" + combinedCategory;

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

    private String fetchCombinedCategoryFromJsDb() {
        try {
            String subscribedCat = fetchCategoryFromUrl(JS_DB_URL_SUBSCRIBED, "mostSubscribedCategory");
            String likedCat = fetchCategoryFromUrl(JS_DB_URL_LIKED, "mostLikedCategory");

            if (subscribedCat.equals(likedCat)) {
                return subscribedCat;
            }

            return subscribedCat + "," + likedCat;

        } catch (Exception e) {
            logger.error("JS DB unavailable: {}", e.getMessage());
            return "ERROR";
        }
    }

    private String fetchCategoryFromUrl(String url, String jsonKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String json = response.body();
        String key = "\"" + jsonKey + "\":";
        int index = json.indexOf(key) + key.length();

        return json.substring(index)
                .replaceAll("[^0-9]", "");
    }
}