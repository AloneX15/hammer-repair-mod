package com.takumistudios.hammer_repair.repair;

import java.util.List;

/** Cálculos puros de un golpe de reparación: cuánto se repara, cuánto cuesta y qué pieza se elige. */
public final class RepairMath {
    private RepairMath() {
    }

    /**
     * Reparación y coste de un golpe.
     *
     * <pre>
     * reparacion = min(desgaste_actual, maximo_por_golpe, experiencia_disponible × eficiencia)
     * coste      = ceil(reparacion / eficiencia)
     * </pre>
     *
     * @param damage      desgaste actual de la pieza (0 = intacta)
     * @param maxPerHit   máximo de durabilidad por golpe
     * @param availableXp puntos de experiencia del reparador
     * @param efficiency  durabilidad reparada por punto de experiencia
     * @param free        modo creativo: repara sin cobrar
     */
    public static Hit computeHit(int damage, int maxPerHit, long availableXp, int efficiency, boolean free) {
        if (damage <= 0 || maxPerHit <= 0 || efficiency <= 0) {
            return Hit.NONE;
        }
        long affordable = free ? Long.MAX_VALUE : Math.max(0, availableXp) * efficiency;
        int repair = (int) Math.min(Math.min(damage, maxPerHit), affordable);
        if (repair <= 0) {
            return Hit.NONE;
        }
        int cost = free ? 0 : (repair + efficiency - 1) / efficiency;
        return new Hit(repair, cost);
    }

    /**
     * Índice de la pieza con menor porcentaje de durabilidad restante, o -1 si ninguna está dañada.
     * La lista va en orden de desempate (casco, pechera, pantalones, botas): ante empate gana la primera.
     */
    public static int selectPiece(List<Piece> pieces) {
        int best = -1;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            if (piece == null || !piece.isDamaged()) {
                continue;
            }
            // Compara remaining/max sin decimales: a/b < c/d  <=>  a*d < c*b
            if (best < 0 || piece.compareRemainingTo(pieces.get(best)) < 0) {
                best = i;
            }
        }
        return best;
    }

    public record Hit(int repair, int cost) {
        public static final Hit NONE = new Hit(0, 0);

        public boolean isEmpty() {
            return repair <= 0;
        }
    }

    /** Estado de durabilidad de una pieza. */
    public record Piece(int damage, int maxDamage) {
        public boolean isDamaged() {
            return maxDamage > 0 && damage > 0;
        }

        int compareRemainingTo(Piece other) {
            long left = (long) (maxDamage - damage) * other.maxDamage;
            long right = (long) (other.maxDamage - other.damage) * maxDamage;
            return Long.compare(left, right);
        }
    }
}
