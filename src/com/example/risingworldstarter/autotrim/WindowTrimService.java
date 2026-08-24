package com.example.risingworldstarter.autotrim;

import net.risingworld.api.World;
import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Cuts a window-sized opening through intersecting construction wall panels. */
public final class WindowTrimService {
    private static final float FRAME_OVERLAP = 0.06f;
    private static final int MAX_INTERSECTIONS = 64;
    private final Consumer<String> debug;

    public WindowTrimService(Consumer<String> debug) {
        this.debug = debug;
    }

    public void trim(Player player, Vector3f approximatePosition, Quaternion approximateRotation,
                     Vector3f approximateSize) {
        Vector3f position = approximatePosition;
        Quaternion rotation = approximateRotation;
        Vector3f openingSize = approximateSize;
        ConstructionElement window = findNearestWindow(approximatePosition);
        if (window != null) {
            position = window.getWorldPosition().copy();
            rotation = window.getRotation().copy();
            Vector3f actualSize = window.getScale();
            if (actualSize != null && actualSize.x > 0.05f
                    && actualSize.y > 0.05f && actualSize.z > 0.02f) {
                openingSize = actualSize.copy();
            }
            log("Resolved placed window " + window.getGlobalID() + " at " + position
                    + " with size " + openingSize);
        }

        int carved = carveTerrainOpening(position, rotation, openingSize);
        int[] stats = new int[2];
        int constructionBlocks = carved == 0
                ? trimConstructionOpening(position, rotation, openingSize, stats) : 0;
        int total = carved + constructionBlocks;
        if (total > 0) {
            player.sendTextMessage("<color=#77FF99>Auto-trimmed " + total + " wall block"
                    + (total == 1 ? "" : "s") + " around the window.</color>");
        } else {
            Vector3i chunk = Utils.ChunkUtils.getChunkPosition(position);
            player.sendTextMessage("<color=#FFAA66>Auto-trim diagnostic: chunk "
                    + chunk.x + "," + chunk.z
                    + "; no terrain or construction blocks intersected (scanned " + stats[0]
                    + ", rectangular " + stats[1] + "); opening "
                    + String.format(Locale.US, "%.2f x %.2f x %.2f",
                    openingSize.x, openingSize.y, openingSize.z) + ".</color>");
        }
    }

