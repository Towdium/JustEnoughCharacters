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

/**
 * The whole-framebuffer screenshot the harness takes, written straight through the game's own
 * screenshot writer.
 *
 * <p><b>Port note (and why this is the simple half of the port).</b> On 1.21.1 a capture is a queued
 * request the {@code FrameCapturePipeline} services on a later frame, because the 1.21.1 readback is
 * asynchronous ({@code GL11} grab into a PBO on moderner, {@code Screenshot.takeScreenshot} on
 * Fabric) and the report has to be re-pointed at the file when it lands. On 1.12.2 there is exactly
 * one readback — {@code ScreenShotHelper.saveScreenshot}, which reads the bound texture with
 * {@code glGetTexImage} and writes the PNG with {@code ImageIO} — and it is synchronous. So the
 * capture is taken inside the step that asked for it and the ref is correct the moment the step
 * finishes; there is no pipeline, no queue and no late correction.
 *
 * <p>{@code gameDirectory} is the run's <em>output</em> directory, not the game directory: the writer
 * appends {@code screenshots/} itself, so passing the output directory is what lands the file at the
 * {@code <outDir>/screenshots/<scenario>/<NN>_<label>.png} the report names. That also means the
 * {@code -PjechRunDir} in-place form puts the captures beside the reports under {@code <runDir>/uitest/}
 * instead of in the game directory, which is the two-placement rule every other checkout follows.
 *
 * <p>The flat-colour probe is here rather than in the report because it is a property of the image:
 * a capture that comes back a single colour means the readback read the wrong surface, and the
 * report carries that verdict so a green run with a black screenshot is visible.
 */
public final class ScreenshotCapture {

    /** The only capture kind this line produces. */
    public static final String kind = "FULL";

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    /** How many pixels of the image the flat-colour probe samples, at most. */
    private static final int probeSamples = 6_000;

    private ScreenshotCapture() {}

    /**
     * Takes one screenshot of the current framebuffer and reports where it landed.
     *
     * <p>Must be called with the GL context current and the main framebuffer still bound — the run's
     * frame pump is on {@code TickEvent.RenderTickEvent} {@code Phase.END}, which is the one point in
     * the frame where both hold.
     */
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
        // ScreenShotHelper writes to <gameDirectory>/screenshots/<name>; the output directory is the
        // game directory from its point of view, and the name carries the scenario subdirectory.
        String name = captureName(relativePath);
        ScreenShotHelper.saveScreenshot(
                outDir,
                name,
                minecraft.displayWidth,
                minecraft.displayHeight,
                minecraft.getFramebuffer());
        if (!target.isFile() || target.length() == 0L) {
            // The game's writer swallows its own failures (it logs "Couldn't save screenshot" and
            // returns a message the caller usually drops), so the file's presence is the proof.
            throw new IOException("the game's screenshot writer did not write " + target);
        }
        boolean suspect = isFlatColour(target);
        if (suspect) {
            log.warn("capture {} is a single flat colour - wrong framebuffer?", target);
        }
        return new CaptureRef(kind, relativePath, suspect);
    }

    /** {@code screenshots/<scenario>/<NN>_<label>.png} as written, made relative to the screenshots root. */
    private static String captureName(String relativePath) {
        String prefix = "screenshots/";
        return relativePath.startsWith(prefix) ? relativePath.substring(prefix.length()) : relativePath;
    }

    /**
     * Whether the image is one flat colour.
     *
     * <p>Sampled rather than exhaustively counted: a 1280x720 frame is nearly a million pixels and
     * the question is only whether <em>anything</em> varies, so the first few thousand samples answer
     * it. A whole-frame capture of a real world is never flat; an all-black or all-white one is.
     */
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
