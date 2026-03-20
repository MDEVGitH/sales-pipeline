package com.crm.qualifier.adapter.outbound.score;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class RandomScoreAdapterTest {

    @Test
    void shouldGenerateScoreInRange() {
        RandomScoreAdapter adapter = new RandomScoreAdapter();
        for (int i = 0; i < 1000; i++) {
            int score = adapter.generateScore();
            assertTrue(score >= 0 && score <= 100,
                    "Score must be 0-100, got: " + score);
        }
    }

    @Test
    void shouldProduceDeterministicScore_withSeed() {
        RandomScoreAdapter adapter1 = new RandomScoreAdapter(42L);
        RandomScoreAdapter adapter2 = new RandomScoreAdapter(42L);
        assertEquals(adapter1.generateScore(), adapter2.generateScore());
    }
}