    private ConstructionElement findNearestWindow(Vector3f nearPosition) {
        Vector3i centerChunk = Utils.ChunkUtils.getChunkPosition(nearPosition);
        ConstructionElement nearest = null;
        float nearestDistanceSquared = 25f;
        for (int cx = centerChunk.x - 1; cx <= centerChunk.x + 1; cx++) {
            for (int cz = centerChunk.z - 1; cz <= centerChunk.z + 1; cz++) {
                var chunk = World.getChunk(cx, cz);
                if (chunk == null || !chunk.isValid()) continue;
                ConstructionElement[] elements = chunk.getAllConstructionElements();
                if (elements == null) continue;
                for (ConstructionElement element : elements) {
                    if (element == null || !element.isValid()) continue;
                    Constructions.ConstructionDefinition definition =
                            Definitions.getConstructionDefinition(element.getTypeID());
                    if (definition == null || definition.type != Constructions.Type.Window) continue;
                    Vector3f candidate = element.getWorldPosition();
                    float dx = candidate.x - nearPosition.x;
                    float dy = candidate.y - nearPosition.y;
                    float dz = candidate.z - nearPosition.z;
                    float distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared;
                        nearest = element;
                    }
                }
            }
        }
        return nearest;
    }

    private int carveTerrainOpening(Vector3f center, Quaternion rotation, Vector3f size) {
        float width = Math.max(size.x, size.z);
        Vector3f widthAxis = rotation.mult(Vector3f.RIGHT).normalize();
        Vector3f normalAxis = rotation.mult(Vector3f.FORWARD).normalize();
        Set<String> editedBlocks = new HashSet<>();
        int carved = 0;
        for (float x = -width * 0.5f + 0.25f; x < width * 0.5f; x += 0.5f) {
            for (float y = -size.y * 0.5f + 0.25f; y < size.y * 0.5f; y += 0.5f) {
                for (float z = -0.5f; z <= 0.5f; z += 0.25f) {
                    Vector3f sample = center.add(widthAxis.mult(x)).add(Vector3f.UP.mult(y))
                            .add(normalAxis.mult(z));
                    Vector3i chunk = new Vector3i();
                    Vector3i block = new Vector3i();
                    Utils.ChunkUtils.getChunkAndBlockPosition(sample, chunk, block);
                    String key = chunk.x + "," + chunk.y + "," + chunk.z + ":"
                            + block.x + "," + block.y + "," + block.z;
                    if (!editedBlocks.add(key)) continue;
                    var chunkPart = World.getChunkPart(chunk.x, chunk.y, chunk.z);
                    if (chunkPart == null || !chunkPart.isValid()
                            || chunkPart.getTerrainID(block.x, block.y, block.z) == 0) continue;
                    World.setTerrainData(0, chunk.x, chunk.y, chunk.z, block.x, block.y, block.z,
                            net.risingworld.api.world.EditRestriction.SolidOnly);
                    carved++;
                }
            }
        }
        log("Terrain carve at " + center + ": removed " + carved + " solid blocks");
        return carved;
    }

    public int trimConstructionOnly(Vector3f position, Quaternion rotation, Vector3f size) {
        return trimConstructionOpening(position, rotation, size, new int[2]);
    }

    public int countConstructionIntersections(Vector3f position, Quaternion rotation, Vector3f size) {
        Vector3i openingChunk = Utils.ChunkUtils.getChunkPosition(position);
        int intersections = 0;
        for (int cx = openingChunk.x - 1; cx <= openingChunk.x + 1; cx++) {
            for (int cz = openingChunk.z - 1; cz <= openingChunk.z + 1; cz++) {
                var chunk = World.getChunk(cx, cz);
                if (chunk == null || !chunk.isValid()) continue;
                ConstructionElement[] elements = chunk.getAllConstructionElements();
                if (elements == null) continue;
                for (ConstructionElement wall : elements) {
                    if (isWall(wall) && trimWall(wall, position, rotation, size, true)) intersections++;
                }
            }
        }
        return intersections;
    }

    /** Returns an orientation whose local X axis follows the wall at a position. */
    public Quaternion resolveWallPlaneRotation(Vector3f position) {
        Vector3i centerChunk = Utils.ChunkUtils.getChunkPosition(position);
        Quaternion nearestRotation = null;
        float nearestDepth = Float.MAX_VALUE;
        for (int cx = centerChunk.x - 1; cx <= centerChunk.x + 1; cx++) {
            for (int cz = centerChunk.z - 1; cz <= centerChunk.z + 1; cz++) {
                var chunk = World.getChunk(cx, cz);
                if (chunk == null || !chunk.isValid()) continue;
                ConstructionElement[] elements = chunk.getAllConstructionElements();
                if (elements == null) continue;
                for (ConstructionElement wall : elements) {
                    if (!isWall(wall)) continue;
                    Vector3f size = wall.getScale();
                    Quaternion rotation = wall.getRotation().copy();
                    Vector3f local = rotation.inverse().mult(position.subtract(wall.getWorldPosition()));
                    boolean widthIsX = size.x >= size.z;
                    float width = widthIsX ? size.x : size.z;
                    float depth = Math.abs(widthIsX ? local.z : local.x);
                    float along = Math.abs(widthIsX ? local.x : local.z);
                    if (along > width * 0.5f + 1.5f
                            || Math.abs(local.y) > size.y * 0.5f + 1.5f
                            || depth >= nearestDepth) continue;
                    nearestDepth = depth;
                    nearestRotation = widthIsX ? rotation
                            : rotation.multLocal(new Quaternion().fromAngles(0f,
                            (float) (Math.PI * 0.5), 0f));
                }
            }
        }
        return nearestRotation;
    }

    private int trimConstructionOpening(Vector3f position, Quaternion openingRotation,
                                        Vector3f size, int[] stats) {
        Vector3i openingChunk = Utils.ChunkUtils.getChunkPosition(position);
        List<ConstructionElement> intersecting = new ArrayList<>();
        int scanned = 0;
        int compatible = 0;
        for (int cx = openingChunk.x - 1; cx <= openingChunk.x + 1; cx++) {
            for (int cz = openingChunk.z - 1; cz <= openingChunk.z + 1; cz++) {
                var chunk = World.getChunk(cx, cz);
                if (chunk == null || !chunk.isValid()) continue;
                ConstructionElement[] elements = chunk.getAllConstructionElements();
                if (elements == null) continue;
                for (ConstructionElement wall : elements) {
                    scanned++;
                    if (intersecting.size() >= MAX_INTERSECTIONS || !isWall(wall)) continue;
                    compatible++;
                    if (trimWall(wall, position, openingRotation, size, true)) intersecting.add(wall);
                }
            }
        }
        int trimmed = 0;
        for (ConstructionElement wall : intersecting) {
            if (trimWall(wall, position, openingRotation, size, false)) trimmed++;
        }
        stats[0] = scanned;
        stats[1] = compatible;
        log("Construction scan at " + position + ": scanned=" + scanned
                + ", rectangular=" + compatible + ", trimmed=" + trimmed);
        return trimmed;
    }

    private static boolean isWall(ConstructionElement element) {
        if (element == null || !element.isValid()) return false;
        Constructions.ConstructionDefinition definition = Definitions.getConstructionDefinition(element.getTypeID());
        if (definition == null || definition.type == Constructions.Type.Window
                || definition.shapetype != Constructions.ShapeType.Default) return false;
        Vector3f size = element.getScale();
        return size != null && size.y >= 0.25f && Math.max(size.x, size.z) >= 0.25f
                && Math.min(size.x, size.z) <= 1f;
    }

    private boolean trimWall(ConstructionElement wall, Vector3f openingPosition,
                             Quaternion openingRotation, Vector3f openingSize,
                             boolean detectOnly) {
        Vector3f wallSize = wall.getScale();
        if (wallSize == null) return false;
        wallSize = wallSize.copy();
        Quaternion rotation = wall.getRotation().copy();
        boolean widthIsX = wallSize.x >= wallSize.z;
        float wallWidth = widthIsX ? wallSize.x : wallSize.z;
        float thickness = widthIsX ? wallSize.z : wallSize.x;
        Quaternion inverseWallRotation = rotation.inverse();
        Vector3f local = inverseWallRotation.mult(openingPosition.subtract(wall.getWorldPosition()));
        Vector3f openingRight = inverseWallRotation.mult(openingRotation.mult(Vector3f.RIGHT));
        Vector3f openingForward = inverseWallRotation.mult(openingRotation.mult(Vector3f.FORWARD));
        float localWidth = widthIsX ? local.x : local.z;
        float localDepth = widthIsX ? local.z : local.x;
        float rightWidth = Math.abs(widthIsX ? openingRight.x : openingRight.z);
        float forwardWidth = Math.abs(widthIsX ? openingForward.x : openingForward.z);
        float rightDepth = Math.abs(widthIsX ? openingRight.z : openingRight.x);
        float forwardDepth = Math.abs(widthIsX ? openingForward.z : openingForward.x);
        float openingHalfWidth = rightWidth * openingSize.x * 0.5f
                + forwardWidth * openingSize.z * 0.5f;
        float openingHalfDepth = rightDepth * openingSize.x * 0.5f
                + forwardDepth * openingSize.z * 0.5f;
        if (Math.abs(localDepth) > thickness * 0.5f + openingHalfDepth + 0.05f) return false;

        float minW = -wallWidth * 0.5f;
        float maxW = wallWidth * 0.5f;
        float minY = -wallSize.y * 0.5f;
        float maxY = wallSize.y * 0.5f;
        float cutMinW = Math.max(minW, localWidth - openingHalfWidth + FRAME_OVERLAP);
        float cutMaxW = Math.min(maxW, localWidth + openingHalfWidth - FRAME_OVERLAP);
        float cutMinY = Math.max(minY, local.y - openingSize.y * 0.5f + FRAME_OVERLAP);
        float cutMaxY = Math.min(maxY, local.y + openingSize.y * 0.5f - FRAME_OVERLAP);
        if (cutMaxW - cutMinW < 0.08f || cutMaxY - cutMinY < 0.08f) return false;
        if (detectOnly) return true;

        List<float[]> pieces = new ArrayList<>(4);
        addPiece(pieces, minW, cutMinW, minY, maxY);
        addPiece(pieces, cutMaxW, maxW, minY, maxY);
        addPiece(pieces, cutMinW, cutMaxW, minY, cutMinY);
        addPiece(pieces, cutMinW, cutMaxW, cutMaxY, maxY);
        for (float[] piece : pieces) createPiece(wall, rotation, wallSize, widthIsX, piece, thickness);
        wall.setScale(0.001f, 0.001f, 0.001f);
        wall.destroy(true);
        return true;
    }

    private static void addPiece(List<float[]> pieces, float minW, float maxW,
                                 float minY, float maxY) {
        if (maxW - minW >= 0.08f && maxY - minY >= 0.08f) {
            pieces.add(new float[]{(minW + maxW) * 0.5f, (minY + maxY) * 0.5f,
                    maxW - minW, maxY - minY});
        }
    }

    private static void createPiece(ConstructionElement source, Quaternion rotation,
                                    Vector3f sourceSize, boolean widthIsX,
                                    float[] piece, float thickness) {
        Vector3f sourceScale = source.getScale();
        if (sourceScale == null) return;
        Vector3f offset = widthIsX ? new Vector3f(piece[0], piece[1], 0f)
                : new Vector3f(0f, piece[1], piece[0]);
        Vector3f position = source.getWorldPosition().add(rotation.mult(offset));
        Vector3f pieceSize = widthIsX ? new Vector3f(piece[2], piece[3], thickness)
                : new Vector3f(thickness, piece[3], piece[2]);
        Vector3f scale = new Vector3f(sourceScale.x * pieceSize.x / sourceSize.x,
                sourceScale.y * pieceSize.y / sourceSize.y,
                sourceScale.z * pieceSize.z / sourceSize.z);
        ConstructionElement created = World.createConstructionElement(source.getTypeID(), source.getTexture(),
                source.getTextureScale(), source.getColor(), position, rotation.copy(), scale,
                source.getSurfaceOffset(), source.getSurfaceScale());
        if (created != null) {
            created.setPlayerDbID(source.getPlayerDbID());
            created.setStrength(source.getStrength());
        }
    }

    private void log(String message) {
        if (debug != null) debug.accept("Auto-trim " + message);
    }
}
