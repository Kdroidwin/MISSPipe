package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** Stores short-lived authentication material outside normal app preferences and backups. */
public final class SecurePreferences {
    public static final String RECAPTCHA_PREFS = "recaptcha_session";
    public static final String YOUTUBE_ACCOUNT_PREFS = "youtube_account";

    private SecurePreferences() {
    }

    @NonNull
    public static SharedPreferences open(@NonNull final Context context,
                                         @NonNull final String name) {
        try {
            final MasterKey key = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(context.getApplicationContext(), name, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (final GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to open encrypted application storage", e);
        }
    }

    @NonNull
    public static SharedPreferences recaptcha(@NonNull final Context context) {
        final Context appContext = context.getApplicationContext();
        final SharedPreferences encrypted = open(appContext, RECAPTCHA_PREFS);
        final String key = appContext.getString(org.schabi.newpipe.R.string.recaptcha_cookies_key);
        final SharedPreferences legacy = PreferenceManager.getDefaultSharedPreferences(appContext);
        if (!encrypted.contains(key) && legacy.contains(key)) {
            final String value = legacy.getString(key, "");
            if (encrypted.edit().putString(key, value).commit()) {
                legacy.edit().remove(key).apply();
            }
        }
        return encrypted;
    }

    @NonNull
    public static SharedPreferences youtubeAccount(@NonNull final Context context) {
        final Context appContext = context.getApplicationContext();
        final SharedPreferences encrypted = open(appContext, YOUTUBE_ACCOUNT_PREFS);
        final SharedPreferences legacy = PreferenceManager.getDefaultSharedPreferences(appContext);
        final String cookiesKey = appContext.getString(org.schabi.newpipe.R.string.youtube_cookies_key);
        final String poTokenKey = appContext.getString(org.schabi.newpipe.R.string.youtube_po_token_key);
        final SharedPreferences.Editor migration = encrypted.edit();
        boolean changed = false;
        if (!encrypted.contains(cookiesKey) && legacy.contains(cookiesKey)) {
            migration.putString(cookiesKey, legacy.getString(cookiesKey, ""));
            changed = true;
        }
        if (!encrypted.contains(poTokenKey) && legacy.contains(poTokenKey)) {
            migration.putString(poTokenKey, legacy.getString(poTokenKey, ""));
            changed = true;
        }
        if (changed) {
            if (!migration.commit()) {
                return legacy;
            }
            legacy.edit().remove(cookiesKey).remove(poTokenKey).apply();
        }
        return encrypted;
    }
}
