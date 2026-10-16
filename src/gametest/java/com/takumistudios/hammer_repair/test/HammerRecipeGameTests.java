package com.takumistudios.hammer_repair.test;

import com.takumistudios.hammer_repair.command.HammerRepairCommand;
import com.takumistudios.hammer_repair.config.CommonConfig;
import com.takumistudios.hammer_repair.config.ConfigResult;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.config.RecipeConfig;
import com.takumistudios.hammer_repair.recipe.HammerRecipes;
import com.takumistudios.hammer_repair.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Receta configurable de punta a punta: recarga real de los datos del servidor con distintas configuraciones. Va en su
 * propio entorno porque recargar los datos afecta a todo el servidor; los pasos se ejecutan en orden en una sola prueba.
 */
public class HammerRecipeGameTests {
    private static final String CUSTOM_SHAPELESS = """
            {"schema_version": 2, "enabled": true, "type": "shapeless",
             "ingredients": [{"item": "minecraft:dirt"}, {"tag": "minecraft:planks"}]}
            """;
    private static final String MISSING_MOD_ITEM = """
            {"schema_version": 2, "type": "shaped", "ingredients": [[{"item": "minecraft:stick"}], [{"item": "otro_mod:lingote_especial"}]]}
            """;
    private static final String ALTERNATIVES_WITH_MISSING_MOD = """
            {"schema_version": 2, "type": "shapeless",
             "ingredients": [{"any_of": [{"item": "otro_mod:lingote_especial"}, {"item": "minecraft:stick"}, {"tag": "otro_mod:vacia"}]},
                             {"item": "minecraft:flint"}]}
            """;
    private static final String DISABLED = """
            {"schema_version": 2, "enabled": false, "type": "shapeless", "ingredients": [{"item": "minecraft:dirt"}]}
            """;

    private static final List<Item> DEFAULT_GRID = Arrays.asList(
            Items.NETHERITE_BLOCK, Items.NETHER_STAR, Items.NETHERITE_BLOCK,
            Items.NETHERITE_INGOT, Items.HEAVY_CORE, Items.NETHERITE_INGOT,
            Items.AIR, Items.BLAZE_ROD, Items.AIR);

