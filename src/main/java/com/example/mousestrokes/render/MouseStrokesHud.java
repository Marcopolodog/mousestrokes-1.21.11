package com.example.mousestrokes.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import com.example.mousestrokes.config.MouseStrokesConfig;
import com.example.mousestrokes.state.MouseStrokeState;

/**
 * Renders the widget: exactly one square background, one square outline, one
 * circular indicator - nothing else (spec section 32).
 *
 * <h2>Rendering approach and why</h2>
 * <b>VERIFIED</b> (Yarn 1.21.11+build.4 javadoc, {@code DrawContext.html}):
 * {@code DrawContext} exposes {@code public void fill(int x1, int y1, int x2,
 * int y2, int color)}, taking a packed ARGB int and alpha-blending it over
 * whatever has already been drawn - this has been DrawContext's translucent
 * fill primitive since {@code DrawContext} was introduced, and is how vanilla
 * draws things like the dark background behind inventory tooltips.
 *
 * <p><b>DESIGN CHOICE</b>: both the square (via {@link #fillBorderRing} /
 * plain {@code fill}) and the circle (via {@link #fillScanlineCircle}) are
 * built entirely out of {@code fill(...)} calls on integer pixel rectangles.
 * This is deliberately the least version-fragile option available: it needs
 * no custom {@code VertexConsumer}/{@code RenderLayer}/{@code RenderPipeline}
 * setup, and therefore does not depend on the internal restructuring of
 * Blaze3D/{@code GuiRenderState} that shipped around Minecraft 1.21.6-1.21.8
 * (which is exactly the kind of internal, not-fully-decompiled-here surface
 * this task's rules say not to guess at). The tradeoff, disclosed per spec
 * section 41/"critical technical honesty": the circle's edge is rasterized to
 * whole pixels with no anti-aliasing, and the indicator's floating-point
 * {@code renderOffsetX/Y} position is rounded to the nearest pixel every
 * frame rather than rendered at literal sub-pixel precision. At the
 * recommended widget sizes (60-100px) this is visually smooth; a
 * sub-pixel/anti-aliased version would need a custom shader/vertex pipeline
 * that was not independently verified against 1.21.11 in this session and is
 * therefore not included.
 *
 * <p>No matrix stack push/pop, blend-state, or scissor manipulation is
 * performed by this class at all: every coordinate passed to {@code fill(...)}
 * is an already-resolved absolute screen pixel, and {@code fill(...)} manages
 * its own draw/blend state internally. There is therefore no rendering state
 * left over to restore (spec section 17) - the safest way to satisfy "restore
 * all state" is to never touch any state outside of these calls in the first
 * place.
 */
public final class MouseStrokesHud implements HudElement {

	/** Fixed per spec section 12: flat black, exactly ~20% opacity, not configurable.
	 *  0x33 = 51 = round(0.20 * 255). */
	private static final int BACKGROUND_ARGB = 0x33000000;

	private final MouseStrokesConfig config;
	private final MouseStrokeState state;

	public MouseStrokesHud(MouseStrokesConfig config, MouseStrokeState state) {
		this.config = config;
		this.state = state;
	}

	@Override
	public void render(DrawContext context, RenderTickCounter tickCounter) {
		if (!config.enabled) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		// Spec section 27/28: do not draw over menus/screens, and only while
		// actually in a world. When any of this is true, MouseStrokesClientLifecycle's
		// tick hook is responsible for resetting AND continuously draining
		// telemetry, so nothing stale builds up while we are not rendering -
		// see that class's doc comment. We deliberately do NOT call
		// state.update(...) in that case: GUI-cursor movement while a screen
		// is open must never be interpreted as a physical "stroke".
		if (client.player == null || client.currentScreen != null) {
			return;
		}

		// Advance the animation exactly once per render call.
		state.update(config);

		int windowW = context.getScaledWindowWidth();
		int windowH = context.getScaledWindowHeight();

		int size = config.widgetSize;
		int[] originXY = resolveAnchorOrigin(config.anchor, windowW, windowH, size, config.offsetX, config.offsetY);
		int squareX1 = originXY[0];
		int squareY1 = originXY[1];
		int squareX2 = squareX1 + size;
		int squareY2 = squareY1 + size;

		int thickness = config.outlineThickness;
		fillBorderRing(context, squareX1, squareY1, squareX2, squareY2, thickness, config.accentColor);
		context.fill(squareX1 + thickness, squareY1 + thickness, squareX2 - thickness, squareY2 - thickness, BACKGROUND_ARGB);

		double centerX = squareX1 + size / 2.0;
		double centerY = squareY1 + size / 2.0;

		int maxRadius = config.effectiveMaxRadius();
		double[] clampedOffset = clampMagnitude(state.renderOffsetX(), state.renderOffsetY(), maxRadius);

		int indicatorCenterX = (int) Math.round(centerX + clampedOffset[0]);
		int indicatorCenterY = (int) Math.round(centerY + clampedOffset[1]);

		fillScanlineCircle(context, indicatorCenterX, indicatorCenterY, config.circleRadius, config.accentColor);
	}

