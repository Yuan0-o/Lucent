package com.lucent.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * **Blackout Mode** (C-group task 1) — the app's maximum-privacy switch.
 *
 * ### What it is
 *
 * One toggle that puts Lucent into a state where nothing it holds can leave the device and nothing
 * on screen can be seen by anyone who is not holding an unlocked phone. It is deliberately a single
 * switch rather than a page of them: the whole value of a "panic" privacy control is that it is one
 * decision made in one second, not eight decisions made carefully.
 *
 * ### The supremacy rule
 *
 * While Blackout is on it **outranks every other setting in the app**. A user who has web search on,
 * a cloud API configured, share integration enabled and the app lock off still gets: no network, no
 * share surface, and a mandatory password. Individual settings are not edited to say so — they are
 * *overridden in the read path* ([networkAllowed] and friends are consulted at the point of use), so
 * nothing the user configured is destroyed by flipping this on.
 *
 * ### Restoring the previous state
 *
 * The two settings Blackout genuinely has to *write* (app lock on, share integration off) are parked
 * before they are overwritten and handed back when Blackout is switched off — the same park/restore
 * shape [SettingsRepository.setLocalModelEnabled] already uses for local-model mode, chosen so both
 * features behave identically and there is only one idea to learn.
 *
 * A restore never silently *lowers* security: if the user turned the app lock on by hand while
 * Blackout was running, leaving Blackout keeps the lock on regardless of what was parked. Parking is
 * there to avoid taking something away that the user had, not to undo their later choices.
 *
 * ### What it costs (surfaced to the user before they turn it on)
 *
 * Blackout is not free, and the confirmation dialog says so in plain language:
 *  - the cloud assistant, web search and model downloads stop working entirely;
 *  - the app disappears from the system share sheet;
 *  - a password becomes mandatory, and **a forgotten password with no security question is
 *    unrecoverable**;
 *  - screenshots and the recents-screen thumbnail are blocked, so the app looks black when switched
 *    away from.
 *
 * ### Process-wide state, not a Flow
 *
 * [active] is plain snapshot state read from non-composable code (the OkHttp interceptor, the
 * lifecycle callbacks) as well as from composables. It is hydrated once at startup from the stored
 * flag and written through [SettingsRepository] whenever it changes, so the persisted value and the
 * live gate can never disagree.
 */
object BlackoutMode {

    /** The live gate. Read by the network layer, the lock controller and the UI. */
    var active: Boolean by mutableStateOf(false)
        private set

    /**
     * Hydrate the in-memory gate from persisted settings. Called once, early in `MainActivity.onCreate`,
     * on the same pre-first-frame path the theme is read on — the gate must be correct *before* the
     * first composition, or a single frame of a non-blacked-out UI could be captured by the recents
     * screenshot the mode exists to prevent.
     */
    fun hydrate(enabled: Boolean) {
        active = enabled
    }

    /**
     * Whether the app may touch the network at all right now.
     *
     * This is the single question every network call site asks. It is phrased as "allowed" rather
     * than "blackout is off" so that future reasons to refuse the network (an offline setting, a
     * metered-connection rule) can be added here without revisiting thirty call sites.
     */
    fun networkAllowed(): Boolean = !active

    /**
     * Whether cloud-backed assistant surfaces should render as frozen (visible but inert).
     *
     * Frozen, not hidden: a control that vanishes leaves the user wondering whether they imagined
     * it, while a control that is visibly disabled with a reason attached teaches them what the
     * switch they just flipped actually did.
     */
    fun cloudSurfacesFrozen(): Boolean = active

    /** Whether the app lock may be switched off. It may not while Blackout is holding it on. */
    fun appLockLocked(): Boolean = active

    /**
     * The re-lock grace period Blackout imposes, in milliseconds.
     *
     * Normally the lock allows a short window so an in-app file picker doesn't demand the password
     * again the instant it returns. Blackout sets that window to zero: "re-entering the app asks for
     * the password" is one of the things the user turned this on to get, and a convenience window is
     * exactly the hole it would open.
     */
    const val GRACE_MS_WHEN_ACTIVE: Long = 0L
}
