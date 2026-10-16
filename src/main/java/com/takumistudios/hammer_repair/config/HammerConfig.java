package com.takumistudios.hammer_repair.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/**
 * Estado de la configuración de la instancia que ejecuta la partida (servidor dedicado o integrado).
 *
 * <ul>
 *   <li>Al arrancar: si falta un archivo se genera con los valores predeterminados; un {@code recipe.json} inválido
 *       desactiva la receta generada (nunca activa la predeterminada) y se conserva sin tocar.</li>
 *   <li>Al recargar: se valida todo antes de aplicar nada; si algo falla se conserva lo último válido.</li>
 * </ul>
 *
 * <p>Los métodos de carga leen disco: se llaman fuera del hilo principal.
 */
public final class HammerConfig {
    private static volatile Path directory;
    private static volatile CommonConfig common = CommonConfig.DEFAULTS;
    private static volatile RecipeState recipe;

    private HammerConfig() {
    }

    /** Estado de la receta configurable. {@code config} es nulo si el archivo es inválido desde el arranque. */
    public record RecipeState(RecipeConfig config, List<Diagnostic> diagnostics) {
        public RecipeState {
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean generatesRecipe() {
            return config != null && config.enabled();
        }
    }

    /** Resultado de leer y validar los dos archivos, sin aplicarlo todavía. */
    public record Loaded(ConfigResult<CommonConfig> common, ConfigResult<RecipeConfig> recipe,
                         boolean commonMissing, boolean recipeMissing) {
    }

    public static void setDirectory(Path dir) {
        directory = dir;
    }

    public static Path commonFile() {
        return directory.resolve("common.json");
    }

    public static Path recipeFile() {
        return directory.resolve("recipe.json");
    }

    public static CommonConfig common() {
        return common;
    }

    /** Estado actual de la receta, cargándolo del disco si es la primera vez en esta partida. */
    public static RecipeState recipe() {
        RecipeState state = recipe;
        if (state == null) {
            synchronized (HammerConfig.class) {
                if (recipe == null) {
                    loadAtStartup();
                }
                state = recipe;
            }
        }
        return state;
    }

    /** Olvida el estado al cerrar la partida: el siguiente mundo vuelve a leer los archivos. */
    public static synchronized void reset() {
        recipe = null;
        common = CommonConfig.DEFAULTS;
    }

    /** Lee ambos archivos y los valida estructuralmente. No modifica el estado. */
    public static Loaded readFromDisk() {
        ConfigFiles.Read commonRead = ConfigFiles.read(commonFile());
        ConfigFiles.Read recipeRead = ConfigFiles.read(recipeFile());
        return new Loaded(
                parse(commonRead, CommonConfig.DEFAULT_JSON, CommonConfig::parse),
                parse(recipeRead, RecipeConfig.DEFAULT_JSON, RecipeConfig::parse),
                commonRead.status() == ConfigFiles.Read.Status.MISSING,
                recipeRead.status() == ConfigFiles.Read.Status.MISSING);
    }

    private static <T> ConfigResult<T> parse(ConfigFiles.Read read, String defaults,
                                             java.util.function.Function<String, ConfigResult<T>> parser) {
        return switch (read.status()) {
            case MISSING -> parser.apply(defaults);
            case FAILED -> ConfigResult.failure(read.diagnostics());
            case OK -> parser.apply(read.text());
        };
    }

    /** Carga de arranque: genera lo que falte y aplica las reglas de archivo inválido. */
    public static synchronized void loadAtStartup() {
        if (directory == null) {
            throw new IllegalStateException("Directorio de configuración sin inicializar");
        }
        Loaded loaded = readFromDisk();

        if (loaded.commonMissing()) {
            ConfigFiles.writeIfAbsent(commonFile(), CommonConfig.DEFAULT_JSON);
        }
        if (loaded.common().isValid()) {
            common = loaded.common().orThrow();
            log("common.json", loaded.common().diagnostics());
        } else {
            log("common.json", loaded.common().diagnostics());
            // Norma TakumiStudios: copia .bak y regenerar. Si la copia falla, el original no se toca.
            Path backup = ConfigFiles.backup(commonFile());
            if (backup != null && ConfigFiles.writeAtomically(commonFile(), CommonConfig.DEFAULT_JSON)) {
                LOGGER.warn("common.json era inválido: copia en {} y archivo regenerado con valores predeterminados", backup.getFileName());
            } else {
                LOGGER.warn("common.json es inválido: se usan los valores predeterminados en memoria");
            }
            common = CommonConfig.DEFAULTS;
        }

        if (loaded.recipeMissing() && !ConfigFiles.writeIfAbsent(recipeFile(), RecipeConfig.DEFAULT_JSON)) {
            LOGGER.warn("No se pudo crear {}: se usa la receta predeterminada en memoria", recipeFile());
        }
        if (loaded.recipe().isValid()) {
            recipe = new RecipeState(loaded.recipe().orThrow(), loaded.recipe().diagnostics());
            log("recipe.json", loaded.recipe().diagnostics());
        } else {
            // Un archivo existente inválido nunca activa la receta predeterminada: la receta queda desactivada
            recipe = new RecipeState(null, loaded.recipe().diagnostics());
            log("recipe.json", loaded.recipe().diagnostics());
            LOGGER.error("recipe.json es inválido: la receta del martillo queda desactivada hasta corregirlo y ejecutar /hammerrepair reload. "
                    + "El archivo se conserva sin cambios.");
        }
    }

    /** Aplica una configuración ya validada (en el hilo del servidor). */
    public static synchronized void apply(CommonConfig newCommon, RecipeConfig newRecipe, List<Diagnostic> recipeDiagnostics) {
        if (newCommon != null) {
            common = newCommon;
        }
        if (newRecipe != null) {
            recipe = new RecipeState(newRecipe, recipeDiagnostics);
        }
    }

    /** Para gametests: fija el estado sin pasar por el disco. */
    public static synchronized void setForTesting(CommonConfig newCommon, RecipeState newRecipe) {
        common = newCommon;
        recipe = newRecipe;
    }

    public static void log(String file, List<Diagnostic> diagnostics) {
        List<Diagnostic> sorted = new ArrayList<>(diagnostics);
        for (Diagnostic d : sorted) {
            if (d.isError()) {
                LOGGER.error("{}: {}", file, d.describe());
            } else {
                LOGGER.warn("{}: {}", file, d.describe());
            }
        }
    }
}
