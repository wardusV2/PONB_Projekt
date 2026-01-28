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

    private final RecommendationClient recommendationClient;

    public MainServiceController(
            RecommendationClient recommendationClient
    ) {
        this.recommendationClient = recommendationClient;
    }

    /* ============================================================
       ===============  STORAGE (FIXED)  ==========================
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
       ===============  WEIGHTED MAJORITY  ========================
       ============================================================ */

    public Optional<String> computeWeightedMajorityForUser(
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

        double totalWeight = weightSums.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalWeight == 0) {
            return Optional.empty();
        }

        return weightSums.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > totalWeight * 0.5)
                .map(Map.Entry::getKey);
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
                                e -> computeWeightedMajorityForUser(
                                        e.getKey()
                                ).orElse("NO_MAJORITY")
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

                computeWeightedMajorityForUser(userId)
                        .ifPresentOrElse(
                                category -> {
                                    logger.info(
                                            "Majority → user={}, category={}",
                                            userId, category
                                    );
                                    recommendationClient
                                            .saveRecommendation(
                                                    userId,
                                                    category
                                            );
                                },
                                () -> logger.info(
                                        "No majority yet for user {}",
                                        userId
                                )
                        )
        );
    }
}
