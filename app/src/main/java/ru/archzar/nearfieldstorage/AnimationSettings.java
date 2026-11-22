package ru.archzar.nearfieldstorage;

import android.content.Context;
import android.content.SharedPreferences;

final class AnimationSettings {
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

    final int transitionMs;
    final int pulseMs;
    final int pauseMs;
    final boolean sharedAxis;

    AnimationSettings(int transitionMs, int pulseMs, int pauseMs, boolean sharedAxis) {
        this.transitionMs = transitionMs;
        this.pulseMs = pulseMs;
        this.pauseMs = pauseMs;
        this.sharedAxis = sharedAxis;
    }

    static AnimationSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return new AnimationSettings(prefs.getInt(TRANSITION, DEFAULT_TRANSITION_MS),
                prefs.getInt(PULSE, DEFAULT_PULSE_MS), prefs.getInt(PAUSE, DEFAULT_PAUSE_MS),
                prefs.getBoolean(SHARED_AXIS, true));
    }

    void save(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putInt(TRANSITION, transitionMs)
                .putInt(PULSE, pulseMs)
                .putInt(PAUSE, pauseMs)
                .putBoolean(SHARED_AXIS, sharedAxis)
                .apply();
    }
}
