package com.example.risingworldstarter.wallfit;

import java.util.ArrayList;
import java.util.List;

/** Pure horizontal geometry; all dimensions are world units and axes are unit vectors. */
public final class WallFitGeometry {
    private static final double ANGLE_LIMIT = Math.cos(Math.toRadians(10));
    private static final double RUN_ANGLE_LIMIT = Math.cos(Math.toRadians(1));
    private static final double EPSILON = 0.005;

    private WallFitGeometry() {}

    public record Wall(double x, double z, double dx, double dz, double length,
                       double thickness, double bottom, double height) {}
    public record Result(Wall wall, String reason) {
        public boolean fitted() { return wall != null; }
    }

    public static Result fit(Wall proposed, List<Wall> nearby) {
        List<Wall> candidates = new ArrayList<>();
        for (Wall wall : nearby) {
            double alignment = proposed.dx * wall.dx + proposed.dz * wall.dz;
            if (Math.abs(alignment) < ANGLE_LIMIT
                    || Math.abs(wall.bottom - proposed.bottom) > 0.25) continue;
            double dx = alignment < 0 ? -wall.dx : wall.dx;
            double dz = alignment < 0 ? -wall.dz : wall.dz;
            double along = (wall.x - proposed.x) * dx + (wall.z - proposed.z) * dz;
            double across = -(wall.x - proposed.x) * dz + (wall.z - proposed.z) * dx;
            double gap = Math.abs(along) - (wall.length + proposed.length) / 2;
            if (Math.abs(across) > 0.3 || Math.abs(gap) > 0.75) continue;
            candidates.add(new Wall(wall.x, wall.z, dx, dz, wall.length,
                    wall.thickness, wall.bottom, wall.height));
        }
        if (candidates.isEmpty()) return new Result(null, "no nearby wall continuation");

        Wall anchor = candidates.get(0);
        double dx = anchor.dx, dz = anchor.dz;
        double plane = -anchor.x * dz + anchor.z * dx;
        double center = proposed.x * dx + proposed.z * dz;
        Double left = null, right = null;
        for (Wall wall : candidates) {
            if (wall.dx * dx + wall.dz * dz < RUN_ANGLE_LIMIT
                    || Math.abs(-wall.x * dz + wall.z * dx - plane) > 0.02) {
                return new Result(null, "ambiguous neighboring wall lines");
            }
            double at = wall.x * dx + wall.z * dz;
            double end = at + wall.length / 2;
            double start = at - wall.length / 2;
            if (end <= center + EPSILON) left = left == null ? end : Math.max(left, end);
            else if (start >= center - EPSILON) right = right == null ? start : Math.min(right, start);
            else return new Result(null, "placement center overlaps a wall");
        }

        double length = proposed.length;
        if (left != null && right != null) {
            length = right - left;
            if (length < 0.1 || Math.abs(length - proposed.length) > Math.min(0.5, proposed.length * 0.2)) {
                return new Result(null, "gap differs too much from proposed length");
            }
            center = (left + right) / 2;
        } else if (left != null) center = left + length / 2;
        else if (right != null) center = right - length / 2;
        else return new Result(null, "no usable endpoint");

        Wall fitted = new Wall(center * dx - plane * dz, center * dz + plane * dx,
                dx, dz, length, proposed.thickness, proposed.bottom, proposed.height);
        if (Math.hypot(fitted.x - proposed.x, fitted.z - proposed.z) > 0.75) {
            return new Result(null, "required movement exceeds snap limit");
        }
        for (Wall wall : nearby) {
            if (wall.bottom >= fitted.bottom + fitted.height - EPSILON
                    || wall.bottom + wall.height <= fitted.bottom + EPSILON) continue;
            if (overlaps(fitted, wall)) return new Result(null, "fitted wall would overlap an existing wall");
        }
        return new Result(fitted, "fitted");
    }

    private static boolean overlaps(Wall a, Wall b) {
        return overlapsOn(a, b, a.dx, a.dz) && overlapsOn(a, b, -a.dz, a.dx)
                && overlapsOn(a, b, b.dx, b.dz) && overlapsOn(a, b, -b.dz, b.dx);
    }

    private static boolean overlapsOn(Wall a, Wall b, double dx, double dz) {
        double distance = Math.abs((a.x - b.x) * dx + (a.z - b.z) * dz);
        return distance < radius(a, dx, dz) + radius(b, dx, dz) - EPSILON;
    }

    private static double radius(Wall wall, double dx, double dz) {
        return Math.abs(wall.dx * dx + wall.dz * dz) * wall.length / 2
                + Math.abs(-wall.dz * dx + wall.dx * dz) * wall.thickness / 2;
    }
}
