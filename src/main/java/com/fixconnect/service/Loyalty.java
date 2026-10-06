package com.fixconnect.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Loyalty programme rules: 10 points per Rs.100 spent; tier by lifetime points. */
public final class Loyalty {

    private Loyalty() {
    }

    public static final String EARNING_RULE = "10 points earned per ₹100 spent.";

    public static int pointsFor(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        return amount.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue() * 10;
    }

    public static String tier(int lifetimePoints) {
        if (lifetimePoints >= 1000) return "Platinum Member";
        if (lifetimePoints >= 200) return "Gold Member";
        if (lifetimePoints >= 100) return "Silver Member";
        return "Bronze Member";
    }

    public static String nextTier(int lifetimePoints) {
        if (lifetimePoints >= 1000) return null;
        if (lifetimePoints >= 200) return "Platinum Member";
        if (lifetimePoints >= 100) return "Gold Member";
        return "Silver Member";
    }

    public static int pointsToNext(int lifetimePoints) {
        if (lifetimePoints >= 1000) return 0;
        if (lifetimePoints >= 200) return 1000 - lifetimePoints;
        if (lifetimePoints >= 100) return 200 - lifetimePoints;
        return 100 - lifetimePoints;
    }
}
