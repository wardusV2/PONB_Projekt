package com.example.mainservice;

import com.example.mainservice.Controller.MainServiceController;
import com.example.mainservice.DTO.ServiceMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MainServiceApplicationTests {

    private MainServiceController controller;

    @BeforeEach
    void setup() throws Exception {
        controller = new MainServiceController();

        // --- wstrzyknięcie lastMessages przez refleksję (pole prywatne) ---
        Field field = MainServiceController.class.getDeclaredField("lastMessages");
        field.setAccessible(true);

        ConcurrentHashMap<String, ServiceMessage> map = new ConcurrentHashMap<>();
        map.put("S1", new ServiceMessage("S1", "10", 1.0));
        map.put("S2", new ServiceMessage("S2", "10", 1.0));
        map.put("S3", new ServiceMessage("S3", "20", 1.5));
        map.put("S4", new ServiceMessage("S4", "30", 1.0));
        map.put("S5", new ServiceMessage("S5", "10", 1.0));

        field.set(controller, map);
    }

    @Test
    void testSampledVoteReturnsMostCommonWeightedValue() {
        /*
         * W twojej implementacji losowanie odbywa się poprzez ThreadLocalRandom.current()
         * Nie da się tego stabilnie kontrolować, ale przy małej liczbie danych
         * prawdopodobieństwo, że z 5 elementów (3x "10") metoda zwróci "10"
         * jest > 50% nawet przy k=3.
         *
         * Aby test był deterministyczny – wykonujemy metodę wielokrotnie
         * i sprawdzamy, że najczęściej rezultatem jest "10".
         */

        int hits10 = 0;
        int iterations = 200;

        for (int i = 0; i < iterations; i++) {
            String result = controller.computeSampledVote(3).orElse("NONE");
            if (result.equals("10")) hits10++;
        }

        System.out.println("Wynik testu: hits10 = " + hits10 + " / " + iterations);

        // Wynik "10" powinien dominować (probabilistycznie)
        assertTrue(hits10 > iterations * 0.6,
                "Oczekiwano że '10' pojawi się w większości prób, hits=" + hits10);
    }


}
