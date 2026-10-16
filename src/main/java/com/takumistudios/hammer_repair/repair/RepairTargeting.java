package com.takumistudios.hammer_repair.repair;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Apuntado al compañero. Lo usan igual el cliente (para no mostrar un uso que el servidor rechazaría) y el servidor
 * (que es quien decide): rayo desde los ojos, alcance máximo y sin bloques en medio.
 */
public final class RepairTargeting {
    /** Margen al seguir apuntando durante la sesión, para que el temblor normal del ratón no interrumpa. */
    private static final double AIM_TOLERANCE = 0.3;

    private RepairTargeting() {
    }

    /**
     * Jugador al que apunta {@code user} dentro de {@code reach} bloques con línea de visión.
     *
     * @param inflate margen de las cajas de colisión (el cliente usa uno mayor para no ser más estricto que el servidor)
     */
    public static Optional<Player> findTarget(Player user, double reach, double inflate) {
        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getViewVector(1.0F).scale(reach));
        double blockDistance = blockDistanceSqr(user, eye, end);

        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = new AABB(eye, end).inflate(1.0);
        for (Player other : user.level().getEntitiesOfClass(Player.class, search, p -> isValidTarget(user, p))) {
            Optional<Vec3> hit = other.getBoundingBox().inflate(inflate).clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(hit.get());
            if (distance < bestDistance && distance <= blockDistance) {
                best = other;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Comprobación continua: sigue apuntando al mismo jugador, dentro del alcance y sin obstáculos. */
    public static boolean stillTargeting(Player user, Player target, double reach) {
        if (!isValidTarget(user, target)) {
            return false;
        }
        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getViewVector(1.0F).scale(reach));
        Optional<Vec3> hit = target.getBoundingBox().inflate(AIM_TOLERANCE).clip(eye, end);
        if (hit.isEmpty()) {
            // Ojos dentro de la caja del otro (pegados): cuenta como apuntado
            return target.getBoundingBox().inflate(AIM_TOLERANCE).contains(eye);
        }
        return eye.distanceToSqr(hit.get()) <= blockDistanceSqr(user, eye, end) && user.hasLineOfSight(target);
    }

    public static boolean isValidTarget(Player user, Player other) {
        return other != user && other.isAlive() && !other.isRemoved() && !other.isSpectator() && other.level() == user.level();
    }

    private static double blockDistanceSqr(Player user, Vec3 eye, Vec3 end) {
        BlockHitResult block = user.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceToSqr(block.getLocation());
    }
}
