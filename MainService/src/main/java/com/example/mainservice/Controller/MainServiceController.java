package com.example.mainservice.Controller;

import com.example.mainservice.DTO.MainResponse;
import com.example.mainservice.DTO.ServiceMessage;
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

    private static final Logger logger = LoggerFactory.getLogger(MainServiceController.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // przechowujemy ostatnią wiadomość od każdego serwisu
    private final ConcurrentHashMap<String, ServiceMessage> lastMessages = new ConcurrentHashMap<>();

    @MessageMapping("/from-service")
    @SendTo("/topic/main-broadcast")
    public MainResponse receiveMessage(ServiceMessage message) {
        String timestamp = LocalDateTime.now().format(formatter);

        logger.info("═══════════════════════════════════════════════════════");
        logger.info(" RECEIVED MESSAGE at {}", timestamp);
        logger.info("   Service: {}", message.getServiceName());
        logger.info("   Content: {}", message.getContent());
        logger.info("   Weight: {}", message.getWeight());
        logger.info("═══════════════════════════════════════════════════════");

        // zapisujemy najnowszą wiadomość od serwisu
        lastMessages.put(message.getServiceName(), message);

        String responseMsg = "MainService ACK: " + message.getContent() + " (from " + message.getServiceName() + ")";
        logger.info(" SENDING RESPONSE: {}", responseMsg);

        return new MainResponse(responseMsg);
    }

    // --- Metody głosowania ---

    // 1) Weighted majority: agregujemy sumy wag dla identycznych contentów, wymagamy >50% sumy wag
    public Optional<String> computeWeightedMajority() {
        Map<String, Double> weightSums = new HashMap<>();
        lastMessages.values().forEach(msg ->
                weightSums.merge(msg.getContent(), msg.getWeight(), Double::sum)
        );

        double totalWeight = weightSums.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight == 0) return Optional.empty();

        Map.Entry<String, Double> maxEntry = weightSums.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (maxEntry == null) return Optional.empty();

        if (maxEntry.getValue() > totalWeight * 0.5) {
            return Optional.of(maxEntry.getKey());
        } else {
            return Optional.empty(); // brak większości
        }
    }

    // 2) Sampled vote (k losowań proporcjonalnych do wag)
    public Optional<String> computeSampledVote(int k) {
        List<ServiceMessage> messages = new ArrayList<>(lastMessages.values());
        if (messages.isEmpty()) return Optional.empty();

        double sum = messages.stream().mapToDouble(ServiceMessage::getWeight).sum();
        double[] cumulative = new double[messages.size()];
        double acc = 0;
        for (int i = 0; i < messages.size(); i++) {
            acc += messages.get(i).getWeight();
            cumulative[i] = acc;
        }

        // wybór jednego według wag
        java.util.function.Supplier<ServiceMessage> pickOne = () -> {
            double r = ThreadLocalRandom.current().nextDouble() * sum;
            int idx = Arrays.binarySearch(cumulative, r);
            if (idx < 0) idx = -idx - 1;
            if (idx >= messages.size()) idx = messages.size() - 1;
            return messages.get(idx);
        };

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < k; i++) {
            ServiceMessage s = pickOne.get();
            counts.merge(s.getContent(), 1, Integer::sum);
        }

        Map.Entry<String, Integer> best = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        return best == null ? Optional.empty() : Optional.of(best.getKey());
    }

    // 3) Weighted average dla contentów będących liczbami
    public OptionalDouble computeWeightedAverageForNumericValues() {
        double weightedSum = 0;
        double sumWeights = 0;
        for (ServiceMessage m : lastMessages.values()) {
            try {
                double val = Double.parseDouble(m.getContent().trim());
                weightedSum += val * m.getWeight();
                sumWeights += m.getWeight();
            } catch (NumberFormatException e) {
                // pomiń
            }
        }
        if (sumWeights == 0) return OptionalDouble.empty();
        return OptionalDouble.of(weightedSum / sumWeights);
    }

    /* ---------------------- REST endpoint ---------------------- */

    /**
     * GET /vote/result
     * Zwraca JSON z aktualnym wynikiem:
     * - weightedMajority: content z >50% wag (jeśli istnieje)
     * - sampledVote_k3: wynik przybliżony przez sampling k=3
     * - weightedNumericAverage: ważona średnia (jeśli content to liczby)
     * - totalWeight: suma wag
     * - lastMessages: tablica ostatnich wiadomości od satelit
     */
    @GetMapping("/vote/result")
    public Map<String, Object> getVoteResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("weightedMajority", computeWeightedMajority().orElse("NO_MAJORITY"));
        // sampled vote z domyślnym k = 3 (możesz zmienić)
        result.put("sampledVote_k3", computeSampledVote(3).orElse("NO_RESULT"));

        OptionalDouble avg = computeWeightedAverageForNumericValues();
        result.put("weightedNumericAverage", avg.isPresent() ? avg.getAsDouble() : "N/A");

        double totalWeight = lastMessages.values().stream().mapToDouble(ServiceMessage::getWeight).sum();
        result.put("totalWeight", totalWeight);

        result.put("lastMessages", lastMessages.values().stream()
                .map(m -> Map.of(
                        "service", m.getServiceName(),
                        "content", m.getContent(),
                        "weight", m.getWeight()
                ))
                .collect(Collectors.toList())
        );

        return result;
    }
    /* ---------------------- REST endpoint ---------------------- */

    // Opcjonalny scheduler: co 15s logujemy stan (wymaga @EnableScheduling w aplikacji)
    @Scheduled(fixedRate = 15000)
    public void periodicVoteCheck() {
        logger.info("Periodic vote check:");
        logger.info(" Weighted majority (threshold 50%): {}", computeWeightedMajority().orElse("NO MAJORITY"));
        logger.info(" Sampled vote (k=3): {}", computeSampledVote(3).orElse("NO RESULT"));
        computeWeightedAverageForNumericValues().ifPresentOrElse(
                v -> logger.info(" Weighted numeric average: {}", v),
                () -> logger.info(" Weighted numeric average: N/A")
        );
    }



}
