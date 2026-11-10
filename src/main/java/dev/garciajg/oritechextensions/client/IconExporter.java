package dev.garciajg.oritechextensions.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.garciajg.oritechextensions.OritechExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev/CI tool for the docs site: renders items exactly as they look in the inventory (3D blocks, GeckoLib
 * models and all) and saves them as PNGs. Used by the clientIconExport run, never in normal play.
 *
 * <p>Set {@code -Doritech_extensions.exportIcons=<path to items.txt>} (one item id per line, written by
 * {@code tools/wiki/build_site.py --list-items}). Icons go next to it as {@code <namespace>/<path>.png}.
 */
final class IconExporter {

    static final String PROPERTY = "oritech_extensions.exportIcons";
    private static final int SIZE = 128;
    private static boolean done; // the quit tick can fire again before the game closes

    private IconExporter() {}

    static boolean requested() {
        return System.getProperty(PROPERTY) != null;
    }

    /** Must run on the render thread, with resources loaded. */
    static void export() {
        if (done) return;
        done = true;
        Path list = Path.of(System.getProperty(PROPERTY));
        List<String> ids;
        try {
            ids = Files.readAllLines(list).stream().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
        } catch (IOException e) {
            OritechExtensions.LOGGER.error("[icons] Can't read item list {}", list, e);
            return;
        }
        Path outDir = list.toAbsolutePath().getParent();
        Minecraft mc = Minecraft.getInstance();
        List<String> missing = new ArrayList<>();
        int written = 0;

        var target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        // Same projection the GUI uses, scaled so one 16x16 item slot fills the whole image.
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0, 0, -11000f);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        try {
            for (String id : ids) {
                var key = ResourceLocation.tryParse(id);
                var item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
                if (item == null) {
                    missing.add(id);
                    continue;
                }
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                graphics.renderItem(new ItemStack(item), 0, 0);
                graphics.flush();
                try (var image = new NativeImage(SIZE, SIZE, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId());
                    image.downloadTexture(0, false);
                    image.flipY();
                    Path out = outDir.resolve(key.getNamespace()).resolve(key.getPath() + ".png");
                    Files.createDirectories(out.getParent());
                    image.writeToFile(out);
                    written++;
                } catch (IOException e) {
                    OritechExtensions.LOGGER.error("[icons] Couldn't save {}", id, e);
                }
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
        OritechExtensions.LOGGER.info("[icons] Exported {} item icons to {}{}", written, outDir,
                missing.isEmpty() ? "" : "; unknown items: " + String.join(", ", missing));
    }
}
