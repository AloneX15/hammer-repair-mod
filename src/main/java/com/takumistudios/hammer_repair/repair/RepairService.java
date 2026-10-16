package com.takumistudios.hammer_repair.repair;

import com.takumistudios.hammer_repair.config.CommonConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Transacción de un golpe: elige la pieza, calcula la reparación que se puede pagar, la aplica y cobra la experiencia
 * al reparador. Todo en el hilo del servidor y sobre el equipo actual (nunca sobre referencias antiguas).
 */
public final class RepairService {
    private RepairService() {
    }

    public enum Outcome { REPAIRED, NOTHING_TO_REPAIR, NO_XP }

    /** Resultado de un golpe; {@code repairerOutOfXp} indica que tras cobrar ya no queda experiencia. */
    public record HitResult(Outcome outcome, int repaired, int cost, boolean moreToRepair, boolean repairerOutOfXp) {
        static HitResult of(Outcome outcome) {
            return new HitResult(outcome, 0, 0, false, false);
        }
    }

    /** Puntos de experiencia del jugador (nivel + barra). */
    public static long availableXp(Player player) {
        return XpMath.totalPoints(player.experienceLevel, player.experienceProgress);
    }

    public static boolean repairsForFree(Player player) {
        return player.isCreative();
    }

    public static boolean canPayAnything(Player player) {
        return repairsForFree(player) || availableXp(player) > 0;
    }

    /** Golpe sobre la armadura equipada de {@code target}. */
    public static HitResult hitTarget(ServerPlayer repairer, ServerPlayer target, CommonConfig config) {
        EquipmentSlot slot = ArmorPieces.mostWorn(target);
        if (slot == null) {
            return HitResult.of(Outcome.NOTHING_TO_REPAIR);
        }
        ItemStack stack = ArmorPieces.worn(target, slot);
        HitResult result = repairStack(repairer, stack, config);
        if (result.outcome() == Outcome.REPAIRED) {
            playEffects(repairer, InteractionHand.MAIN_HAND, piecePosition(target, slot));
            result = new HitResult(result.outcome(), result.repaired(), result.cost(), ArmorPieces.hasDamagedArmor(target),
                    result.repairerOutOfXp());
        }
        return result;
    }

    /** Golpe sobre la pieza que el reparador sostiene en la mano principal (martillo en la otra mano). */
    public static HitResult hitHeld(ServerPlayer repairer, CommonConfig config) {
        ItemStack stack = repairer.getMainHandItem();
        if (!ArmorPieces.isRepairable(stack)) {
            return HitResult.of(Outcome.NOTHING_TO_REPAIR);
        }
        HitResult result = repairStack(repairer, stack, config);
        if (result.outcome() == Outcome.REPAIRED) {
            Vec3 eye = repairer.getEyePosition();
            playEffects(repairer, InteractionHand.OFF_HAND, eye.add(repairer.getViewVector(1.0F).scale(0.6)).add(0, -0.35, 0));
            result = new HitResult(result.outcome(), result.repaired(), result.cost(), stack.isDamaged(), result.repairerOutOfXp());
        }
        return result;
    }

    /** Repara solo el daño de la pieza: encantamientos, nombre y demás componentes quedan intactos. */
    private static HitResult repairStack(ServerPlayer repairer, ItemStack stack, CommonConfig config) {
        boolean free = repairsForFree(repairer);
        RepairMath.Hit hit = RepairMath.computeHit(stack.getDamageValue(), config.maxRepairPerHit(), availableXp(repairer),
                config.durabilityPerXp(), free);
        if (hit.isEmpty()) {
            // Sin daño no se cobra nunca; con daño y sin experiencia no se puede pagar nada
            return HitResult.of(stack.isDamaged() ? Outcome.NO_XP : Outcome.NOTHING_TO_REPAIR);
        }
        stack.setDamageValue(stack.getDamageValue() - hit.repair());
        if (hit.cost() > 0) {
            chargeXp(repairer, hit.cost());
        }
        return new HitResult(Outcome.REPAIRED, hit.repair(), hit.cost(), stack.isDamaged(), !canPayAnything(repairer));
    }

    /** Descuenta puntos exactos y actualiza nivel y barra sin negativos ni deriva por redondeo. */
    public static void chargeXp(ServerPlayer player, int cost) {
        XpMath.Levelled after = XpMath.subtract(player.experienceLevel, player.experienceProgress, cost);
        player.setExperienceLevels(after.level());
        player.setExperiencePoints(after.points());
        player.totalExperience = Math.max(0, player.totalExperience - cost);
    }

    private static Vec3 piecePosition(Player target, EquipmentSlot slot) {
        double height = switch (slot) {
            case HEAD -> target.getBbHeight() * 0.88;
            case CHEST -> target.getBbHeight() * 0.62;
            case LEGS -> target.getBbHeight() * 0.38;
            default -> target.getBbHeight() * 0.08;
        };
        return target.position().add(0, height, 0);
    }

    /** Golpe visible para todos (brazo), sonido suave inspirado en el yunque y unas pocas chispas. */
    private static void playEffects(ServerPlayer repairer, InteractionHand hand, Vec3 at) {
        Versions.swingHammer(repairer, hand);
        ServerLevel level = (ServerLevel) repairer.level();
        float pitch = 1.35F + repairer.getRandom().nextFloat() * 0.2F;
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.25F, pitch);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.12, 0.12, 0.12, 0.05);
    }
}
