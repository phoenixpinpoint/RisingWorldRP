package com.example.risingworldstarter.autotrim;

import net.risingworld.api.World;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.world.ObjectElement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

import java.util.function.Consumer;

/** Cuts a fixed doorway and validates the door's swept quarter-circle. */
public final class DoorTrimService {
    private static final int SWEEP_STEPS = 6;
    private static final float MIN_WIDTH = 1.0f;
    private static final float MIN_HEIGHT = 2.0f;
    private static final float MIN_THICKNESS = 0.15f;

    private final WindowTrimService wallCutter;
    private final Consumer<String> debug;

    public DoorTrimService(WindowTrimService wallCutter, Consumer<String> debug) {
        this.wallCutter = wallCutter;
        this.debug = debug;
    }

    public void trim(Player player, long globalId, int chunkX, int chunkY, int chunkZ,
                     Vector3f fallbackCenter, Quaternion fallbackRotation, Vector3f reportedSize) {
        // reportedSize is already boundscale×scale (physical dimensions) from the
        // event handler — only pull rotation from the placed object.
        Quaternion closedRotation = fallbackRotation;
        ObjectElement placedDoor = World.getObject(globalId, chunkX, chunkY, chunkZ);
        if (placedDoor != null && placedDoor.isValid()) {
            Quaternion actualRotation = placedDoor.getRotation();
            if (actualRotation != null) closedRotation = actualRotation.copy();
            log("resolved door " + globalId + " rotation=" + closedRotation);
        } else {
            log("could not resolve door " + globalId + "; using event data");
        }

        float width     = Math.max(MIN_WIDTH,     Math.max(reportedSize.x, reportedSize.z));
        float height    = Math.max(MIN_HEIGHT,    reportedSize.y);
        float thickness = Math.max(MIN_THICKNESS, Math.min(reportedSize.x, reportedSize.z));

        // Resolve the wall's plane orientation from near the pivot.
        Vector3f nearHinge = fallbackCenter.add(Vector3f.UP.mult(height * 0.5f));
        Quaternion wallPlane = wallCutter.resolveWallPlaneRotation(nearHinge);
        if (wallPlane != null) {
            closedRotation = wallPlane;
            log("resolved wall plane from nearby wall");
        }

        // The hinge pivot is at one corner of the opening. Probe three candidate
        // opening centers and use the one that intersects the most wall panels.
        Vector3f wallRight   = closedRotation.mult(Vector3f.RIGHT);
        Vector3f doorwaySize = new Vector3f(width, height, width);
        Vector3f candLeft    = nearHinge.add(wallRight.mult(width * 0.5f));
        Vector3f candRight   = nearHinge.subtract(wallRight.mult(width * 0.5f));

        int hitsBase  = wallCutter.countConstructionIntersections(nearHinge, closedRotation, doorwaySize);
        int hitsLeft  = wallCutter.countConstructionIntersections(candLeft,  closedRotation, doorwaySize);
        int hitsRight = wallCutter.countConstructionIntersections(candRight, closedRotation, doorwaySize);

        Vector3f doorCenter;
        Vector3f hingeCenter; // mid-height hinge point used as the swing pivot
        if (hitsLeft > hitsBase && hitsLeft >= hitsRight) {
            doorCenter  = candLeft;
            hingeCenter = nearHinge;
        } else if (hitsRight > hitsBase) {
            doorCenter  = candRight;
            hingeCenter = nearHinge;
        } else {
            // No offset beats the base; treat event position as center-base, assume left hinge.
            doorCenter  = nearHinge;
            hingeCenter = nearHinge.subtract(wallRight.mult(width * 0.5f));
        }

        player.sendTextMessage("<color=#66CCFF>Door geometry: "
                + String.format(java.util.Locale.US, "%.2f x %.2f x %.2f",
                width, height, thickness) + ".</color>");

        int trimmed = wallCutter.trimConstructionOnly(doorCenter, closedRotation, doorwaySize);

        // Check both swing directions; the door only needs one clear arc.
        Vector3f slabSize = new Vector3f(width, height, thickness);
        int obstCCW = sweepObstructions(hingeCenter, doorCenter, closedRotation, slabSize, +1f);
        int obstCW  = sweepObstructions(hingeCenter, doorCenter, closedRotation, slabSize, -1f);
        int obstructions = Math.min(obstCCW, obstCW);

        if (trimmed > 0) {
            player.sendTextMessage("<color=#77FF99>Auto-trimmed " + trimmed
                    + " wall section" + (trimmed == 1 ? "" : "s")
                    + " for the doorway.</color>");
        } else {
            player.sendTextMessage("<color=#FFAA66>Doorway auto-trim found no wall panel "
                    + "at the placement point.</color>");
        }
        if (obstructions > 0) {
            player.sendTextMessage("<color=#FF6666>Door swing obstructed by " + obstructions
                    + " construction section" + (obstructions == 1 ? "" : "s")
                    + ". Move the door or clear its opening side.</color>");
        } else {
            player.sendTextMessage("<color=#77FF99>Door opening arc is clear.</color>");
        }
        log("doorway at " + doorCenter + ": size=" + doorwaySize
                + ", trimmed=" + trimmed + ", obstCCW=" + obstCCW + " obstCW=" + obstCW);
    }

    /**
     * Sweeps the door leaf from closed to 90° open in the given direction
     * (+1 = CCW, -1 = CW) and counts construction intersections at each step.
     */
    private int sweepObstructions(Vector3f hingeCenter, Vector3f doorCenter,
                                  Quaternion closedRotation, Vector3f slabSize, float direction) {
        // Normalized horizontal vector from hinge to door center (closed position).
        Vector3f toCenter = doorCenter.subtract(hingeCenter);
        float radius = (float) Math.sqrt(toCenter.x * toCenter.x + toCenter.z * toCenter.z);
        if (radius < 0.01f) return 0;
        float invR = 1f / radius;
        float odx = toCenter.x * invR;
        float odz = toCenter.z * invR;
        // Perpendicular in the horizontal plane, rotated by direction.
        float pdx = -odz * direction;
        float pdz =  odx * direction;
        int count = 0;
        for (int step = 2; step <= SWEEP_STEPS; step++) {
            float angle = (float) (Math.PI * 0.5 * step / SWEEP_STEPS);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            // Door leaf center at this sweep angle (Y stays at mid-height).
            Vector3f slabCenter = new Vector3f(
                    hingeCenter.x + (odx * cosA + pdx * sinA) * radius,
                    hingeCenter.y,
                    hingeCenter.z + (odz * cosA + pdz * sinA) * radius);
            Quaternion sweepRot = closedRotation.copy()
                    .multLocal(new Quaternion().fromAngles(0f, angle * direction, 0f));
            count += wallCutter.countConstructionIntersections(slabCenter, sweepRot, slabSize);
        }
        return count;
    }

    private void log(String message) {
        if (debug != null) debug.accept("Auto-trim " + message);
    }
}
