package com.example.mousestrokes.config;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Minimal built-in configuration screen, deliberately NOT depending on
 * ModMenu or Cloth Config (spec section 21 only requires "a configuration
 * file, Mod Menu, or both" - a file plus this in-game screen already
 * satisfies "or both" without adding a third-party dependency whose exact
 * API surface for 1.21.11 was not independently verified in this session).
 *
 * <p><b>CONFIDENCE NOTE</b>: this file relies on {@code Screen},
 * {@code ButtonWidget.builder(Text, ButtonWidget.PressAction).dimensions(...).build()},
 * and {@code Text.literal(...)} / {@code Text.translatable(...)} - all long
 * -standing, stable GUI-toolkit classes, but their exact signatures were NOT
 * independently re-verified against Yarn 1.21.11 in this session the way
 * {@code Mouse} and {@code DrawContext} were. If this specific file fails to
 * compile, delete it and its one reference in {@code MouseStrokesMod}
 * (the keybinding registration) - the rest of the mod (telemetry, mixin,
 * HUD rendering) does not depend on it at all, and the widget remains fully
 * configurable by hand-editing {@code config/mousestrokes.json}.
 *
 * <p>Only cycles through preset values rather than accepting free-form text
 * input, specifically to avoid depending on {@code TextFieldWidget}'s exact
 * numeric-parsing/validation behavior, which would have been another
 * unverified surface.
 */
public final class MouseStrokesConfigScreen extends Screen {

	private static final int[] SIZE_PRESETS = { 60, 80, 100, 120 };
	private static final double[] SENSITIVITY_PRESETS = { 1.0, 2.0, 3.0, 5.0, 8.0 };
	private static final int[] COLOR_PRESETS = {
			0xFF00E5FF, // cyan
			0xFFFF3B3B, // red
			0xFF39FF6A, // green
			0xFFFFFFFF, // white
			0xFFFFC300  // amber
	};

	private final Screen parent;
	private final MouseStrokesConfig config;

	public MouseStrokesConfigScreen(Screen parent, MouseStrokesConfig config) {
		super(Text.literal("Mouse Strokes HUD"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 70;
		int rowHeight = 24;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Enabled: " + config.enabled),
				button -> {
					config.enabled = !config.enabled;
					button.setMessage(Text.literal("Enabled: " + config.enabled));
				}).dimensions(centerX - 100, y, 200, 20).build());
		y += rowHeight;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Size: " + config.widgetSize),
				button -> {
					config.widgetSize = nextInArray(SIZE_PRESETS, config.widgetSize);
					config.sanitize();
					button.setMessage(Text.literal("Size: " + config.widgetSize));
				}).dimensions(centerX - 100, y, 200, 20).build());
		y += rowHeight;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Anchor: " + config.anchor),
				button -> {
					MouseStrokesConfig.Anchor[] values = MouseStrokesConfig.Anchor.values();
					int next = (config.anchor.ordinal() + 1) % values.length;
					config.anchor = values[next];
					button.setMessage(Text.literal("Anchor: " + config.anchor));
				}).dimensions(centerX - 100, y, 200, 20).build());
		y += rowHeight;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Sensitivity: " + config.inputSensitivity),
				button -> {
					config.inputSensitivity = nextInArray(SENSITIVITY_PRESETS, config.inputSensitivity);
					config.sanitize();
					button.setMessage(Text.literal("Sensitivity: " + config.inputSensitivity));
				}).dimensions(centerX - 100, y, 200, 20).build());
		y += rowHeight;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Accent color"),
				button -> config.accentColor = nextInArray(COLOR_PRESETS, config.accentColor)
				).dimensions(centerX - 100, y, 200, 20).build());
		y += rowHeight;

		this.addDrawableChild(ButtonWidget.builder(
				Text.literal("Done"),
				button -> {
					config.sanitize();
					config.save();
					this.close();
				}).dimensions(centerX - 100, y + 10, 200, 20).build());
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(parent);
		}
	}

	private static int nextInArray(int[] values, int current) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == current) {
				return values[(i + 1) % values.length];
			}
		}
		return values[0];
	}

	private static double nextInArray(double[] values, double current) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == current) {
				return values[(i + 1) % values.length];
			}
		}
		return values[0];
	}
}
