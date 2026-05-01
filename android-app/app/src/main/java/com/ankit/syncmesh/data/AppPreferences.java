package com.ankit.syncmesh.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

public class AppPreferences {
    private static final String PREFS_NAME = "syncmesh_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PAIRING_CODE = "pairing_code";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_KEYBOARD_AUTO_SEND_ENABLED = "auto_send_keyboard";
    private static final String KEY_OLD_KEYBOARD_AUTO_SEND_ENABLED = "keyboard_auto_send_enabled";
    private static final String KEY_LAST_KEYBOARD_SENT_TEXT = "last_keyboard_sent_text";
    private static final String KEY_LAST_KEYBOARD_SENT_AT = "last_keyboard_sent_at";
    private static final String KEY_KEYBOARD_LANGUAGE = "keyboard_language";

    private final SharedPreferences preferences;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getDeviceId() {
        String value = preferences.getString(KEY_DEVICE_ID, null);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        value = UUID.randomUUID().toString();
        preferences.edit().putString(KEY_DEVICE_ID, value).apply();
        return value;
    }

    public String getPairingCode() {
        String value = preferences.getString(KEY_PAIRING_CODE, null);
        if (value != null && value.matches("\\d{6}")) {
            return value;
        }
        value = String.format(Locale.US, "%06d", secureRandom.nextInt(900000) + 100000);
        preferences.edit().putString(KEY_PAIRING_CODE, value).apply();
        return value;
    }

    public String getDeviceName() {
        String value = preferences.getString(KEY_DEVICE_NAME, null);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        value = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        preferences.edit().putString(KEY_DEVICE_NAME, value).apply();
        return value;
    }

    public void setDeviceName(String deviceName) {
        preferences.edit().putString(KEY_DEVICE_NAME, deviceName).apply();
    }

    public boolean isKeyboardAutoSendEnabled() {
        if (preferences.contains(KEY_KEYBOARD_AUTO_SEND_ENABLED)) {
            return preferences.getBoolean(KEY_KEYBOARD_AUTO_SEND_ENABLED, true);
        }
        return preferences.getBoolean(KEY_OLD_KEYBOARD_AUTO_SEND_ENABLED, true);
    }

    public void setKeyboardAutoSendEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_KEYBOARD_AUTO_SEND_ENABLED, enabled)
                .putBoolean(KEY_OLD_KEYBOARD_AUTO_SEND_ENABLED, enabled)
                .apply();
    }

    public String getLastKeyboardSentText() {
        return preferences.getString(KEY_LAST_KEYBOARD_SENT_TEXT, null);
    }

    public long getLastKeyboardSentAt() {
        return preferences.getLong(KEY_LAST_KEYBOARD_SENT_AT, 0L);
    }

    public void setLastKeyboardSentState(String text, long timestamp) {
        preferences.edit()
                .putString(KEY_LAST_KEYBOARD_SENT_TEXT, text)
                .putLong(KEY_LAST_KEYBOARD_SENT_AT, timestamp)
                .apply();
    }

    public String getKeyboardLanguage() {
        return preferences.getString(KEY_KEYBOARD_LANGUAGE, "en");
    }

    public void setKeyboardLanguage(String languageCode) {
        preferences.edit().putString(KEY_KEYBOARD_LANGUAGE, languageCode).apply();
    }
}
