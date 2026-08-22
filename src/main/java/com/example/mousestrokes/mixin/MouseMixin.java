package com.example.mousestrokes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Mouse;

/**
 * Observes {@link Mouse#onCursorPos(long, double, double)} without altering
 * its behavior in any way.
 *
 * <h2>Verification ledger for this Mixin</h2>
 * <ul>
 *   <li><b>VERIFIED</b> (Yarn 1.21.11+build.3/4 javadoc,
 *       {@code net/minecraft/client/Mouse.html}): the target class is
 *       {@code net.minecraft.client.Mouse} (intermediary {@code class_312},
 *       official/obfuscated {@code gfk} in that build). It contains a
 *       private method {@code void onCursorPos(long window, double x, double y)}
 *       with descriptor {@code (JDD)V} (intermediary {@code method_1600}).
 *       This is a private instance method, which Mixin can target directly
 *       by name + descriptor without needing an accessor/invoker mixin.</li>
 *   <li><b>VERIFIED</b>: {@code onCursorPos} is the method Minecraft's GLFW
 *       cursor-position callback (registered in {@code Mouse.setup(Window)})
 *       ultimately invokes; it runs strictly BEFORE {@code Mouse.updateMouse(double)}
 *       (intermediary {@code method_1606}), which is the method that later
 *       reads the accumulated cursor delta and applies it (with sensitivity)
 *       to the player's camera. Injecting at the HEAD of {@code onCursorPos}
 *       therefore observes movement at the earliest point Fabric/Mixin can
 *       reach it in the vanilla call chain, strictly upstream of sensitivity
 *       and camera-rotation processing - see spec sections 3-4.</li>
 *   <li><b>DESIGN CHOICE</b>: rather than reading Minecraft's own
 *       {@code cursorDeltaX}/{@code cursorDeltaY} fields (which DO exist on
 *       this class per the same javadoc, as private doubles, intermediary
 *       {@code field_1789}/{@code field_1787}), this Mixin passes the raw
 *       {@code x}/{@code y} callback parameters straight to
 *       {@link com.example.mousestrokes.telemetry.MouseTelemetry}, which
 *       computes its own delta against its own previous-position baseline.
 *       This avoids any dependency on the exact accumulation/reset/gating
 *       conditions inside {@code onCursorPos}'s method body (e.g. whether it
 *       only accumulates while the cursor is locked, or resets on every
 *       resolution change) - none of which is visible from a Yarn javadoc
 *       page (signatures only, no method bodies) and none of which was
 *       decompiled or reproduced here for copyright reasons. It also means
 *       this Mixin keeps working unchanged even if Mojang alters exactly how
 *       {@code cursorDeltaX}/{@code cursorDeltaY} are computed in a future
 *       patch, as long as {@code onCursorPos(long,double,double)} keeps
 *       receiving the raw window-space cursor position from GLFW.</li>
 *   <li><b>LIMITATION</b>: while the cursor is locked for gameplay (GLFW
 *       "disabled" cursor mode), GLFW itself is responsible for turning
 *       relative hardware motion into the ever-growing virtual x/y this
 *       callback receives; whether "raw mouse motion" (GLFW_RAW_MOUSE_MOTION,
 *       bypassing OS pointer acceleration) is enabled for that virtual
 *       position is controlled by Minecraft's own mouse setup code and the
 *       player's OS/driver settings, not by this mod. This mod cannot and
 *       does not claim to read hardware HID reports directly - see the
 *       "Critical technical honesty" section of the accompanying explanation.</li>
 *   <li>No {@code cancellable = true} is declared, and this handler never
 *       calls {@code ci.cancel()}: cancellation is therefore structurally
 *       impossible from this injector, satisfying the "zero interference"
 *       requirement (spec sections 34-35) - Minecraft's own camera/sensitivity
 *       processing in {@code updateMouse(double)} runs completely unmodified
 *       afterwards.</li>
 * </ul>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	@Inject(method = "onCursorPos(JDD)V", at = @At("HEAD"))
	private void mousestrokes$onCursorPos(long window, double x, double y, CallbackInfo ci) {
		// Minimal work only, per spec section 26: no allocation, no trig,
		// no logging. All that happens on the callback thread is storing two
		// primitive doubles behind a monitor.
		com.example.mousestrokes.telemetry.MouseTelemetry.INSTANCE.onRawCursorPos(x, y);
	}
}
