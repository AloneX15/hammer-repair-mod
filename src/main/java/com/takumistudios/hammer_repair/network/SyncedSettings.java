package com.takumistudios.hammer_repair.network;

import com.takumistudios.hammer_repair.config.CommonConfig;

/**
 * Ajustes del servidor que el cliente necesita para predecir el uso del martillo (el alcance) y para el tooltip (el
 * coste). El cliente nunca decide nada con ellos: solo evita mostrar un uso que el servidor no haría.
 */
public final class SyncedSettings {
    /**
     * Margen extra del cliente: si su predicción fuese más estricta que el servidor, este podría empezar una sesión
     * sin que el cliente supiera que está usando el martillo (y no enviaría el aviso de soltar el botón).
     */
    public static final double CLIENT_REACH_MARGIN = 0.5;

    /** Margen extra de las cajas de colisión al predecir el objetivo en el cliente. */
    public static final double CLIENT_AIM_MARGIN = 0.3;

    private static volatile double clientMaxDistance = CommonConfig.DEFAULTS.maxDistance();

    private static volatile int clientDurabilityPerXp;

    private SyncedSettings() {
    }

    public static double clientMaxDistance() {
        return clientMaxDistance;
    }

    public static void setClientMaxDistance(double distance) {
        clientMaxDistance = Math.max(1.0, Math.min(6.0, distance));
    }

    /** Durabilidad por punto de experiencia del servidor, o 0 si no se conoce (menú principal o servidor 1.0.x). */
    public static int clientDurabilityPerXp() {
        return clientDurabilityPerXp;
    }

    public static void setClientDurabilityPerXp(int durabilityPerXp) {
        clientDurabilityPerXp = Math.max(1, Math.min(100, durabilityPerXp));
    }

    public static void resetClient() {
        clientMaxDistance = CommonConfig.DEFAULTS.maxDistance();
        clientDurabilityPerXp = 0;
    }
}
