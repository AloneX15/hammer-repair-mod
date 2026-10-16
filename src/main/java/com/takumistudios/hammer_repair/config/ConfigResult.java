package com.takumistudios.hammer_repair.config;

import java.util.List;
import java.util.Optional;

/** Valor validado más los avisos y errores encontrados. Sin errores, {@link #value()} está presente. */
public record ConfigResult<T>(Optional<T> value, List<Diagnostic> diagnostics) {
    public ConfigResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public static <T> ConfigResult<T> of(T value, List<Diagnostic> diagnostics) {
        boolean failed = diagnostics.stream().anyMatch(Diagnostic::isError);
        return new ConfigResult<>(failed ? Optional.empty() : Optional.of(value), diagnostics);
    }

    public static <T> ConfigResult<T> failure(List<Diagnostic> diagnostics) {
        return new ConfigResult<>(Optional.empty(), diagnostics);
    }

    public boolean isValid() {
        return value.isPresent();
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> !d.isError()).toList();
    }

    public T orThrow() {
        return value.orElseThrow(() -> new IllegalStateException("Configuración inválida: " + errors()));
    }
}
