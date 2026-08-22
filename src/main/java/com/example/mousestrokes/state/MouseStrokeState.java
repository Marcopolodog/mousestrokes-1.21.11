package com.example.mousestrokes.state;

import com.example.mousestrokes.config.MouseStrokesConfig;
import com.example.mousestrokes.telemetry.MouseTelemetry;
import com.example.mousestrokes.telemetry.MouseVector;

/**
 * Implements spec sections 7-9 (indicator translation, movement-accumulation
 * semantics, smoothing/reset).
 *
 * <h2>Accumulation model chosen (spec section 8, option C)</h2>
 * "Instantaneous / decaying displacement": {@code targetOffset} is NOT a
 * running total of every raw sample ever received - each frame it is
 * replaced with a fresh vector scaled from that frame's raw input (or held at
 * its last value during the configured {@code resetDelayMs} grace period,
 * then snapped to zero). {@code renderOffset} is a separate, smoothed value
 * that chases {@code targetOffset} using frame-rate-independent exponential
 * decay, which is what actually gets drawn. This gives:
 * <ul>
 *   <li>Immediate reaction: {@code targetOffset} updates the instant new
 *       input arrives, with no smoothing applied to the raw numbers
 *       themselves (spec section 9: "the raw telemetry itself must NEVER be
 *       smoothed").</li>
 *   <li>No permanent drift: because {@code targetOffset} is replaced, not
 *       accumulated, holding the mouse still after a stroke cannot leave the
 *       indicator stuck away from center, and repeated same-direction motion
 *       cannot walk the indicator outside the clamp.</li>
 *   <li>Smooth return: once movement stops (and the grace period elapses),
 *       {@code targetOffset} becomes (0,0) and {@code renderOffset} decays
 *       toward it smoothly rather than snapping instantly.</li>
 * </ul>
 */
public final class MouseStrokeState {

	private double targetOffsetX = 0.0;
	private double targetOffsetY = 0.0;

	private double renderOffsetX = 0.0;
	private double renderOffsetY = 0.0;

	private long lastFrameNanos = 0L;
	private boolean haveLastFrameTime = false;

	/**
	 * Advances the state machine by one render frame. Must be called exactly
	 * once per HUD render call.
	 *
	 * @param config current configuration (read-only here)
	 */
	public void update(MouseStrokesConfig config) {
		long now = System.nanoTime();
		double dtSeconds;
		if (!haveLastFrameTime) {
			dtSeconds = 0.0;
			haveLastFrameTime = true;
		} else {
			dtSeconds = Math.max(0.0, (now - lastFrameNanos) / 1_000_000_000.0);
		}
		lastFrameNanos = now;

		MouseVector frameVector = MouseTelemetry.INSTANCE.consumeSinceLastFrame();
		boolean movedThisFrame = !frameVector.isEffectivelyZero(MouseTelemetry.epsilon());

		if (movedThisFrame) {
			applyScaledClampedTarget(frameVector, config);
		} else {
			long lastNonZeroNanos = MouseTelemetry.INSTANCE.getLastNonZeroSampleNanos();
			long msSinceLastMovement = lastNonZeroNanos == 0L
					? Long.MAX_VALUE
					: (now - lastNonZeroNanos) / 1_000_000L;
			if (msSinceLastMovement >= config.resetDelayMs) {
				targetOffsetX = 0.0;
				targetOffsetY = 0.0;
			}
			// else: still inside the grace period - hold the previous target.
		}

		double tau = config.returnSpeed;
		double alpha = tau <= 0.0 ? 1.0 : 1.0 - Math.exp(-dtSeconds / tau);
		// Clamp alpha into [0,1] defensively (e.g. if dtSeconds is huge after
		// a stutter/alt-tab, snap directly to target rather than overshoot).
		alpha = Math.max(0.0, Math.min(1.0, alpha));

		renderOffsetX += (targetOffsetX - renderOffsetX) * alpha;
		renderOffsetY += (targetOffsetY - renderOffsetY) * alpha;
	}

	private void applyScaledClampedTarget(MouseVector frameVector, MouseStrokesConfig config) {
		double scaledX = frameVector.dx() * config.inputSensitivity;
		double scaledY = frameVector.dy() * config.inputSensitivity;

		double magnitude = Math.hypot(scaledX, scaledY);
		int maxRadius = config.effectiveMaxRadius();

		if (magnitude <= maxRadius || magnitude <= MouseTelemetry.epsilon()) {
			targetOffsetX = scaledX;
			targetOffsetY = scaledY;
		} else {
			double normalizedX = scaledX / magnitude;
			double normalizedY = scaledY / magnitude;
			targetOffsetX = normalizedX * maxRadius;
			targetOffsetY = normalizedY * maxRadius;
		}
	}

	public double renderOffsetX() {
		return renderOffsetX;
	}

	public double renderOffsetY() {
		return renderOffsetY;
	}

	/** Resets ALL animation state to center with no transition. Call this
	 *  alongside {@link MouseTelemetry#reset()} on every lifecycle boundary
	 *  from spec section 29. */
	public void reset() {
		targetOffsetX = 0.0;
		targetOffsetY = 0.0;
		renderOffsetX = 0.0;
		renderOffsetY = 0.0;
		haveLastFrameTime = false;
	}
}
