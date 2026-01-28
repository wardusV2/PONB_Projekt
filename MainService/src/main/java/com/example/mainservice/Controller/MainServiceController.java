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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@RestController
public class MainServiceController {

    private static final Logger logger =
            LoggerFactory.getLogger(MainServiceController.class);

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RecommendationClient recommendationClient;

    public MainServiceController(RecommendationClient recommendationClient) {
        this.recommendationClient = recommendationClient;
    }

    // ostatnia wiadomość od każdego serwisu
    private final ConcurrentHashMap<String, ServiceMessage> lastMessages =
            new ConcurrentHashMap<>();

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

        lastMessages.put(message.getServiceName(), message);

        return new MainResponse("ACK from MainService");
    }

    /* ============================================================
       ===============  PAYLOAD EXTRACTION  =======================
       ============================================================ */

    @SuppressWarnings("unchecked")
    private Optional<UserCategoryPayload> extractPayload(ServiceMessage msg) {

        if (!(msg.getContent() instanceof Map<?, ?> map)) {
            return Optional.empty();
        }

        try {
            Integer userId = (Integer) map.get("userId");
            String category = (String) map.get("category");

            if (userId == null || category == null) {
                return Optional.empty();
            }

            return Optional.of(new UserCategoryPayload(userId, category));

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /* ============================================================
       ===============  WEIGHTED MAJORITY  ========================
       ============================================================ */

    public Optional<String> computeWeightedMajorityForUser(Integer userId) {

        Map<String, Double> weightSums = new HashMap<>();

        lastMessages.values().forEach(msg ->
                extractPayload(msg).ifPresent(payload -> {
                    if (payload.userId().equals(userId)) {
                        weightSums.merge(
                                payload.category(),
                                msg.getWeight(),
                                Double::sum
                        );
                    }
                })
        );

        double totalWeight = weightSums.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalWeight == 0) return Optional.empty();

        return weightSums.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > totalWeight * 0.5)
                .map(Map.Entry::getKey);
    }

    /* ============================================================
       ===============  SAMPLED VOTE  =============================
       ============================================================ */

    public Optional<String> computeSampledVoteForUser(Integer userId, int k) {

        List<ServiceMessage> messages = lastMessages.values()
                .stream()
                .filter(m -> extractPayload(m)
                        .map(p -> p.userId().equals(userId))
                        .orElse(false))
                .toList();

        if (messages.isEmpty()) return Optional.empty();

        double totalWeight =
                messages.stream().mapToDouble(ServiceMessage::getWeight).sum();

        double[] cumulative = new double[messages.size()];
        double acc = 0;

        for (int i = 0; i < messages.size(); i++) {
            acc += messages.get(i).getWeight();
            cumulative[i] = acc;
        }

        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < k; i++) {
            double r = ThreadLocalRandom.current().nextDouble() * totalWeight;
            int idx = Arrays.binarySearch(cumulative, r);
            if (idx < 0) idx = -idx - 1;
            idx = Math.min(idx, messages.size() - 1);

            ServiceMessage msg = messages.get(idx);
            String category = extractPayload(msg).get().category();

            counts.merge(category, 1, Integer::sum);
        }

        return counts.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /* ============================================================
       ===============  REST ENDPOINT  =============================
       ============================================================ */

    @GetMapping("/vote/result")
    public Map<String, Object> getVoteResult() {

        Integer userId = 1; // NA RAZIE SZTYWNO

        Map<String, Object> result = new HashMap<>();

        result.put(
                "userId",
                userId
        );

        result.put(
                "weightedMajority",
                computeWeightedMajorityForUser(userId)
                        .orElse("NO_MAJORITY")
        );

        result.put(
                "sampledVote_k3",
                computeSampledVoteForUser(userId, 3)
                        .orElse("NO_RESULT")
        );

        result.put(
                "totalWeight",
                lastMessages.values()
                        .stream()
                        .mapToDouble(ServiceMessage::getWeight)
                        .sum()
        );

        result.put(
                "lastMessages",
                lastMessages.values()
                        .stream()
                        .map(m -> Map.of(
                                "service", m.getServiceName(),
                                "payload", m.getContent(),
                                "weight", m.getWeight()
                        ))
                        .collect(Collectors.toList())
        );

        return result;
    }

    /* ============================================================
       ===============  SCHEDULER (opcjonalny)  ===================
       ============================================================ */

    @Scheduled(fixedRate = 15000)
    public void periodicVoteCheck() {

        Integer userId = 1;

        computeWeightedMajorityForUser(userId)
                .ifPresentOrElse(
                        category -> {
                            logger.info("Majority category: {}", category);
                            recommendationClient.saveRecommendation(userId, category);
                        },
                        () -> logger.info("No majority yet")
                );
    }
    public void persistRecommendationIfPresent(Integer userId) {

        computeWeightedMajorityForUser(userId)
                .ifPresent(category -> {
                    logger.info(
                            "Persisting recommendation → user={}, category={}",
                            userId, category
                    );
                    recommendationClient.saveRecommendation(userId, category);
                });
    }
}
