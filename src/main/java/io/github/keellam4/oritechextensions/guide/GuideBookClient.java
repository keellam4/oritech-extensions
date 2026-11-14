package io.github.keellam4.oritechextensions.guide;

import io.github.keellam4.oritechextensions.OritechExtensions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.net.URI;

/**
 * Client-only half of the guide book. Talks to Oracle Index by reflection so the mod has no compile-time
 * dependency on it and keeps working (with a web link) when it isn't installed.
 */
public final class GuideBookClient {

    /** Folder name under {@code assets/oracle_index/books/}; build.gradle copies {@code wiki/} there. */
    public static final String WIKI_ID = OritechExtensions.MODID;
    /** Page shown the first time the book is opened each session. */
    public static final Identifier START_PAGE =
            Identifier.fromNamespaceAndPath("oracle_index", "books/" + WIKI_ID + "/docs/intro.mdx");
    /** GitHub Pages site built from the same {@code wiki/} folder. */
    public static final String WEB_URL = "https://keellam4.github.io/oritech-extensions/";

    private static final String ORACLE_MOD_ID = "oracle_index";
    private static boolean openedThisSession;
    private static Method openScreen;

    private GuideBookClient() {}

    static void open(Player player) {
        if (ModList.get().isLoaded(ORACLE_MOD_ID) && openInOracle()) {
            return;
        }
        player.sendSystemMessage(webLinkMessage());
    }

    /** Calls {@code rearth.oracle.OracleClient.openScreen(String, Identifier, Screen)}. */
    private static boolean openInOracle() {
        try {
            if (openScreen == null) {
                openScreen = Class.forName("rearth.oracle.OracleClient")
                        .getMethod("openScreen", String.class, Identifier.class, Screen.class);
            }
            // After the first open, pass null so Oracle Index returns to the page the player was reading.
            Identifier entry = openedThisSession ? null : START_PAGE;
            openScreen.invoke(null, WIKI_ID, entry, Minecraft.getInstance().screen);
            openedThisSession = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            OritechExtensions.LOGGER.warn("Couldn't open the guide in Oracle Index; showing the web link instead.", e);
            return false;
        }
    }

    private static Component webLinkMessage() {
        Component link = Component.literal(WEB_URL).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(WEB_URL)))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.translatable("message.oritech_extensions.guide.open_web"))));
        return Component.translatable("message.oritech_extensions.guide.no_oracle", link);
    }
}
