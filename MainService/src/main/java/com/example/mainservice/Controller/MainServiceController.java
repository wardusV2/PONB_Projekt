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

    // Przechowuje ostatnią wiadomość od każdego serwisu
    // klucz: nazwa serwisu, wartość: ostatni ServiceMessage
    private final ConcurrentHashMap<String, ServiceMessage> lastMessages = new ConcurrentHashMap<>();

    /**
     * Odbiera wiadomości WebSocketowe od usług satelitarnych.
     * Endpoint STOMP: /app/from-service
     * Broadcastuje odpowiedź na: /topic/main-broadcast
     */
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

        // Zapisujemy nową wiadomość od serwisu (nadpisujemy poprzednią)
        lastMessages.put(message.getServiceName(), message);

        // Tworzymy odpowiedź do broadcastu WebSocket
        String responseMsg = "MainService ACK: " + message.getContent() + " (from " + message.getServiceName() + ")";
        logger.info(" SENDING RESPONSE: {}", responseMsg);

        return new MainResponse(responseMsg);
    }

    /* -------------------------------------------------------------
     *                     METODY GŁOSOWANIA
     * ------------------------------------------------------------- */

    /**
     * Weighted majority — oblicza, czy jakaś treść (content) przekracza 50% sumy wag.
     * Zwraca Optional.empty(), jeśli brak większości.
     */
    public Optional<String> computeWeightedMajority() {
        Map<String, Double> weightSums = new HashMap<>();

        // Sumujemy wagi według contentu
        lastMessages.values().forEach(msg ->
                weightSums.merge(msg.getContent(), msg.getWeight(), Double::sum)
        );

        double totalWeight = weightSums.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight == 0) return Optional.empty();

        // Znajdujemy content o największej sumie wag
        Map.Entry<String, Double> maxEntry = weightSums.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (maxEntry == null) return Optional.empty();

        // Warunek większości > 50%
        if (maxEntry.getValue() > totalWeight * 0.5) {
            return Optional.of(maxEntry.getKey());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Sampled vote — losujemy k głosów proporcjonalnie do wag.
     * Zwraca content, który najczęściej został wylosowany.
     *
     *
     *
     */
    public Optional<String> computeSampledVote(int k) {

        List<ServiceMessage> messages = new ArrayList<>(lastMessages.values());
        if (messages.isEmpty()) return Optional.empty();

        // --- 1. Normalizacja: ignorujemy wagi ujemne lub NaN ---
        List<ServiceMessage> valid = messages.stream()
                .filter(m -> m.getWeight() > 0 && !Double.isNaN(m.getWeight())).collect(Collectors.toList());

        if (valid.isEmpty()) return Optional.empty();

        // --- 2. Obliczenie sumy wag ---
        double sum = valid.stream().mapToDouble(ServiceMessage::getWeight).sum();
        if (sum <= 0) return Optional.empty();

        // --- 3. Przygotowanie skumulowanej tablicy wag ---
        double[] cumulative = new double[valid.size()];
        double acc = 0;
        for (int i = 0; i < valid.size(); i++) {
            acc += valid.get(i).getWeight();
            cumulative[i] = acc;
        }

        // --- 4. Funkcja wybierająca jedną próbkę (inexact voting core) ---
        java.util.function.Supplier<ServiceMessage> pickOne = () -> {
            double r = ThreadLocalRandom.current().nextDouble() * sum;
            int idx = Arrays.binarySearch(cumulative, r);

            if (idx < 0) idx = -idx - 1;
            if (idx >= valid.size()) idx = valid.size() - 1;

            return valid.get(idx);
        };

        // --- 5. Wykonywanie k losowań ---
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < k; i++) {
            String content = pickOne.get().getContent();
            counts.merge(content, 1, Integer::sum);
        }

        // --- 6. Wybór zwycięzcy ---
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry::getKey)) // stabilne łamanie remisu
                .map(Map.Entry::getKey);
    }


    /**
     * Weighted average — oblicza średnią ważoną, jeśli treści są liczbami.
     * Ignoruje nieliczbowe wiadomości.
     */
    public OptionalDouble computeWeightedAverageForNumericValues() {
        double weightedSum = 0;
        double sumWeights = 0;

        for (ServiceMessage m : lastMessages.values()) {
            try {
                double val = Double.parseDouble(m.getContent().trim());
                weightedSum += val * m.getWeight();
                sumWeights += m.getWeight();
            } catch (NumberFormatException e) {
                // ignorujemy treści nieliczbowe
            }
        }

        if (sumWeights == 0) return OptionalDouble.empty();
        return OptionalDouble.of(weightedSum / sumWeights);
    }

    /* -------------------------------------------------------------
     *                     REST ENDPOINT
     * ------------------------------------------------------------- */

    /**
     * GET /vote/result
     * Zwraca:
     * - wynik majority vote
     * - wynik głosowania próbkującego (k=3)
     * - średnią ważoną liczbową
     * - sumę wag
     * - listę ostatnich wiadomości
     */
    @GetMapping("/vote/result")
    public Map<String, Object> getVoteResult() {
        Map<String, Object> result = new HashMap<>();

        result.put("weightedMajority", computeWeightedMajority().orElse("NO_MAJORITY"));
        result.put("sampledVote_k3", computeSampledVote(3).orElse("NO_RESULT"));

        OptionalDouble avg = computeWeightedAverageForNumericValues();
        result.put("weightedNumericAverage", avg.isPresent() ? avg.getAsDouble() : "N/A");

        // Suma wag wszystkich wiadomości
        double totalWeight = lastMessages.values().stream().mapToDouble(ServiceMessage::getWeight).sum();
        result.put("totalWeight", totalWeight);

        // Zwracamy listę ostatnich wiadomości
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

    /* -------------------------------------------------------------
     *                   OKRESOWY LOGGER STANU
     * ------------------------------------------------------------- */

    /**
     * Co 15 sekund wypisuje wyniki głosowania do logów.
     * Wymaga @EnableScheduling w klasie aplikacji.
     */
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
