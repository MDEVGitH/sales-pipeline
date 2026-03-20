package com.crm.qualifier.adapter.outbound.score;

import com.crm.qualifier.application.port.outbound.QualificationScorePort;

import java.util.Random;

public class RandomScoreAdapter implements QualificationScorePort {

    private final Random random;

    public RandomScoreAdapter() {
        this.random = new Random();
    }

    public RandomScoreAdapter(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int generateScore() {
        return random.nextInt(101); // 0-100 inclusive
    }
}
