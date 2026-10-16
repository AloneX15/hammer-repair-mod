package com.takumistudios.hammer_repair.repair;

/**
 * Aritmética de puntos de experiencia de vanilla, sin dependencias de Minecraft para poder probarla con JUnit.
 *
 * <p>El juego guarda la experiencia como nivel + progreso (float de 0 a 1). Aquí se trabaja con puntos enteros dentro
 * del nivel para descontar sin valores negativos ni pérdidas por redondeo de la barra. Los niveles absurdos que
 * permite {@code /xp} (hasta {@code Integer.MAX_VALUE}) no desbordan: los totales se saturan.
 */
public final class XpMath {
    private XpMath() {
    }

    /** Puntos necesarios para pasar de {@code level} a {@code level + 1} (igual que {@code Player#getXpNeededForNextLevel}). */
    public static int xpNeededForNextLevel(int level) {
        if (level >= 30) {
            long needed = 112L + (long) (level - 30) * 9;
            return (int) Math.min(Integer.MAX_VALUE, needed);
        }
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /** Puntos acumulados al llegar al inicio de {@code level}, saturado a {@code Long.MAX_VALUE}. */
    public static long pointsAtLevelStart(int level) {
        long l = Math.max(0, level);
        try {
            if (l <= 16) {
                return l * l + 6 * l;
            }
            if (l <= 31) {
                return (5 * l * l - 81 * l + 720) / 2;
            }
            return Math.addExact(Math.multiplyExact(Math.multiplyExact(9L, l), l), 4440L - 325L * l) / 2;
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /** Puntos dentro del nivel actual a partir del progreso de la barra, acotados a [0, necesarios - 1]. */
    public static int pointsIntoLevel(int level, float progress) {
        int needed = xpNeededForNextLevel(Math.max(0, level));
        if (!(progress > 0.0F)) {
            return 0; // también cubre NaN
        }
        long points = Math.round((double) progress * needed);
        return (int) Math.max(0, Math.min(needed - 1L, points));
    }

    /** Experiencia total disponible en puntos (saturada). */
    public static long totalPoints(int level, float progress) {
        long start = pointsAtLevelStart(level);
        return start == Long.MAX_VALUE ? Long.MAX_VALUE : start + pointsIntoLevel(level, progress);
    }

    /** Nivel y puntos dentro del nivel que corresponden a un total de puntos. */
    public static Levelled fromTotal(long total) {
        long remaining = Math.max(0, total);
        // Búsqueda binaria del mayor nivel cuyo inicio no supera el total
        int low = 0;
        int high = Integer.MAX_VALUE - 1;
        while (low < high) {
            int mid = (int) (((long) low + high + 1) >>> 1);
            if (pointsAtLevelStart(mid) <= remaining) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return new Levelled(low, (int) Math.min(Integer.MAX_VALUE, remaining - pointsAtLevelStart(low)));
    }

    /**
     * Resultado de quitar {@code cost} puntos a un jugador con ese nivel y progreso. Baja nivel a nivel (el coste de
     * un golpe es pequeño), así que funciona igual con niveles enormes.
     */
    public static Levelled subtract(int level, float progress, int cost) {
        int currentLevel = Math.max(0, level);
        long points = pointsIntoLevel(currentLevel, progress);
        long remaining = Math.max(0, cost);
        while (remaining > points) {
            if (currentLevel == 0) {
                return new Levelled(0, 0);
            }
            remaining -= points + 1; // +1: bajar de nivel consume el primer punto del nivel actual
            currentLevel--;
            points = xpNeededForNextLevel(currentLevel) - 1L;
        }
        return new Levelled(currentLevel, (int) (points - remaining));
    }

    public record Levelled(int level, int points) {
    }
}
