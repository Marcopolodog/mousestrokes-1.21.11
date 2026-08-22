package com.example.mousestrokes.telemetry;

/**
 * Singleton, thread-guarded holder for raw mouse-movement telemetry.
 *
 * <p>Two independent responsibilities live here on purpose (spec section 35 -
 * separation of concerns still applies within one small class):
 * <ol>
 *   <li><b>Delta computation</b>: {@link #onRawCursorPos(double, double)} is
 *       called from {@code MouseMixin} once per GLFW cursor-position callback,
 *       with the raw absolute x/y the callback received. This class keeps its
 *       OWN previous-position state and diffs against it, rather than reading
 *       Minecraft's private {@code Mouse.cursorDeltaX}/{@code cursorDeltaY}
 *       accumulator fields. See the ledger entry "Why compute our own delta"
 *       for the reasoning - in short, it removes any dependency on the exact
 *       internal accumulation/reset semantics of those private fields, which
 *       are not shown in any public API surface and were not decompiled or
 *       independently verified in this session.</li>
 *   <li><b>Per-frame aggregation</b>: {@link #consumeSinceLastFrame()} is
 *       called from the HUD renderer once per render frame. It atomically
 *       reads and resets an accumulator, implementing the "accumulate between
 *       frames" strategy from spec section 25 (chosen over "latest event
 *       only" because a high-polling-rate mouse can fire this callback many
 *       times per render frame, and summing preserves the total physical
 *       displacement across that frame instead of discarding all but the last
 *       sample).</li>
 * </ol>
 *
 * <h2>Threading (spec section 26)</h2>
 * ASSUMPTION, not re-verified against 1.21.11 decompiled source in this
 * session, but grounded in long-standing and extremely stable Minecraft
 * client architecture: GLFW callbacks are dispatched synchronously from
 * {@code GLFW.glfwPollEvents()} / {@code Window.pollEvents()}, which is
 * called from the client's main loop on the same thread that later calls the
 * HUD render callback in the same frame. In practice input and rendering are
 * therefore expected to run on one thread, sequentially. This class does NOT
 * rely on that assumption for correctness, however: all mutable state is
 * guarded by a single monitor so it is safe even if that assumption turns out
 * to be wrong on some future version, a resource pack loading thread, or a
 * different Fabric mod's threading model.
 */
public final class MouseTelemetry {

	public static final MouseTelemetry INSTANCE = new MouseTelemetry();

	private static final double EPSILON = 1.0e-9;

	private boolean havePreviousPosition = false;
	private double previousX;
	private double previousY;

	private double accumulatedDx = 0.0;
	private double accumulatedDy = 0.0;

	/** Wall-clock timestamp (nanoTime) of the most recent non-zero raw sample. */
	private volatile long lastNonZeroSampleNanos = 0L;

	private MouseTelemetry() {
	}

	/**
	 * Called from {@code MouseMixin} at the HEAD of {@code Mouse.onCursorPos},
	 * with the exact (x, y) parameters GLFW delivered to that callback. Must
	 * remain cheap: no allocations, no trig, no logging on the hot path.
	 */
	public synchronized void onRawCursorPos(double x, double y) {
		if (!havePreviousPosition) {
			// First sample after (re)initialization: establish a baseline only.
			// Emitting a "delta" against an arbitrary/uninitialized previous
			// position would produce a spurious large jump.
			previousX = x;
			previousY = y;
			havePreviousPosition = true;
			return;
		}

		double dx = x - previousX;
		double dy = y - previousY;
		previousX = x;
		previousY = y;

		if (dx != 0.0 || dy != 0.0) {
			accumulatedDx += dx;
			accumulatedDy += dy;
			lastNonZeroSampleNanos = System.nanoTime();
		}
	}

	/**
	 * Atomically reads and resets the accumulator. Call exactly once per
	 * render frame. Returns a {@link MouseVector} representing the sum of all
	 * raw deltas observed since the previous call.
	 */
	public synchronized MouseVector consumeSinceLastFrame() {
		if (accumulatedDx == 0.0 && accumulatedDy == 0.0) {
			return MouseVector.ZERO;
		}
		MouseVector result = new MouseVector(accumulatedDx, accumulatedDy);
		accumulatedDx = 0.0;
		accumulatedDy = 0.0;
		return result;
	}

	public long getLastNonZeroSampleNanos() {
		return lastNonZeroSampleNanos;
	}

	/**
	 * Clears ALL telemetry state, including the previous-position baseline.
	 * Must be invoked on every lifecycle boundary listed in spec section 29
	 * (world join/leave, dimension change, screen open/close transitions
	 * where relevant, cursor lock/unlock, focus loss/gain, mod
	 * disable/config reload) so that no stale delta survives across the
	 * boundary. See {@code MouseStrokesClientLifecycle} for the actual event
	 * registrations that call this.
	 */
	public synchronized void reset() {
		havePreviousPosition = false;
		previousX = 0.0;
		previousY = 0.0;
		accumulatedDx = 0.0;
		accumulatedDy = 0.0;
		lastNonZeroSampleNanos = 0L;
	}

	public static double epsilon() {
		return EPSILON;
	}
}
