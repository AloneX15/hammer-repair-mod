package com.takumistudios.hammer_repair.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/**
 * Lectura y escritura de los archivos de configuración. Sin dependencias de Minecraft para poder probarla; los
 * llamadores la ejecutan fuera del hilo principal.
 */
public final class ConfigFiles {
    private ConfigFiles() {
    }

    /** Resultado de leer un archivo: su texto, o que no existe, o un error de lectura ya diagnosticado. */
    public record Read(Status status, String text, List<Diagnostic> diagnostics) {
        public enum Status { OK, MISSING, FAILED }
    }

    public static Read read(Path file) {
        if (!Files.exists(file)) {
            return new Read(Read.Status.MISSING, null, List.of());
        }
        List<Diagnostic> out = new ArrayList<>();
        try {
            long size = Files.size(file);
            if (size > JsonSupport.MAX_FILE_BYTES) {
                out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "too_large", size, JsonSupport.MAX_FILE_BYTES));
                return new Read(Read.Status.FAILED, null, out);
            }
            byte[] bytes;
            try (InputStream in = Files.newInputStream(file)) {
                // Nunca más de lo permitido aunque el archivo crezca entre la comprobación y la lectura
                bytes = in.readNBytes(JsonSupport.MAX_FILE_BYTES + 1);
            }
            if (bytes.length > JsonSupport.MAX_FILE_BYTES) {
                out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "too_large", bytes.length, JsonSupport.MAX_FILE_BYTES));
                return new Read(Read.Status.FAILED, null, out);
            }
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
            if (text.startsWith("﻿")) {
                text = text.substring(1); // BOM de algunos editores de Windows
            }
            return new Read(Read.Status.OK, text, out);
        } catch (CharacterCodingException e) {
            out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "read_error", "UTF-8 no válido"));
        } catch (IOException | SecurityException e) {
            out.add(Diagnostic.error(Diagnostic.Category.SYNTAX, Diagnostic.Location.FILE, "read_error", e.getMessage()));
        }
        return new Read(Read.Status.FAILED, null, out);
    }

    /** Escritura atómica: archivo temporal y renombrado. Devuelve {@code false} si no se pudo escribir. */
    public static boolean writeAtomically(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | SecurityException e) {
            LOGGER.warn("No se pudo escribir {}: {}", file, e.toString());
            return false;
        }
    }

    /** Escribe el archivo solo si no existe, sin reemplazar nunca uno del usuario. */
    public static boolean writeIfAbsent(Path file, String text) {
        if (Files.exists(file)) {
            return false;
        }
        return writeAtomically(file, text);
    }

    /**
     * Copia de diagnóstico con sufijo único ({@code nombre.AAAAMMDD-HHMMSS[-n].bak}). El original no se toca.
     *
     * @return la ruta de la copia, o {@code null} si no se pudo crear
     */
    public static Path backup(Path file) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = file.resolveSibling(file.getFileName() + "." + stamp + ".bak");
            for (int n = 1; Files.exists(target) && n < 1000; n++) {
                target = file.resolveSibling(file.getFileName() + "." + stamp + "-" + n + ".bak");
            }
            Files.copy(file, target);
            return target;
        } catch (IOException | SecurityException e) {
            LOGGER.warn("No se pudo crear la copia de seguridad de {}: {}", file, e.toString());
            return null;
        }
    }
}
