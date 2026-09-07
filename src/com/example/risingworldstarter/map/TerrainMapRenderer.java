package com.example.risingworldstarter.map;

import com.example.risingworldstarter.claims.Claim;
import com.example.risingworldstarter.claims.ClaimedChunk;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** Produces the raster used by the large in-game topographical map. */
public final class TerrainMapRenderer {
    public static final int IMAGE_SIZE = 640;

    private TerrainMapRenderer() { }

    @FunctionalInterface
    public interface SurfaceProvider {
        /** Returns the terrain elevation, or NaN when the chunk is unavailable. */
        float elevation(int chunkX, int chunkZ, int localX, int localZ);
    }

    public static byte[] render(int centerChunkX, int centerChunkZ, int radius,
                                int chunkSizeX, int chunkSizeZ, SurfaceProvider terrain,
                                Map<ClaimedChunk, Claim> claims, boolean showClaims,
                                String personalOwnerId, String clanOwnerId,
                                float playerWorldX, float playerWorldZ) {
        int side = radius * 2 + 1;
        int minimumChunkX = centerChunkX - radius;
        int minimumChunkZ = centerChunkZ - radius;
        float[][] heights = new float[IMAGE_SIZE + 2][IMAGE_SIZE + 2];
        float minimumHeight = Float.POSITIVE_INFINITY;
        float maximumHeight = Float.NEGATIVE_INFINITY;

        for (int py = 0; py < IMAGE_SIZE + 2; py++) {
            for (int px = 0; px < IMAGE_SIZE + 2; px++) {
                double chunkOffsetX = ((px - 1d) / IMAGE_SIZE) * side;
                double chunkOffsetZ = ((py - 1d) / IMAGE_SIZE) * side;
                int relativeChunkX = (int) Math.floor(chunkOffsetX);
                int relativeChunkZ = (int) Math.floor(chunkOffsetZ);
                int localX = clamp((int) ((chunkOffsetX - relativeChunkX) * chunkSizeX), 0, chunkSizeX - 1);
                int localZ = clamp((int) ((chunkOffsetZ - relativeChunkZ) * chunkSizeZ), 0, chunkSizeZ - 1);
                float height = terrain.elevation(minimumChunkX + relativeChunkX,
                        minimumChunkZ + relativeChunkZ, localX, localZ);
                heights[px][py] = height;
                if (Float.isFinite(height)) {
                    minimumHeight = Math.min(minimumHeight, height);
                    maximumHeight = Math.max(maximumHeight, height);
                }
            }
        }
        if (!Float.isFinite(minimumHeight)) {
            minimumHeight = 0f;
            maximumHeight = 1f;
        }
        float heightRange = Math.max(1f, maximumHeight - minimumHeight);
        BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                float height = heights[x + 1][y + 1];
                if (!Float.isFinite(height)) {
                    image.setRGB(x, y, 0xFF202830);
                    continue;
                }
                float normalized = (height - minimumHeight) / heightRange;
                Color base = terrainColor(normalized);
                float eastWest = finiteDifference(heights[x][y + 1], heights[x + 2][y + 1]);
                float northSouth = finiteDifference(heights[x + 1][y], heights[x + 1][y + 2]);
                float shade = clamp(0.82f + (eastWest - northSouth) * 0.018f, 0.55f, 1.18f);
                boolean contour = crossesContour(height, heights[x][y + 1])
                        || crossesContour(height, heights[x + 1][y]);
                image.setRGB(x, y, shade(base, contour ? shade * 0.62f : shade).getRGB());
            }
        }

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double pixelsPerChunk = IMAGE_SIZE / (double) side;
        if (showClaims) {
            for (Map.Entry<ClaimedChunk, Claim> entry : claims.entrySet()) {
                ClaimedChunk chunk = entry.getKey();
                int x = (int) Math.round((chunk.x() - minimumChunkX) * pixelsPerChunk);
                int y = (int) Math.round((chunk.z() - minimumChunkZ) * pixelsPerChunk);
                int nextX = (int) Math.round((chunk.x() - minimumChunkX + 1) * pixelsPerChunk);
                int nextY = (int) Math.round((chunk.z() - minimumChunkZ + 1) * pixelsPerChunk);
                Claim claim = entry.getValue();
                Color fill = claim.ownerUid().equals(personalOwnerId) ? new Color(40, 125, 255, 100)
                        : claim.ownerUid().equals(clanOwnerId) ? new Color(171, 75, 235, 105)
                        : new Color(235, 55, 55, 100);
                graphics.setColor(fill);
                graphics.fillRect(x, y, Math.max(1, nextX - x), Math.max(1, nextY - y));
                graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 230));
                graphics.drawRect(x, y, Math.max(1, nextX - x), Math.max(1, nextY - y));
            }
        }
        graphics.setColor(new Color(255, 255, 255, 42));
        for (int i = 0; i <= side; i++) {
            int coordinate = (int) Math.round(i * pixelsPerChunk);
            graphics.drawLine(coordinate, 0, coordinate, IMAGE_SIZE);
            graphics.drawLine(0, coordinate, IMAGE_SIZE, coordinate);
        }

        double minimumWorldX = minimumChunkX * (double) chunkSizeX;
        double minimumWorldZ = minimumChunkZ * (double) chunkSizeZ;
        int playerX = (int) Math.round((playerWorldX - minimumWorldX) / (side * chunkSizeX) * IMAGE_SIZE);
        int playerY = (int) Math.round((playerWorldZ - minimumWorldZ) / (side * chunkSizeZ) * IMAGE_SIZE);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(playerX - 7, playerY - 7, 14, 14);
        graphics.setColor(new Color(20, 80, 210));
        graphics.fillOval(playerX - 4, playerY - 4, 8, 8);
        graphics.dispose();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode terrain map", exception);
        }
    }

    private static Color terrainColor(float value) {
        if (value < 0.28f) return blend(new Color(45, 92, 70), new Color(92, 126, 72), value / 0.28f);
        if (value < 0.62f) return blend(new Color(92, 126, 72), new Color(150, 132, 86), (value - 0.28f) / 0.34f);
        if (value < 0.84f) return blend(new Color(150, 132, 86), new Color(116, 108, 99), (value - 0.62f) / 0.22f);
        return blend(new Color(116, 108, 99), new Color(226, 230, 225), (value - 0.84f) / 0.16f);
    }

    private static boolean crossesContour(float first, float second) {
        return Float.isFinite(second) && (int) Math.floor(first / 20f) != (int) Math.floor(second / 20f);
    }

    private static float finiteDifference(float first, float second) {
        return Float.isFinite(first) && Float.isFinite(second) ? first - second : 0f;
    }

    private static Color shade(Color color, float factor) {
        return new Color(clamp(Math.round(color.getRed() * factor), 0, 255),
                clamp(Math.round(color.getGreen() * factor), 0, 255),
                clamp(Math.round(color.getBlue() * factor), 0, 255));
    }

    private static Color blend(Color first, Color second, float amount) {
        amount = clamp(amount, 0f, 1f);
        return new Color(Math.round(first.getRed() + (second.getRed() - first.getRed()) * amount),
                Math.round(first.getGreen() + (second.getGreen() - first.getGreen()) * amount),
                Math.round(first.getBlue() + (second.getBlue() - first.getBlue()) * amount));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
