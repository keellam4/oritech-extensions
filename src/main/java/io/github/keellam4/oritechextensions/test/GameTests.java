package io.github.keellam4.oritechextensions.test;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Registers every {@link ModGameTest} method in this package as a Minecraft game test.
 * <p>
 * Only active when the game is started with this mod's namespace in {@code neoforge.enabledGameTestNamespaces}
 * (the runGameTestServer run), so normal players never get the test functions.
 */
public final class GameTests {

    private static final List<Class<?>> HOLDERS = List.of(
            GuideGameTests.class,
            HangarDoorExtensionGameTests.class,
            PipePaintGameTests.class,
            PaintTargetGameTests.class);

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, OritechExtensions.MODID);

    private record Test(String name, int timeoutTicks) {
    }

    private static final List<Test> TESTS = new ArrayList<>();

    private GameTests() {
    }

    public static void register(IEventBus modBus) {
        var enabled = System.getProperty("neoforge.enabledGameTestNamespaces", "");
        if (!List.of(enabled.split(",")).contains(OritechExtensions.MODID)) return;

        for (var holder : HOLDERS) {
            for (var method : holder.getDeclaredMethods()) {
                var annotation = method.getAnnotation(ModGameTest.class);
                if (annotation == null) continue;
                if (!Modifier.isStatic(method.getModifiers())) throw new IllegalStateException(method + " must be static");
                var name = snakeCase(holder.getSimpleName().replace("GameTests", "")) + "/" + snakeCase(method.getName());
                FUNCTIONS.register(name, () -> helper -> invoke(method, helper));
                TESTS.add(new Test(name, annotation.timeoutTicks()));
            }
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(GameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(OritechExtensions.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        var structure = OritechExtensions.id("empty_tall"); // 5 x 20 x 5 of air
        for (var test : TESTS) {
            var id = OritechExtensions.id(test.name());
            var function = ResourceKey.create(Registries.TEST_FUNCTION, id);
            event.registerTest(id, new FunctionGameTestInstance(function,
                    new TestData<>(environment, structure, test.timeoutTicks(), 0, true)));
        }
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new RuntimeException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static String snakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