	/**
	 * Draws a picture-frame-shaped opaque border (4 rectangles, corners
	 * included, no overlap with the interior) so that the translucent
	 * interior fill (drawn separately) blends against the WORLD behind the
	 * HUD rather than against the border's own color. See class docs and the
	 * "double-fill would composite incorrectly" note in the accompanying
	 * explanation for why these two fills must not overlap.
	 */
	private static void fillBorderRing(DrawContext context, int x1, int y1, int x2, int y2, int thickness, int color) {
		context.fill(x1, y1, x2, y1 + thickness, color);           // top strip
		context.fill(x1, y2 - thickness, x2, y2, color);           // bottom strip
		context.fill(x1, y1 + thickness, x1 + thickness, y2 - thickness, color); // left strip
		context.fill(x2 - thickness, y1 + thickness, x2, y2 - thickness, color); // right strip
	}

	/**
	 * Fills a mathematically exact circle (x^2 + y^2 <= r^2) using one
	 * horizontal {@code fill(...)} call per pixel row - see class docs for
	 * why this approach was chosen over a custom vertex-based renderer.
	 */
	private static void fillScanlineCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
		if (radius <= 0) {
			return;
		}
		for (int rowOffset = -radius; rowOffset <= radius; rowOffset++) {
			double maxXOffsetSquared = (double) radius * radius - (double) rowOffset * rowOffset;
			if (maxXOffsetSquared < 0) {
				continue;
			}
			int halfWidth = (int) Math.round(Math.sqrt(maxXOffsetSquared));
			int rowY = centerY + rowOffset;
			context.fill(centerX - halfWidth, rowY, centerX + halfWidth + 1, rowY + 1, color);
		}
	}

	private static double[] clampMagnitude(double x, double y, int maxRadius) {
		double magnitude = Math.hypot(x, y);
		if (magnitude <= maxRadius || magnitude == 0.0) {
			return new double[] { x, y };
		}
		double scale = maxRadius / magnitude;
		return new double[] { x * scale, y * scale };
	}

	/**
	 * Resolves the requested {@link MouseStrokesConfig.Anchor} plus
	 * offsetX/offsetY into an absolute top-left (x,y) for the square, given
	 * the current scaled window dimensions. Recomputed every frame, so a
	 * resolution or GUI-scale change is picked up automatically without any
	 * stored absolute position going stale (spec section 18).
	 */
	private static int[] resolveAnchorOrigin(MouseStrokesConfig.Anchor anchor, int windowW, int windowH,
			int size, int offsetX, int offsetY) {
		int x;
		int y;
		switch (anchor) {
			case TOP_LEFT -> { x = offsetX; y = offsetY; }
			case TOP_CENTER -> { x = (windowW - size) / 2 + offsetX; y = offsetY; }
			case TOP_RIGHT -> { x = windowW - size - offsetX; y = offsetY; }
			case CENTER_LEFT -> { x = offsetX; y = (windowH - size) / 2 + offsetY; }
			case CENTER -> { x = (windowW - size) / 2 + offsetX; y = (windowH - size) / 2 + offsetY; }
			case CENTER_RIGHT -> { x = windowW - size - offsetX; y = (windowH - size) / 2 + offsetY; }
			case BOTTOM_LEFT -> { x = offsetX; y = windowH - size - offsetY; }
			case BOTTOM_CENTER -> { x = (windowW - size) / 2 + offsetX; y = windowH - size - offsetY; }
			case BOTTOM_RIGHT -> { x = windowW - size - offsetX; y = windowH - size - offsetY; }
			default -> { x = windowW - size - offsetX; y = windowH - size - offsetY; }
		}
		return new int[] { x, y };
	}
}
