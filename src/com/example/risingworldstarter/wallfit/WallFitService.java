package com.example.risingworldstarter.wallfit;

import net.risingworld.api.World;
import net.risingworld.api.definitions.Constructions;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.events.player.world.PlayerPlaceConstructionEvent;
import net.risingworld.api.objects.world.ConstructionElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Utils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Fits a rectangular wall into the small remaining gap along its supporting slab. */
public final class WallFitService {
    private static final float MAX_WALL_THICKNESS = 0.5f;
    private static final float SUPPORT_VERTICAL_TOLERANCE = 0.2f;
    private static final float SAME_LEVEL_TOLERANCE = 0.25f;
    private static final float PLANE_TOLERANCE = 0.12f;
    private static final float MIN_LENGTH = 0.1f;
    private static final float MAX_LENGTH_CHANGE = 0.5f;
    private static final float MAX_LENGTH_CHANGE_RATIO = 0.2f;
    private static final float CHANGE_EPSILON = 0.005f;

    private final Consumer<String> debug;

    public WallFitService(Consumer<String> debug) {
        this.debug = debug;
    }

    public FitResult fit(PlayerPlaceConstructionEvent event) {
        if (event.getTotalCount() != 1) return FitResult.notFitted("multi-placement");
        Vector3f size = event.getSize();
        Vector3f position = event.getPosition();
        Quaternion rotation = event.getRotation();
        if (!isRectangularWall(event.getConstructionDefinition(), size)) {
            return FitResult.notFitted("not a rectangular wall");
        }

        boolean lengthIsX = size.x >= size.z;
        float oldLength = lengthIsX ? size.x : size.z;
        float thickness = lengthIsX ? size.z : size.x;
        Vector3f lengthAxis = horizontal(rotation.mult(lengthIsX ? Vector3f.RIGHT : Vector3f.FORWARD));
        Vector3f normalAxis = new Vector3f(-lengthAxis.z, 0f, lengthAxis.x);
        float wallBottom = position.y - size.y * 0.5f;

        ConstructionElement support = findSupport(position, wallBottom, thickness, lengthAxis, normalAxis);
        if (support == null) return FitResult.notFitted("no unambiguous supporting slab");
        Vector3f supportSize = getPhysicalSize(support);
        Vector3f supportPosition = support.getWorldPosition();
        Quaternion supportRotation = support.getRotation();
        Vector3f supportX = horizontal(supportRotation.mult(Vector3f.RIGHT));
        Vector3f supportZ = horizontal(supportRotation.mult(Vector3f.FORWARD));
        float supportRadius = projectedRadius(supportSize, supportX, supportZ, lengthAxis);
        float supportCenter = dot(supportPosition, lengthAxis);
        float spanMin = supportCenter - supportRadius;
        float spanMax = supportCenter + supportRadius;
        float proposedCenter = dot(position, lengthAxis);

        List<Interval> occupied = findOccupiedIntervals(position, size, lengthAxis, normalAxis,
                spanMin, spanMax);
        occupied.sort(Comparator.comparingDouble(Interval::min));
        float gapMin = spanMin;
        float gapMax = spanMax;
        for (Interval interval : occupied) {
            if (proposedCenter > interval.min - CHANGE_EPSILON
                    && proposedCenter < interval.max + CHANGE_EPSILON) {
                return FitResult.notFitted("placement overlaps an existing wall");
            }
            if (interval.max <= proposedCenter) gapMin = Math.max(gapMin, interval.max);
            if (interval.min >= proposedCenter) gapMax = Math.min(gapMax, interval.min);
        }

        float newLength = gapMax - gapMin;
        float allowedChange = Math.min(MAX_LENGTH_CHANGE, oldLength * MAX_LENGTH_CHANGE_RATIO);
        if (newLength < MIN_LENGTH || Math.abs(newLength - oldLength) > allowedChange) {
            return FitResult.notFitted("gap differs too much from the placed wall");
        }
        float newCenter = (gapMin + gapMax) * 0.5f;
        float centerShift = newCenter - proposedCenter;
        if (Math.abs(newLength - oldLength) < CHANGE_EPSILON
                && Math.abs(centerShift) < CHANGE_EPSILON) {
            return FitResult.notFitted("already fitted");
        }

        Vector3f fittedSize = size.copy();
        if (lengthIsX) fittedSize.x = newLength;
        else fittedSize.z = newLength;
        Vector3f fittedPosition = position.add(lengthAxis.mult(centerShift));
        event.setSize(fittedSize);
        event.setPosition(fittedPosition);
        log(String.format(Locale.US, "fitted %.3f to %.3f and shifted %.3f", oldLength,
                newLength, centerShift));
        return new FitResult(true, oldLength, newLength, centerShift, "fitted");
    }

