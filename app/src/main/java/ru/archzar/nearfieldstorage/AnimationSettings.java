package ru.archzar.nearfieldstorage;

import android.content.Context;
import android.content.SharedPreferences;

final class AnimationSettings {
    enum PanelMotion {
        FADE,
        BLINK
    }

    static final int DEFAULT_TRANSITION_MS = 500;
    static final int DEFAULT_PULSE_MS = 2000;
    static final int DEFAULT_PAUSE_MS = 500;
    static final int MAX_TRANSITION_MS = 2000;
    static final int MIN_PULSE_MS = 200;
    static final int MAX_PULSE_MS = 10000;

    private static final String FILE = "nfs_animation_preferences";
    private static final String TRANSITION = "transition_ms";
    private static final String PULSE = "pulse_ms";
    private static final String PAUSE = "pause_ms";
    private static final String SHARED_AXIS = "shared_axis";
    private static final String TOP_BAR_MOTION = "top_bar_motion";
    private static final String BOTTOM_BAR_MOTION = "bottom_bar_motion";

    final int transitionMs;
    final int pulseMs;
    final int pauseMs;
    final boolean sharedAxis;
    final PanelMotion topBarMotion;
    final PanelMotion bottomBarMotion;

    AnimationSettings(int transitionMs, int pulseMs, int pauseMs, boolean sharedAxis) {
        this(transitionMs, pulseMs, pauseMs, sharedAxis, PanelMotion.FADE, PanelMotion.FADE);
    }

    AnimationSettings(int transitionMs, int pulseMs, int pauseMs, boolean sharedAxis,
            PanelMotion topBarMotion, PanelMotion bottomBarMotion) {
        this.transitionMs = transitionMs;
        this.pulseMs = pulseMs;
        this.pauseMs = pauseMs;
        this.sharedAxis = sharedAxis;
        this.topBarMotion = topBarMotion;
        this.bottomBarMotion = bottomBarMotion;
    }

    static AnimationSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return new AnimationSettings(prefs.getInt(TRANSITION, DEFAULT_TRANSITION_MS),
                prefs.getInt(PULSE, DEFAULT_PULSE_MS), prefs.getInt(PAUSE, DEFAULT_PAUSE_MS),
                prefs.getBoolean(SHARED_AXIS, true), loadPanelMotion(prefs, TOP_BAR_MOTION),
                loadPanelMotion(prefs, BOTTOM_BAR_MOTION));
    }

    void save(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putInt(TRANSITION, transitionMs)
                .putInt(PULSE, pulseMs)
                .putInt(PAUSE, pauseMs)
                .putBoolean(SHARED_AXIS, sharedAxis)
                .putString(TOP_BAR_MOTION, topBarMotion.name())
                .putString(BOTTOM_BAR_MOTION, bottomBarMotion.name())
                .apply();
    }

    private static PanelMotion loadPanelMotion(SharedPreferences prefs, String key) {
        return PanelMotion.BLINK.name().equals(prefs.getString(key, null))
                ? PanelMotion.BLINK : PanelMotion.FADE;
    }
}
