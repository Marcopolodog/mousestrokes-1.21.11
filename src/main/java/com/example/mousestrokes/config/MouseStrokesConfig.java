package com.example.mousestrokes.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side configuration. Persisted as JSON via Gson, which ships with
 * every Minecraft/Fabric client (VERIFIED: Gson has been a bundled Minecraft
 * dependency for many years; using it introduces no new external dependency -
 * see spec section 14, "dependency discipline").
 *
 * <p>Every setter clamps to a safe range so a hand-edited or corrupted config
 * file cannot crash the client (spec section 21) - {@link #sanitize()} is
 * called after every load.
 *
 * <p>Fields intentionally NOT present here, per spec section 20: background
 * color/opacity (hard-coded, see {@code MouseStrokesHud.BACKGROUND_ARGB}) and
 * a separate indicator color (the indicator always reads {@link #accentColor}
 * as well - there is exactly one color source).
 */
public final class MouseStrokesConfig {

	public enum Anchor {
		TOP_LEFT, TOP_CENTER, TOP_RIGHT,
		CENTER_LEFT, CENTER, CENTER_RIGHT,
		BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
	}

	private static final String FILE_NAME = "mousestrokes.json";

	// ---- Hard bounds (spec section 21: "clamp invalid values to safe ranges") ----
	private static final int MIN_WIDGET_SIZE = 24;
	private static final int MAX_WIDGET_SIZE = 400;
	private static final int MIN_CIRCLE_RADIUS = 2;
	private static final int MIN_OUTLINE_THICKNESS = 1;
	private static final int MAX_OUTLINE_THICKNESS = 8;
	private static final double MIN_SENSITIVITY = 0.05;
	private static final double MAX_SENSITIVITY = 20.0;
	private static final double MIN_RETURN_SPEED = 0.02;
	private static final double MAX_RETURN_SPEED = 2.0;
	private static final long MIN_RESET_DELAY_MS = 0;
	private static final long MAX_RESET_DELAY_MS = 2000;

	public boolean enabled = true;

	/** Full side length of the square, in HUD (scaled) pixels. Default chosen
	 *  per spec section 11 ("approximately 60-100"): 80px reads clearly at
	 *  common GUI scales without crowding other HUD elements. */
	public int widgetSize = 80;

	public Anchor anchor = Anchor.BOTTOM_RIGHT;
	public int offsetX = 12;
	public int offsetY = 12;

	/** Packed ARGB (0xAARRGGBB). Shared by the outline AND the indicator -
	 *  see spec section 14, there is only ever one color source. Default:
	 *  fully opaque cyan, a common competitive-HUD accent. */
	public int accentColor = 0xFF00E5FF;

	/** Raw-input-to-HUD-pixel scale. Does not affect direction, only how far
	 *  a given raw delta pushes the indicator (spec section 22). */
	public double inputSensitivity = 3.0;

	/** Requested maximum indicator travel from center, in HUD pixels, BEFORE
	 *  the interior-radius clamp described in spec section 7 is applied. The
	 *  effective clamp actually used is always
	 *  {@code min(maximumDisplacement, widgetSize/2 - circleRadius)}. */
	public int maximumDisplacement = 26;

	/** Time constant (seconds) of the exponential return-to-center decay.
	 *  Smaller = snappier return. See spec section 9. */
	public double returnSpeed = 0.12;

	/** Grace period (ms) after the last raw movement before the indicator
	 *  begins returning to center (spec section 9, optional field). 0 means
	 *  "start returning immediately on the next frame with no movement". */
	public long resetDelayMs = 40;

	public int circleRadius = 6;
	public int outlineThickness = 2;

	public static MouseStrokesConfig load() {
		Path path = configPath();
		if (!Files.exists(path)) {
			MouseStrokesConfig fresh = new MouseStrokesConfig();
			fresh.sanitize();
			fresh.save();
			return fresh;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			MouseStrokesConfig loaded = gson().fromJson(reader, MouseStrokesConfig.class);
			if (loaded == null) {
				loaded = new MouseStrokesConfig();
			}
			loaded.sanitize();
			return loaded;
		} catch (IOException | RuntimeException e) {
			// Malformed JSON, IO error, etc: never crash the client over a
			// broken config file (spec section 21) - fall back to defaults.
			MouseStrokesConfig fallback = new MouseStrokesConfig();
			fallback.sanitize();
			return fallback;
		}
	}

	public void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				gson().toJson(this, writer);
			}
		} catch (IOException ignored) {
			// Best-effort persistence; failing to write the config file
			// should not interrupt gameplay.
		}
	}

	/** Clamps every field into its documented safe range. Idempotent. */
	public void sanitize() {
		widgetSize = clamp(widgetSize, MIN_WIDGET_SIZE, MAX_WIDGET_SIZE);
		circleRadius = clamp(circleRadius, MIN_CIRCLE_RADIUS, widgetSize / 2 - 1);
		outlineThickness = clamp(outlineThickness, MIN_OUTLINE_THICKNESS, MAX_OUTLINE_THICKNESS);
		inputSensitivity = clamp(inputSensitivity, MIN_SENSITIVITY, MAX_SENSITIVITY);
		returnSpeed = clamp(returnSpeed, MIN_RETURN_SPEED, MAX_RETURN_SPEED);
		resetDelayMs = clamp(resetDelayMs, MIN_RESET_DELAY_MS, MAX_RESET_DELAY_MS);

		int hardInteriorLimit = Math.max(0, widgetSize / 2 - circleRadius);
		maximumDisplacement = clamp(maximumDisplacement, 0, Math.max(0, hardInteriorLimit));

		if (anchor == null) {
			anchor = Anchor.BOTTOM_RIGHT;
		}
		// Force full alpha on the accent color's top byte is deliberately
		// NOT done here: a user may want a semi-transparent outline/indicator,
		// and nothing in the spec forbids that (only the background alpha is
		// fixed). Any alpha value therefore passes through unchanged.
	}

	/** Effective clamp applied to indicator travel this frame - see spec section 7. */
	public int effectiveMaxRadius() {
		int interior = widgetSize / 2 - circleRadius;
		return Math.max(0, Math.min(maximumDisplacement, interior));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	private static Gson gson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}
}
