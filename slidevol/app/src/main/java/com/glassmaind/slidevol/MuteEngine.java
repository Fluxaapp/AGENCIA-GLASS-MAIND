package com.glassmaind.slidevol;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

/**
 * Mutes the accessible Android audio streams, then restores their prior levels.
 * Optionally enables "no interruptions" if the user grants DND policy access.
 * Telephony/independent hardware output might not be muted by Android.
 */
final class MuteEngine {
    private static final int[] STREAMS = {
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_DTMF
    };
    private static final String KEY_MUTED = "allMuted";
    private final AudioManager audio;
    private final NotificationManager policy;
    private final SharedPreferences prefs;

    MuteEngine(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        policy = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        prefs = context.getSharedPreferences(SlideVolActivity.PREFS, Context.MODE_PRIVATE);
    }
    boolean isMuted() {
        return prefs.getBoolean(KEY_MUTED, false);
    }
    int rememberedMediaVolume() {
        if (isMuted()) return prefs.getInt("savedStream" + AudioManager.STREAM_MUSIC,
                         audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
    synchronized boolean mute() {
        if (isMuted()) return true;
        SharedPreferences.Editor e = prefs.edit();
        for (int stream : STREAMS) {
            try {
                e.putInt("savedStream" + stream, audio.getStreamVolume(stream));
            } catch (Exception ignored) { }
        }
        boolean permitted = policy != null && policy.isNotificationPolicyAccessGranted();
        if (permitted) {
            e.putInt("previousDnd", policy.getCurrentInterruptionFilter());
            e.putBoolean("ownsDnd", true);
        } else {
            e.putBoolean("ownsDnd", false);
        }
        e.putBoolean(KEY_MUTED, true).commit();
        for (int stream : STREAMS) {
            try { audio.setStreamVolume(stream, 0, 0); }
            catch (Exception ignored) { /* Manufacturer or DND may protect this stream. */ }
        }
        if (permitted) {
            try {
                policy.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            } catch (Exception ignored) { }
        }
        return true;
    }
    synchronized boolean restore() {
        if (!isMuted()) return false;
        boolean permitted = policy != null && policy.isNotificationPolicyAccessGranted();
        if (permitted && prefs.getBoolean("ownsDnd", false)) {
            try {
                if (policy.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    int original = prefs.getInt("previousDnd",
                            NotificationManager.INTERRUPTION_FILTER_ALL);
                    if (original != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                        policy.setInterruptionFilter(original);
                    }
                }
            } catch (Exception ignored) { }
        }
        for (int stream : STREAMS) {
            try {
                String key = "savedStream" + stream;
                if (prefs.contains(key)) {
                    int max = audio.getStreamMaxVolume(stream);
                    int old = prefs.getInt(key, 0);
                    audio.setStreamVolume(stream, Math.max(0, Math.min(max, old)), 0);
                }
            } catch (Exception ignored) { }
        }
        prefs.edit().putBoolean(KEY_MUTED, false).putBoolean("ownsDnd", false).apply();
        return false;
    }
    synchronized boolean toggle() {
        return isMuted() ? restore() : mute();
    }
}
