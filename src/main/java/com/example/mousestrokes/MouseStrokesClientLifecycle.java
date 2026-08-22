package com.example.mousestrokes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import com.example.mousestrokes.config.MouseStrokesConfig;
import com.example.mousestrokes.state.MouseStrokeState;
import com.example.mousestrokes.telemetry.MouseTelemetry;

/**
 * Covers spec section 29 ("input lifecycle") and section 28 ("pause
 * behavior") using a single {@code ClientTickEvents.END_CLIENT_TICK} hook
 * rather than several separate connection/screen/focus events, to keep the
 * set of Fabric API surfaces this mod depends on small and easy to verify.
 *
 * <p><b>ASSUMPTION</b> (high confidence, not re-verified against 1.21.11 in
 * this session): {@code net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents}
 * and {@code MinecraftClient#isWindowFocused()} are long-standing, stable
 * Fabric/vanilla surfaces present across many Minecraft versions with no
 * indication of removal in 1.21.11. If Fabric API 0.141.x for 1.21.11 turns
 * out to have moved this event, the compiler will fail on the import and
 * that is the first thing to check (see the build troubleshooting section).
 *
 * <p>Every tick, this class detects three transitions and reacts to each by
 * fully resetting {@link MouseStrokeState} and {@link MouseTelemetry}:
 * <ul>
 *   <li>the "widget should be hidden" condition (disabled in config, no
 *       player/world, a screen is open, or the window lost focus) becoming
 *       true when it was previously false;</li>
 *   <li>the current {@link ClientWorld} instance changing (covers joining a
 *       world, leaving to the title screen (world becomes null), and
 *       changing dimension, since each of those produces a new/absent
 *       {@code ClientWorld} reference).</li>
 * </ul>
 * <p><b>NOTE</b>: this mod does not currently implement a live config-reload
 * command - {@link MouseStrokesConfig#load()} runs once at startup, and the
 * in-game config screen mutates that same in-memory object directly, so
 * there is no separate "reload" event for this class to hook. If a
 * reload-from-disk command is added later, it should call
 * {@link #resetAll()} afterwards for the same reason a world change does.</p>
 * While the "should be hidden" condition remains continuously true, raw
 * telemetry is drained and discarded every tick (not just once on the
 * transition) so that, for example, browsing an inventory screen for several
 * minutes cannot build up a large accumulated delta that would otherwise
 * appear as one large jump the next time the widget is shown.
 */
public final class MouseStrokesClientLifecycle {

	private final MouseStrokesConfig config;
	private final MouseStrokeState state;

	private boolean wasHiddenLastTick = true;
	private ClientWorld lastWorldReference = null;

	public MouseStrokesClientLifecycle(MouseStrokesConfig config, MouseStrokeState state) {
		this.config = config;
		this.state = state;
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
	}

	private void onEndClientTick(MinecraftClient client) {
		boolean hidden = !config.enabled
				|| client.player == null
				|| client.currentScreen != null
				|| !client.isWindowFocused();

		if (hidden) {
			if (!wasHiddenLastTick) {
				resetAll();
			} else {
				// Keep draining so nothing accumulates while hidden.
				MouseTelemetry.INSTANCE.consumeSinceLastFrame();
			}
		}
		wasHiddenLastTick = hidden;

		ClientWorld currentWorld = client.world;
		if (currentWorld != lastWorldReference) {
			resetAll();
			lastWorldReference = currentWorld;
		}
	}

	public void resetAll() {
		state.reset();
		MouseTelemetry.INSTANCE.reset();
	}
}
