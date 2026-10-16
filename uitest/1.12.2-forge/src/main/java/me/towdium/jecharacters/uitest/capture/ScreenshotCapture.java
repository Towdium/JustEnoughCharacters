package me.towdium.jecharacters.uitest.capture;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ScreenshotCapture {

    public static final String kind = "FULL";

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private static final int probeSamples = 6_000;

    private ScreenshotCapture() {}

    public static CaptureRef take(File outDir, String relativePath) throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        File target = new File(outDir, relativePath);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("could not create the capture directory " + parent);
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("could not replace the previous capture at " + target);
        }
        String name = captureName(relativePath);
        ScreenShotHelper.saveScreenshot(
                outDir,
                name,
                minecraft.displayWidth,
                minecraft.displayHeight,
                minecraft.getFramebuffer());
        if (!target.isFile() || target.length() == 0L) {
            throw new IOException("the game's screenshot writer did not write " + target);
        }
        boolean suspect = isFlatColour(target);
        if (suspect) {
            log.warn("capture {} is a single flat colour - wrong framebuffer?", target);
        }
        return new CaptureRef(kind, relativePath, suspect);
    }

    private static String captureName(String relativePath) {
        String prefix = "screenshots/";
        return relativePath.startsWith(prefix) ? relativePath.substring(prefix.length()) : relativePath;
    }

    private static boolean isFlatColour(File png) {
        BufferedImage image;
        try {
            image = ImageIO.read(png);
        } catch (IOException e) {
            log.warn("could not read back the capture {} for the flat-colour probe", png, e);
            return false;
        }
        if (image == null || image.getWidth() == 0 || image.getHeight() == 0) return false;
        int stepX = Math.max(1, image.getWidth() / 100);
        int stepY = Math.max(1, image.getHeight() / 60);
        int first = 0;
        int samples = 0;
        for (int y = 0; y < image.getHeight() && samples < probeSamples; y += stepY) {
            for (int x = 0; x < image.getWidth() && samples < probeSamples; x += stepX) {
                int rgb = image.getRGB(x, y) & 0x00FFFFFF;
                if (samples == 0) {
                    first = rgb;
                } else if (rgb != first) {
                    return false;
                }
                samples++;
            }
        }
        return samples > 1;
    }
}
