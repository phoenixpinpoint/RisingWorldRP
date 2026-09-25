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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Aligns a placed rectangular wall with nearby wall endpoints. */
public final class WallFitService {
    private static final float MAX_WALL_THICKNESS = 0.5f;
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
        WallFitGeometry.Wall proposed = geometry(position, size, rotation);
        if (proposed == null) return FitResult.notFitted("tilted wall");
        List<WallFitGeometry.Wall> nearby = new ArrayList<>();
        for (ConstructionElement element : nearbyElements(Utils.ChunkUtils.getChunkPosition(position))) {
            Vector3f otherSize = getPhysicalSize(element);
            if (!isRectangularWall(Definitions.getConstructionDefinition(element.getTypeID()), otherSize)) continue;
            WallFitGeometry.Wall wall = geometry(element.getWorldPosition(), otherSize, element.getRotation());
            if (wall != null) nearby.add(wall);
        }
        WallFitGeometry.Result result = WallFitGeometry.fit(proposed, nearby);
        if (!result.fitted()) {
            log(result.reason());
            return FitResult.notFitted(result.reason());
        }
        WallFitGeometry.Wall fitted = result.wall();
        Vector3f fittedSize = size.copy();
        if (size.x >= size.z) fittedSize.x = (float) fitted.length();
        else fittedSize.z = (float) fitted.length();
        // Positive yaw rotates +X toward -Z. Pre-multiply to rotate in world space.
        double yaw = Math.atan2(proposed.dz() * fitted.dx() - proposed.dx() * fitted.dz(),
                proposed.dx() * fitted.dx() + proposed.dz() * fitted.dz());
        Quaternion fittedRotation = new Quaternion(0f, (float) Math.sin(yaw / 2),
                0f, (float) Math.cos(yaw / 2));
        fittedRotation.multLocal(rotation);
        float shift = (float) Math.hypot(fitted.x() - proposed.x(), fitted.z() - proposed.z());
        if (shift < 0.005f && Math.abs(fitted.length() - proposed.length()) < 0.005
                && Math.abs(yaw) < 0.0001) return FitResult.notFitted("already fitted");
        event.setSize(fittedSize);
        event.setPosition(new Vector3f((float) fitted.x(), position.y, (float) fitted.z()));
        event.setRotation(fittedRotation);
        log(String.format(Locale.US, "fitted %.3f to %.3f, moved %.3f, rotated %.2f degrees",
                proposed.length(), fitted.length(), shift, Math.toDegrees(yaw)));
        return new FitResult(true, (float) proposed.length(), (float) fitted.length(), shift, "fitted");
    }

    private static WallFitGeometry.Wall geometry(Vector3f position, Vector3f size, Quaternion rotation) {
        Vector3f x = horizontalOrNull(rotation.mult(Vector3f.RIGHT));
        Vector3f z = horizontalOrNull(rotation.mult(Vector3f.FORWARD));
        if (x == null || z == null) return null;
        Vector3f axis = size.x >= size.z ? x : z;
        return new WallFitGeometry.Wall(position.x, position.z, axis.x, axis.z,
                Math.max(size.x, size.z), Math.min(size.x, size.z),
                position.y - size.y * 0.5f, size.y);
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

    private static Vector3f horizontalOrNull(Vector3f vector) {
        float lengthSquared = vector.x * vector.x + vector.z * vector.z;
        if (lengthSquared < 0.99f) return null;
        return new Vector3f(vector.x, 0f, vector.z).normalize();
    }

    private void log(String message) {
        if (debug != null) debug.accept("Wall fit " + message);
    }

    public record FitResult(boolean fitted, float oldLength, float newLength,
                            float centerShift, String reason) {
        private static FitResult notFitted(String reason) {
            return new FitResult(false, 0f, 0f, 0f, reason);
        }
    }
}
