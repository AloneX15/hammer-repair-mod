package com.takumistudios.hammer_repair.repair;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairMathTest {
    private static final int EFFICIENCY = 2;
    private static final int MAX_PER_HIT = 20;

    @Test
    void specExamples() {
        assertEquals(new RepairMath.Hit(20, 10), RepairMath.computeHit(100, MAX_PER_HIT, 1000, EFFICIENCY, false));
        assertEquals(new RepairMath.Hit(6, 3), RepairMath.computeHit(6, MAX_PER_HIT, 1000, EFFICIENCY, false));
        // Solo falta 1 punto: cuesta 1 de experiencia y el resto no se transfiere
        assertEquals(new RepairMath.Hit(1, 1), RepairMath.computeHit(1, MAX_PER_HIT, 1000, EFFICIENCY, false));
    }

    @Test
    void partialRepairWithLittleXp() {
        // 3 puntos de experiencia con eficiencia 2 pagan 6 de durabilidad
        assertEquals(new RepairMath.Hit(6, 3), RepairMath.computeHit(100, MAX_PER_HIT, 3, EFFICIENCY, false));
        assertEquals(new RepairMath.Hit(2, 1), RepairMath.computeHit(100, MAX_PER_HIT, 1, EFFICIENCY, false));
    }

    @Test
    void noXpOrNoDamageRepairsNothingAndChargesNothing() {
        assertTrue(RepairMath.computeHit(100, MAX_PER_HIT, 0, EFFICIENCY, false).isEmpty());
        assertEquals(RepairMath.Hit.NONE, RepairMath.computeHit(0, MAX_PER_HIT, 1000, EFFICIENCY, false));
        assertEquals(0, RepairMath.computeHit(0, MAX_PER_HIT, 1000, EFFICIENCY, false).cost());
    }

    @Test
    void costIsCeilingOfRepairOverEfficiency() {
        for (int efficiency = 1; efficiency <= 100; efficiency++) {
            for (int damage = 1; damage <= 50; damage++) {
                RepairMath.Hit hit = RepairMath.computeHit(damage, 1000, 1_000_000, efficiency, false);
                assertEquals(damage, hit.repair());
                assertEquals((damage + efficiency - 1) / efficiency, hit.cost());
            }
        }
    }

    @Test
    void creativeIsFree() {
        assertEquals(new RepairMath.Hit(20, 0), RepairMath.computeHit(100, MAX_PER_HIT, 0, EFFICIENCY, true));
    }

    @Test
    void selectsLowestRemainingPercentage() {
        List<RepairMath.Piece> pieces = List.of(
                new RepairMath.Piece(10, 100),   // 90 %
                new RepairMath.Piece(300, 500),  // 40 %
                new RepairMath.Piece(50, 100),   // 50 %
                new RepairMath.Piece(0, 100));   // intacta
        assertEquals(1, RepairMath.selectPiece(pieces));
    }

    @Test
    void tiesFollowHelmetChestLegsBootsOrder() {
        List<RepairMath.Piece> pieces = List.of(
                new RepairMath.Piece(0, 100),
                new RepairMath.Piece(40, 80),    // 50 %
                new RepairMath.Piece(25, 50),    // 50 %
                new RepairMath.Piece(100, 200)); // 50 %
        assertEquals(1, RepairMath.selectPiece(pieces));
    }

    @Test
    void emptySlotsAndIntactPiecesAreSkipped() {
        assertEquals(-1, RepairMath.selectPiece(Arrays.asList(null, new RepairMath.Piece(0, 10), null, null)));
        assertEquals(3, RepairMath.selectPiece(Arrays.asList(null, null, null, new RepairMath.Piece(1, 10))));
    }
}
