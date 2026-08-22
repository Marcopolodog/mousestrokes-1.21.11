package com.example.mousestrokes;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

import com.example.mousestrokes.config.MouseStrokesConfig;
import com.example.mousestrokes.config.MouseStrokesConfigScreen;
import com.example.mousestrokes.render.MouseStrokesHud;
import com.example.mousestrokes.state.MouseStrokeState;

/**
 * Entry point (spec section 39, deliverable 1). Only wiring lives here -
 * everything else is delegated to its own class per spec section 35.
 *
 * <h2>HUD attachment point (spec section 16/18)</h2>
 * <b>VERIFIED</b> (Fabric API 0.129.0+1.21.7 javadoc for
 * {@code HudElementRegistry}, and the equivalent 1.21.11 API surface, which
 * carries the same class per the 1.21.6-1.21.11 changelog trail): attaching
 * after {@code VanillaHudElements.SUBTITLES} renders after every other
 * vanilla HUD layer, and - unlike {@code addFirst}/{@code addLast} -
 * inherits that layer's render condition, which is {@code GameOptions.hudHidden}.
 * This means pressing F1 to hide the vanilla HUD also hides this widget, for
 * free, without this mod needing to check {@code hudHidden} itself.
 */
public final class MouseStrokesMod implements ClientModInitializer {

	private static final String MOD_ID = "mousestrokes";

	private MouseStrokesConfig config;
	private MouseStrokeState state;
	private MouseStrokesClientLifecycle lifecycle;
	private KeyBinding openConfigKey;

	@Override
	public void onInitializeClient() {
		this.config = MouseStrokesConfig.load();
		this.state = new MouseStrokeState();
		this.lifecycle = new MouseStrokesClientLifecycle(config, state);
		lifecycle.register();

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.SUBTITLES,
				Identifier.of(MOD_ID, "mouse_strokes_widget"),
				new MouseStrokesHud(config, state));

		// ASSUMPTION (high confidence, not re-verified for 1.21.11 in this
		// session): KeyBindingHelper.registerKeyBinding + a plain KeyBinding
		// constructed with a translation key, GLFW key code, and category
		// translation key is a long-standing, stable Fabric API pattern.
		this.openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mousestrokes.open_config",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN, // unbound by default; user binds it in Controls
				"key.categories.misc"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MouseStrokesConfigScreen(null, config));
				}
			}
		});
	}
}
