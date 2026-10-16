package com.takumistudios.hammer_repair.repair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpMathTest {
    @Test
    void levelStartMatchesSumOfNeededPoints() {
        long sum = 0;
        for (int level = 0; level <= 200; level++) {
            assertEquals(sum, XpMath.pointsAtLevelStart(level), "inicio del nivel " + level);
            sum += XpMath.xpNeededForNextLevel(level);
        }
    }

    @Test
    void neededPointsMatchVanillaBrackets() {
        assertEquals(7, XpMath.xpNeededForNextLevel(0));
        assertEquals(35, XpMath.xpNeededForNextLevel(14));
        assertEquals(37, XpMath.xpNeededForNextLevel(15));
        assertEquals(107, XpMath.xpNeededForNextLevel(29));
        assertEquals(112, XpMath.xpNeededForNextLevel(30));
    }

    @Test
    void fromTotalIsInverseOfTotal() {
        for (int level = 0; level <= 120; level++) {
            int needed = XpMath.xpNeededForNextLevel(level);
            for (int points = 0; points < needed; points += Math.max(1, needed / 7)) {
                long total = XpMath.pointsAtLevelStart(level) + points;
                assertEquals(new XpMath.Levelled(level, points), XpMath.fromTotal(total));
            }
        }
    }

    @Test
    void progressRoundingNeverReachesNextLevel() {
        // 0.99999 de la barra en nivel 0 (7 puntos): 6 puntos, nunca 7
        assertEquals(6, XpMath.pointsIntoLevel(0, 0.99999F));
        assertEquals(3, XpMath.pointsIntoLevel(0, 3.0F / 7.0F));
        assertEquals(0, XpMath.pointsIntoLevel(5, -0.2F));
        assertEquals(0, XpMath.pointsIntoLevel(5, Float.NaN));
    }

    @Test
    void subtractCrossesLevelsWithoutLoss() {
        // Nivel 1 con 4 de 9 puntos = 7 + 4 = 11 puntos; quitar 10 deja 1 punto en nivel 0
        assertEquals(new XpMath.Levelled(0, 1), XpMath.subtract(1, 4.0F / 9.0F, 10));
        // Quitar exactamente todo deja 0/0
        assertEquals(new XpMath.Levelled(0, 0), XpMath.subtract(1, 4.0F / 9.0F, 11));
    }

    @Test
    void subtractNeverGoesNegative() {
        assertEquals(new XpMath.Levelled(0, 0), XpMath.subtract(0, 0.5F, 1000));
        assertEquals(new XpMath.Levelled(0, 0), XpMath.subtract(0, 0.0F, 1));
    }

    @Test
    void repeatedChargesMatchSingleCharge() {
        // Diez golpes de 10 puntos desde nivel 30 dejan lo mismo que quitar 100 de una vez (sin deriva de la barra)
        int level = 30;
        int points = 50;
        for (int i = 0; i < 10; i++) {
            float progress = (float) points / XpMath.xpNeededForNextLevel(level);
            XpMath.Levelled after = XpMath.subtract(level, progress, 10);
            level = after.level();
            points = after.points();
        }
        long expected = XpMath.pointsAtLevelStart(30) + 50 - 100;
        assertEquals(expected, XpMath.pointsAtLevelStart(level) + points);
    }

    @Test
    void hugeLevelsDoNotOverflow() {
        XpMath.Levelled result = XpMath.fromTotal(XpMath.pointsAtLevelStart(100_000) + 5);
        assertEquals(100_000, result.level());
        assertEquals(5, result.points());
        assertTrue(XpMath.totalPoints(Integer.MAX_VALUE / 2, 0.5F) > 0);
        assertEquals(Long.MAX_VALUE, XpMath.totalPoints(Integer.MAX_VALUE, 0.0F));
        // Con /xp se puede llegar a niveles enormes: cobrar sigue siendo exacto
        assertEquals(new XpMath.Levelled(Integer.MAX_VALUE - 1, XpMath.xpNeededForNextLevel(Integer.MAX_VALUE - 1) - 10),
                XpMath.subtract(Integer.MAX_VALUE, 0.0F, 10));
    }

    @Test
    void subtractMatchesTotalsForEveryCost() {
        for (int level = 0; level <= 40; level++) {
            int needed = XpMath.xpNeededForNextLevel(level);
            for (int points = 0; points < needed; points += 3) {
                float progress = (float) points / needed;
                for (int cost = 0; cost <= 1000; cost += 37) {
                    long expected = Math.max(0, XpMath.pointsAtLevelStart(level) + points - cost);
                    assertEquals(XpMath.fromTotal(expected), XpMath.subtract(level, progress, cost),
                            "nivel " + level + ", puntos " + points + ", coste " + cost);
                }
            }
        }
    }
}
