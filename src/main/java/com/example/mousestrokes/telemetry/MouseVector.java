package com.example.mousestrokes.telemetry;

/**
 * Immutable 2D vector representing one sample of raw mouse-movement telemetry
 * (either a single GLFW cursor-position callback's delta, or the sum of all
 * deltas accumulated since the last consumption - see {@link MouseTelemetry}).
 *
 * <p>Coordinate convention (see project docs, section "Coordinate system"):
 * <ul>
 *   <li>+X = physical mouse movement to the right</li>
 *   <li>+Y = physical mouse movement downward (matches GLFW/window pixel
 *       coordinates, which already grow downward - see the ledger entry
 *       "GLFW / Mouse Y convention" for why no sign flip is applied)</li>
 * </ul>
 *
 * <p>DESIGN CHOICE (spec section 33, performance): {@code magnitude()} and
 * {@code angleRadians()} are computed on demand rather than eagerly, and nothing
 * in the render path calls {@code angleRadians()} - it exists so the full
 * 360-degree vector math described in the specification is present and testable,
 * without spending a trig call on every frame when only dx/dy (already
 * sufficient to position the indicator) are actually needed.
 */
public final class MouseVector {

	public static final MouseVector ZERO = new MouseVector(0.0, 0.0);

	private final double dx;
	private final double dy;

	public MouseVector(double dx, double dy) {
		this.dx = dx;
		this.dy = dy;
	}

	public double dx() {
		return dx;
	}

	public double dy() {
		return dy;
	}

	/** Euclidean length of the vector, computed via {@link Math#hypot} for overflow-safe precision. */
	public double magnitude() {
		return Math.hypot(dx, dy);
	}

	/**
	 * Direction in radians using atan2(dy, dx), preserving the full 360-degree
	 * range (e.g. (1,0)=0, (1,1)=45deg, (0,1)=90deg, (-1,0)=180deg, (0,-1)=270deg
	 * in this Y-down convention). Not used by the renderer - see class docs.
	 */
	public double angleRadians() {
		return Math.atan2(dy, dx);
	}

	public boolean isEffectivelyZero(double epsilon) {
		// Cheaper than computing magnitude(): avoids the sqrt for the common
		// "no movement" case.
		return Math.abs(dx) <= epsilon && Math.abs(dy) <= epsilon;
	}
}