    private static CraftingInput grid(int width, int height, List<Item> items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : items) {
            stacks.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
        }
        return CraftingInput.of(width, height, stacks);
    }

    /** ¿La mesa de crafteo fabrica el martillo con estos objetos y con nuestra receta? */
    private static boolean crafts(GameTestHelper helper, CraftingInput input) {
        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        return recipe.isPresent() && recipe.get().id().equals(HammerRecipes.RECIPE_KEY)
                && recipe.get().value().assemble(input).is(ModItems.REPAIR_HAMMER);
    }

    private static boolean recipeExists(MinecraftServer server) {
        return server.getRecipeManager().byKey(HammerRecipes.RECIPE_KEY).isPresent();
    }

    private static HammerConfig.Loaded loaded(String recipeJson) {
        return new HammerConfig.Loaded(CommonConfig.parse(CommonConfig.DEFAULT_JSON), RecipeConfig.parse(recipeJson), false, false);
    }

    private static CompletableFuture<Void> reload(MinecraftServer server, Collection<String> packs) {
        return server.reloadResources(packs);
    }

    @GameTest(environment = "hammer_repair:recipes", maxTicks = 3600)
    public void configurableRecipeLifecycle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        ServerPlayer online = TestPlayers.spawn(helper, GameType.SURVIVAL, 1.5, 2, 1.5, 0.0F);
        // El datapack de prueba puede activarse solo al crear el mundo: se parte de los packs sin él
        String overridePack = overridePackId(server);
        List<String> basePacks = new ArrayList<>(server.getPackRepository().getSelectedIds());
        basePacks.remove(overridePack);

        CompletableFuture<?> chain = reload(server, basePacks)
                // 1. Receta predeterminada: fabricación con la mesa y desbloqueada en el libro de recetas
                .thenRun(() -> {
                    assertThat(helper, crafts(helper, grid(3, 3, DEFAULT_GRID)), "la receta predeterminada debe fabricar el martillo");
                    assertThat(helper, online.getRecipeBook().contains(HammerRecipes.RECIPE_KEY), "receta desbloqueada al entrar");
                })
                // 2. Receta personalizada sin forma con etiqueta: sustituye a la anterior
                .thenCompose(ignored -> HammerRepairCommand.apply(source, server, loaded(CUSTOM_SHAPELESS)))
                .thenAccept(outcome -> {
                    assertThat(helper, outcome == HammerRepairCommand.Outcome.APPLIED, "receta personalizada aplicada: " + outcome);
                    assertThat(helper, crafts(helper, grid(2, 1, List.of(Items.OAK_PLANKS, Items.DIRT))), "la receta personalizada fabrica");
                    assertThat(helper, crafts(helper, grid(2, 1, List.of(Items.DIRT, Items.BIRCH_PLANKS))), "la etiqueta acepta cualquier tablón");
                    assertThat(helper, !crafts(helper, grid(3, 3, DEFAULT_GRID)), "la receta anterior ya no existe");
                    assertThat(helper, online.getRecipeBook().contains(HammerRecipes.RECIPE_KEY), "sigue en el libro de quien ya estaba conectado");
                })
                // 3. Recarga inválida (objeto de un mod ausente): se rechaza y se conserva la receta activa
                .thenCompose(ignored -> HammerRepairCommand.apply(source, server, loaded(MISSING_MOD_ITEM)))
                .thenAccept(outcome -> {
                    assertThat(helper, outcome == HammerRepairCommand.Outcome.REJECTED, "configuración inválida rechazada: " + outcome);
                    assertThat(helper, crafts(helper, grid(2, 1, List.of(Items.OAK_PLANKS, Items.DIRT))), "se conserva la última receta válida");
                })
                // 4. Alternativas con mods ausentes: se omiten y la casilla funciona con las disponibles
                .thenCompose(ignored -> HammerRepairCommand.apply(source, server, loaded(ALTERNATIVES_WITH_MISSING_MOD)))
                .thenAccept(outcome -> {
                    assertThat(helper, outcome == HammerRepairCommand.Outcome.APPLIED, "alternativas parciales aplicadas: " + outcome);
                    assertThat(helper, crafts(helper, grid(2, 1, List.of(Items.STICK, Items.FLINT))), "la alternativa vanilla funciona");
                })
                // 5. Receta desactivada: no se puede fabricar, los martillos siguen existiendo
                .thenCompose(ignored -> HammerRepairCommand.apply(source, server, loaded(DISABLED)))
                .thenAccept(outcome -> {
                    assertThat(helper, outcome == HammerRepairCommand.Outcome.APPLIED, "desactivación aplicada");
                    assertThat(helper, !recipeExists(server), "con enabled: false no hay receta generada");
                })
                // 6. Arranque con archivo inválido: sin receta generada y sin receta predeterminada de respaldo
                .thenCompose(ignored -> {
                    HammerConfig.setForTesting(HammerConfig.common(), new HammerConfig.RecipeState(null, List.of()));
                    return reload(server, basePacks);
                })
                .thenRun(() -> assertThat(helper, !recipeExists(server), "un archivo inválido nunca activa la receta predeterminada"))
                // 7. Datapack que define el mismo identificador: tiene prioridad sobre la configuración
                .thenCompose(ignored -> {
                    HammerConfig.setForTesting(HammerConfig.common(), state(RecipeConfig.DEFAULT_JSON));
                    List<String> withOverride = new ArrayList<>(basePacks);
                    withOverride.add(overridePack);
                    return reload(server, withOverride);
                })
                .thenRun(() -> {
                    assertThat(helper, HammerRecipes.isDatapackOverride(), "debe detectarse la sustitución por datapack");
                    assertThat(helper, crafts(helper, grid(1, 1, List.of(Items.COBBLESTONE))), "manda la receta del datapack");
                    assertThat(helper, !crafts(helper, grid(3, 3, DEFAULT_GRID)), "la receta de config queda sustituida");
                })
                // 8. Sin el datapack vuelve la receta de config, y quien entra después la tiene en el libro
                .thenCompose(ignored -> reload(server, basePacks))
                .thenRun(() -> {
                    assertThat(helper, !HammerRecipes.isDatapackOverride(), "sin datapack no hay sustitución");
                    assertThat(helper, crafts(helper, grid(3, 3, DEFAULT_GRID)), "vuelve la receta predeterminada");
                    ServerPlayer late = TestPlayers.spawn(helper, GameType.SURVIVAL, 3.5, 2, 1.5, 0.0F);
                    assertThat(helper, late.getRecipeBook().contains(HammerRecipes.RECIPE_KEY), "desbloqueada para quien entra después");
                    TestPlayers.remove(helper, late);
                });

        chain.whenComplete((ignored, error) -> {
            if (error != null) {
                failure.set(error);
            }
            done.set(true);
        });

        helper.succeedWhen(() -> {
            if (!done.get()) {
                throw helper.assertionException("esperando a las recargas");
            }
            HammerConfig.setForTesting(CommonConfig.DEFAULTS, state(RecipeConfig.DEFAULT_JSON));
            TestPlayers.remove(helper, online);
            Throwable error = failure.get();
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                throw helper.assertionException(String.valueOf(cause.getMessage()));
            }
        });
    }

    private static HammerConfig.RecipeState state(String json) {
        ConfigResult<RecipeConfig> parsed = RecipeConfig.parse(json);
        return new HammerConfig.RecipeState(parsed.orThrow(), parsed.diagnostics());
    }

    private static String overridePackId(MinecraftServer server) {
        return server.getPackRepository().getAvailableIds().stream()
                .filter(id -> id.contains("datapack_override"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró el datapack de prueba: " + server.getPackRepository().getAvailableIds()));
    }

    private static void assertThat(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
