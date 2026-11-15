package io.github.keellam4.oritechextensions.test;

import com.google.gson.JsonParser;
import io.github.keellam4.oritechextensions.Config;
import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Every config option has a name and tooltip in the English lang file, so the config screen never shows raw keys. */
@GameTestHolder(OritechExtensions.MODID)
@PrefixGameTestTemplate(false)
public class ConfigGameTests {

    @GameTest(template = "empty_tall")
    public static void everyOptionHasAName(GameTestHelper helper) throws Exception {
        try (var stream = OritechExtensions.class.getResourceAsStream("/assets/oritech_extensions/lang/en_us.json")) {
            if (stream == null) throw new IllegalStateException("en_us.json not found");
            var lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var missing = new ArrayList<String>();
            for (var option : Config.SPEC.getValues().valueMap().keySet()) {
                for (var key : new String[]{"oritech_extensions.configuration." + option, "oritech_extensions.configuration." + option + ".tooltip"}) {
                    if (!lang.has(key)) missing.add(key);
                }
            }
            if (!missing.isEmpty()) helper.fail("missing config names: " + missing);
        }
        helper.succeed();
    }
}