    private ConstructionElement findSupport(Vector3f wallPosition, float wallBottom, float wallThickness,
                                            Vector3f lengthAxis, Vector3f normalAxis) {
        Vector3i centerChunk = Utils.ChunkUtils.getChunkPosition(wallPosition);
        ConstructionElement best = null;
        float bestDistance = Float.MAX_VALUE;
        boolean ambiguous = false;
        for (ConstructionElement candidate : nearbyElements(centerChunk)) {
            Vector3f candidateSize = getPhysicalSize(candidate);
            if (candidateSize == null || candidateSize.y > 2f
                    || candidateSize.x < wallThickness || candidateSize.z < wallThickness) continue;
            Quaternion candidateRotation = candidate.getRotation();
            Vector3f xAxis = horizontalOrNull(candidateRotation.mult(Vector3f.RIGHT));
            Vector3f zAxis = horizontalOrNull(candidateRotation.mult(Vector3f.FORWARD));
            if (xAxis == null || zAxis == null) continue;
            Vector3f center = candidate.getWorldPosition();
            float top = center.y + candidateSize.y * 0.5f;
            float verticalDistance = Math.abs(wallBottom - top);
            if (verticalDistance > SUPPORT_VERTICAL_TOLERANCE) continue;
            float along = Math.abs(dot(wallPosition.subtract(center), lengthAxis));
            float across = Math.abs(dot(wallPosition.subtract(center), normalAxis));
            float alongRadius = projectedRadius(candidateSize, xAxis, zAxis, lengthAxis);
            float acrossRadius = projectedRadius(candidateSize, xAxis, zAxis, normalAxis);
            if (along > alongRadius + wallThickness || across > acrossRadius + wallThickness) continue;
            if (verticalDistance + 0.01f < bestDistance) {
                best = candidate;
                bestDistance = verticalDistance;
                ambiguous = false;
            } else if (Math.abs(verticalDistance - bestDistance) <= 0.01f) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    private List<Interval> findOccupiedIntervals(Vector3f wallPosition, Vector3f wallSize,
                                                 Vector3f lengthAxis, Vector3f normalAxis,
                                                 float spanMin, float spanMax) {
        List<Interval> intervals = new ArrayList<>();
        Vector3i centerChunk = Utils.ChunkUtils.getChunkPosition(wallPosition);
        float targetBottom = wallPosition.y - wallSize.y * 0.5f;
        float targetThickness = Math.min(wallSize.x, wallSize.z);
        float targetPlane = dot(wallPosition, normalAxis);
        for (ConstructionElement element : nearbyElements(centerChunk)) {
            Vector3f size = getPhysicalSize(element);
            if (!isRectangularWall(Definitions.getConstructionDefinition(element.getTypeID()), size)) continue;
            if (Math.abs((element.getWorldPosition().y - size.y * 0.5f) - targetBottom)
                    > SAME_LEVEL_TOLERANCE) continue;
            Quaternion rotation = element.getRotation();
            Vector3f xAxis = horizontalOrNull(rotation.mult(Vector3f.RIGHT));
            Vector3f zAxis = horizontalOrNull(rotation.mult(Vector3f.FORWARD));
            if (xAxis == null || zAxis == null) continue;
            float normalRadius = projectedRadius(size, xAxis, zAxis, normalAxis);
            float planeDistance = Math.abs(dot(element.getWorldPosition(), normalAxis) - targetPlane);
            if (planeDistance > normalRadius + targetThickness * 0.5f + PLANE_TOLERANCE) continue;
            float radius = projectedRadius(size, xAxis, zAxis, lengthAxis);
            float center = dot(element.getWorldPosition(), lengthAxis);
            float min = Math.max(spanMin, center - radius);
            float max = Math.min(spanMax, center + radius);
            if (max - min >= CHANGE_EPSILON) intervals.add(new Interval(min, max));
        }
        return intervals;
    }

    private static List<ConstructionElement> nearbyElements(Vector3i centerChunk) {
        List<ConstructionElement> result = new ArrayList<>();
        for (int cx = centerChunk.x - 1; cx <= centerChunk.x + 1; cx++) {
            for (int cz = centerChunk.z - 1; cz <= centerChunk.z + 1; cz++) {
                var chunk = World.getChunk(cx, cz);
                if (chunk == null || !chunk.isValid()) continue;
                ConstructionElement[] elements = chunk.getAllConstructionElements();
                if (elements == null) continue;
                for (ConstructionElement element : elements) {
                    if (element != null && element.isValid()) result.add(element);
                }
            }
        }
        return result;
    }

    private static boolean isRectangularWall(Constructions.ConstructionDefinition definition, Vector3f size) {
        if (size == null || definition == null || definition.type == Constructions.Type.Window
                || definition.shapetype != Constructions.ShapeType.Default) return false;
        float length = Math.max(size.x, size.z);
        float thickness = Math.min(size.x, size.z);
        return size.y >= 0.5f && thickness > 0f && thickness <= MAX_WALL_THICKNESS
                && length >= thickness * 3f;
    }

    private static Vector3f getPhysicalSize(ConstructionElement element) {
        if (element == null) return null;
        Vector3f scale = element.getScale();
        Constructions.ConstructionDefinition definition = Definitions.getConstructionDefinition(element.getTypeID());
        if (scale == null || definition == null) return null;
        return definition.startsize == null ? scale.copy() : definition.startsize.mult(scale);
    }

    private static float projectedRadius(Vector3f size, Vector3f xAxis, Vector3f zAxis, Vector3f axis) {
        return Math.abs(dot(xAxis, axis)) * size.x * 0.5f
                + Math.abs(dot(zAxis, axis)) * size.z * 0.5f;
    }

    private static Vector3f horizontal(Vector3f vector) {
        Vector3f result = new Vector3f(vector.x, 0f, vector.z);
        return result.normalize();
    }

    private static Vector3f horizontalOrNull(Vector3f vector) {
        float lengthSquared = vector.x * vector.x + vector.z * vector.z;
        if (lengthSquared < 0.99f) return null;
        return new Vector3f(vector.x, 0f, vector.z).normalize();
    }

    private static float dot(Vector3f left, Vector3f right) {
        return left.x * right.x + left.y * right.y + left.z * right.z;
    }

    private void log(String message) {
        if (debug != null) debug.accept("Wall fit " + message);
    }

    private record Interval(float min, float max) {}

    public record FitResult(boolean fitted, float oldLength, float newLength,
                            float centerShift, String reason) {
        private static FitResult notFitted(String reason) {
            return new FitResult(false, 0f, 0f, 0f, reason);
        }
    }
}
