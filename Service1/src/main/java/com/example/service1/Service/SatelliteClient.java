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

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Parametry pobierane z application.properties
    @Value("${satellite.name:Service1}")
    private String serviceName;

    @Value("${satellite.weight:2.0}")
    private double weight;

    // Obiekt sesji STOMP (połączenie WebSocket ze śródserwisem MainService)
    private StompSession session;

    // Licznik wysłanych wiadomości
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    // Klient HTTP do komunikacji z bazą JS (Node/Express)
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Endpoint do pobierania kategorii z JS DB
    private static final String JS_DB_URL =
            "http://localhost:3001/history/1/favorite-category";

    /**
     * Metoda wykonywana automatycznie po starcie aplikacji.
     * Nawiązuje połączenie STOMP WebSocket do MainService.
     */
    @PostConstruct
    public void connect() {
        logger.info("{} starting connection to MainService...", serviceName);

        // Tworzymy WebSocket STOMP clienta (przez SockJS)
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(
                        new WebSocketTransport(new StandardWebSocketClient())
                ))
        );

        // Konwerter JSON -> obiekty
        client.setMessageConverter(new MappingJackson2MessageConverter());

        // Asynchroniczne nawiązanie połączenia z websocketem MainService
        CompletableFuture<StompSession> futureSession = client.connectAsync(
                "ws://localhost:8080/main-ws",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        logger.info("{} CONNECTED to MainService", serviceName);
                    }
                }
        );

        // Po nawiązaniu połączenia...
        futureSession.thenAccept(stompSession -> {
            this.session = stompSession;

            // Subskrypcja broadcastów z MainService
            stompSession.subscribe("/topic/main-broadcast", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Object.class; // payload jest JSON -> mapowany przez Jacksona
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    logger.info("{} RECEIVED: {}", serviceName, payload);
                }
            });

            // Harmonogram wysyłania wiadomości co 15 sekund
            ScheduledExecutorService scheduler =
                    Executors.newSingleThreadScheduledExecutor();

            scheduler.scheduleAtFixedRate(() -> {
                if (stompSession.isConnected()) {
                    try {
                        int msgNum = messageCounter.incrementAndGet();

                        // Pobranie kategorii z bazy JS
                        String bestCategory = fetchBestCategoryFromJsDb();

                        // Budowanie contentu wiadomości
                        String content =
                                "MOST_WATCHED_CATEGORY=" + bestCategory;

                        // Obiekt do wysłania
                        ServiceMessage message =
                                new ServiceMessage(serviceName, content, weight);

                        logger.info("Sending message #{}: {}", msgNum, content);

                        // Wysłanie komunikatu STOMP do MainService
                        stompSession.send("/app/from-service", message);

                    } catch (Exception e) {
                        logger.error("Error while sending message: {}", e.getMessage());
                    }
                }
            }, 15, 15, TimeUnit.SECONDS);  // start po 15s, powtarzaj co 15s

        });
    }

    /**
     * Pobiera z JS DB ulubioną kategorię użytkownika.
     * Zwraca liczbę lub "ERROR", jeśli zapytanie się nie powiedzie.
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

            // Przykładowy JSON: {"userId":1,"mostWatchedCategory":10}
            String key = "\"mostWatchedCategory\":";
            int index = json.indexOf(key) + key.length();

            // Parsowanie proste: usuwamy wszystko co nie-cyfrowe
            return json.substring(index)
                    .replaceAll("[^0-9]", "");

        } catch (Exception e) {
            logger.error("JS DB unavailable: {}", e.getMessage());
            return "ERROR";
        }
    }
}
