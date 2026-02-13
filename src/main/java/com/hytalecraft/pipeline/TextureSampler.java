package com.hytalecraft.pipeline;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Samples colors from texture images at UV coordinates.
 * Note: requires -Djava.awt.headless=true on headless servers.
 */
public class TextureSampler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final BufferedImage image;
    private final int width;
    private final int height;

    public TextureSampler(byte[] imageData) throws IOException {
        this.image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (this.image == null) {
            throw new IOException("Failed to load texture image");
        }
        this.width = image.getWidth();
        this.height = image.getHeight();
        LOGGER.atInfo().log("[TextureSampler] Loaded texture: %dx%d", width, height);
    }

    /**
     * Samples a color from the texture at UV coordinates.
     * @param u Horizontal texture coordinate (0.0 to 1.0)
     * @param v Vertical texture coordinate (0.0 to 1.0)
     * @return RGB color as an integer (0xRRGGBB)
     */
    public int sample(float u, float v) {
        u = Math.max(0.0f, Math.min(1.0f, u));
        v = Math.max(0.0f, Math.min(1.0f, v));

        int x = (int) (u * (width - 1));
        int y = (int) ((1.0f - v) * (height - 1));

        return image.getRGB(x, y) & 0xFFFFFF;
    }
}
