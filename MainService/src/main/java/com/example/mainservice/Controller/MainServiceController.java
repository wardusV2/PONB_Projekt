package com.example.mainservice.Controller;

import com.example.mainservice.DTO.MainResponse;
import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.DTO.UserCategoryPayload;
import com.example.mainservice.Service.RecommendationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class MainServiceController {

    private static final Logger logger =
            LoggerFactory.getLogger(MainServiceController.class);

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // próg niepewności (15%)
    private static final double EPSILON = 0.15;

    private final RecommendationClient recommendationClient;

    public MainServiceController(
            RecommendationClient recommendationClient
    ) {
        this.recommendationClient = recommendationClient;
    }

    /* ============================================================
       ===============  STORAGE  =================================
       ============================================================ */

    // userId -> (serviceName -> last message)
    private final ConcurrentHashMap<Integer,
            ConcurrentHashMap<String, ServiceMessage>>
            lastMessagesPerUser = new ConcurrentHashMap<>();

    /* ============================================================
       ===============  WEBSOCKET ENDPOINT  =======================
       ============================================================ */

    @MessageMapping("/from-service")
    @SendTo("/topic/main-broadcast")
    public MainResponse receiveMessage(ServiceMessage message) {

        String timestamp = LocalDateTime.now().format(formatter);

        logger.info("══════════════════════════════════════");
        logger.info(" RECEIVED MESSAGE at {}", timestamp);
        logger.info(" Service: {}", message.getServiceName());
        logger.info(" Content: {}", message.getContent());
        logger.info(" Weight: {}", message.getWeight());
        logger.info("══════════════════════════════════════");

        extractPayload(message).ifPresent(payload -> {

            lastMessagesPerUser
                    .computeIfAbsent(
                            payload.userId(),
                            id -> new ConcurrentHashMap<>()
                    )
                    .put(message.getServiceName(), message);

            logger.info(
                    "Stored message → user={}, service={}",
                    payload.userId(),
                    message.getServiceName()
            );
        });

        return new MainResponse("ACK from MainService");
    }

    /* ============================================================
       ===============  PAYLOAD EXTRACTION  =======================
       ============================================================ */

    @SuppressWarnings("unchecked")
    private Optional<UserCategoryPayload> extractPayload(
            ServiceMessage msg
    ) {
        if (!(msg.getContent() instanceof Map<?, ?> map)) {
            return Optional.empty();
        }

        try {
            Integer userId = (Integer) map.get("userId");
            String category = (String) map.get("category");

            if (userId == null || category == null) {
                return Optional.empty();
            }

            return Optional.of(
                    new UserCategoryPayload(userId, category)
            );

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /* ============================================================
       ===============  APPROXIMATE VOTING  =======================
       ============================================================ */

    public Optional<String> computeApproximateVoteForUser(
            Integer userId
    ) {

        Map<String, ServiceMessage> userMessages =
                lastMessagesPerUser.get(userId);

        if (userMessages == null || userMessages.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Double> weightSums = new HashMap<>();

        userMessages.values().forEach(msg ->
                extractPayload(msg).ifPresent(payload ->
                        weightSums.merge(
                                payload.category(),
                                msg.getWeight(),
                                Double::sum
                        )
                )
        );

        if (weightSums.isEmpty()) {
            return Optional.empty();
        }

        // tylko jedna kategoria → brak niepewności
        if (weightSums.size() == 1) {
            return Optional.of(
                    weightSums.keySet().iterator().next()
            );
        }

        double totalWeight = weightSums.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        List<Map.Entry<String, Double>> sorted =
                weightSums.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry.<String, Double>
                                                comparingByValue()
                                        .reversed()
                        )
                        .toList();

        double top = sorted.get(0).getValue();
        double second = sorted.get(1).getValue();

        double relativeDiff = (top - second) / totalWeight;

        if (relativeDiff < EPSILON) {
            // zbyt mała przewaga → brak decyzji
            return Optional.empty();
        }

        return Optional.of(sorted.get(0).getKey());
    }

    /* ============================================================
       ===============  REST DEBUG ENDPOINT  ======================
       ============================================================ */

    @GetMapping("/vote/result")
    public Map<String, Object> getVoteResult() {

        Map<String, Object> result = new HashMap<>();

        result.put(
                "users",
                lastMessagesPerUser.keySet()
        );

        result.put(
                "votes",
                lastMessagesPerUser.entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> computeApproximateVoteForUser(
                                        e.getKey()
                                ).orElse("NO_CONFIDENT_VOTE")
                        ))
        );

        return result;
    }

    /* ============================================================
       ===============  SCHEDULER  ================================
       ============================================================ */

    @Scheduled(fixedRate = 15000)
    public void periodicVoteCheck() {

        lastMessagesPerUser.keySet().forEach(userId ->

                computeApproximateVoteForUser(userId)
                        .ifPresentOrElse(
                                category -> {
                                    logger.info(
                                            "Approximate vote → user={}, category={}",
                                            userId, category
                                    );
                                    recommendationClient
                                            .saveRecommendation(
                                                    userId,
                                                    category
                                            );
                                },
                                () -> logger.info(
                                        "No confident vote yet for user {}",
                                        userId
                                )
                        )
        );
    }
}
