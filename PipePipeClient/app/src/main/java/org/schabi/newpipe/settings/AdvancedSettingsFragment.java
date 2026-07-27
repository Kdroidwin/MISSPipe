package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.io.IOException;

public class AdvancedSettingsFragment extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();
        initializeAndroidAutoPreference();
        requirePreference(R.string.download_thumbnail_key).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    PicassoHelper.setShouldLoadImages((Boolean) newValue);
                    try {
                        PicassoHelper.clearCache(preference.getContext());
                        Toast.makeText(preference.getContext(),
                                R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT)
                                .show();
                    } catch (final IOException e) {
                        Log.e(TAG, "Unable to clear Picasso cache", e);
                    }
                    return true;
                });

        findPreference(getString(R.string.use_experimental_new_ui_key))
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    defaultPreferences.edit()
                            .putBoolean(getString(R.string.use_experimental_new_ui_key),
                                    (Boolean) newValue)
                            .commit();
                    final Activity activity = getActivity();
                    if (activity != null) {
                        NavigationHelper.restartApp(activity);
                    }
                    return true;
                });

        findPreference(getString(R.string.use_dns_over_https_fallback_key))
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    defaultPreferences.edit()
                            .putBoolean(getString(R.string.use_dns_over_https_fallback_key),
                                    (Boolean) newValue)
                            .commit();
                    final Activity activity = getActivity();
                    if (activity != null) {
                        NavigationHelper.restartApp(activity);
                    }
                    return true;
                });

        if (DeviceUtils.isTv(getContext())) {
            findPreference(getString(R.string.use_old_search_filter_key)).setVisible(false);
        }

        updateAutoTranslatedSubtitlesPreferences();
    }
    
    private void initializeAndroidAutoPreference() {
        final SwitchPreferenceCompat androidAutoPref = findPreference(getString(R.string.disable_android_auto_key));
        if (androidAutoPref != null) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            final String key = getString(R.string.disable_android_auto_key);
            final String initKey = key + "_initialized";

            if (!prefs.contains(initKey)) {
                final boolean defaultValue = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
                prefs.edit()
                    .putBoolean(key, defaultValue)
                    .putBoolean(initKey, true)
                    .apply();
                androidAutoPref.setChecked(defaultValue);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.loading_timeout_key))) {
            ServiceHelper.initServices(this.getContext());
        } else if (key.equals(getString(R.string.disable_android_auto_key))) {
            DeviceUtils.updateAndroidAutoComponentState(requireContext());
        } else if (key.equals(getString(R.string.fetch_full_playlist_key))) {
            ServiceHelper.initServices(this.getContext());
        } else if (key.equals(getString(R.string.show_dislike_key))) {
            ServiceHelper.initServices(this.getContext());
        } else if (key.equals(getString(R.string.youtube_cookies_key))
                || key.equals(getString(R.string.show_auto_translated_subtitles_key))) {
            updateAutoTranslatedSubtitlesPreferences();
            ServiceHelper.initServices(this.getContext());
        } else if (key.equals(getString(R.string.auto_translated_subtitles_language_key))) {
            ServiceHelper.initServices(this.getContext());
        } else if (key.equals(getString(R.string.youtube_player_client_key))) {
            NewPipe.setYoutubePlayerClient(sharedPreferences.getString(key, "mweb"));
        }
    }

    private void updateAutoTranslatedSubtitlesPreferences() {
        final SwitchPreferenceCompat autoTranslatedSubtitlesPreference =
                findPreference(getString(R.string.show_auto_translated_subtitles_key));
        final ListPreference autoTranslatedSubtitlesLanguagePreference =
                findPreference(getString(R.string.auto_translated_subtitles_language_key));
        if (autoTranslatedSubtitlesPreference == null
                || autoTranslatedSubtitlesLanguagePreference == null) {
            return;
        }

        final String youtubeCookies = defaultPreferences.getString(
                getString(R.string.youtube_cookies_key), null);
        final boolean hasYouTubeLogin = !TextUtils.isEmpty(youtubeCookies);
        final boolean autoTranslatedSubtitlesEnabled = defaultPreferences.getBoolean(
                getString(R.string.show_auto_translated_subtitles_key), true);

        autoTranslatedSubtitlesPreference.setEnabled(hasYouTubeLogin);
        autoTranslatedSubtitlesPreference.setSummary(hasYouTubeLogin
                ? getString(R.string.show_auto_translated_subtitles_summary)
                : getString(R.string.show_auto_translated_subtitles_login_required_summary));
        autoTranslatedSubtitlesLanguagePreference.setEnabled(hasYouTubeLogin
                && autoTranslatedSubtitlesEnabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAutoTranslatedSubtitlesPreferences();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
