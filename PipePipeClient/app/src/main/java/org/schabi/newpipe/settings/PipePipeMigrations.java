package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.HashSet;
import java.util.Set;

public final class PipePipeMigrations {
    public static final Migration MIGRATION_0_1 = new Migration(0, 1) {
        @Override
        protected void migrate(final Context context, final SharedPreferences preferences) {
            migrateLegacyListViewMode(context, preferences);
            migrateLegacyChannelTabs(context, preferences);
            migrateLegacyVideoTabs(context, preferences);
            migrateShowFutureItemsToFilterFutureItems(context, preferences);
            migrateLegacyCommentsInnerScroll(context, preferences);
        }
    };

    private static final Migration[] PIPEPIPE_MIGRATIONS = {
            MIGRATION_0_1,
    };

    public static final int VERSION = 1;

    public static void initMigrations(final Context context, final boolean isFirstRun) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String versionKey = context.getString(R.string.last_used_pipepipe_preferences_version);
        final int lastVersion = preferences.getInt(versionKey, 0);

        if (isFirstRun) {
            preferences.edit().putInt(versionKey, VERSION).apply();
            return;
        } else if (lastVersion == VERSION) {
            return;
        }

        int currentVersion = lastVersion;
        for (final Migration migration : PIPEPIPE_MIGRATIONS) {
            if (migration.shouldMigrate(currentVersion)) {
                migration.migrate(context, preferences);
                currentVersion = migration.newVersion;
            }
        }

        preferences.edit().putInt(versionKey, currentVersion).apply();
    }

    public static void migrateLegacyChannelTabs(final Context context,
                                                final SharedPreferences preferences) {
        final String key = context.getString(R.string.show_channel_tabs_key);
        final Set<String> enabledTabs = preferences.getStringSet(key, null);
        if (enabledTabs == null) {
            return;
        }

        final Set<String> newSet = new HashSet<>(enabledTabs);
        if (enabledTabs.contains("show_channel_tabs_livestreams")) {
            newSet.remove("show_channel_tabs_livestreams");
            newSet.add(context.getString(R.string.show_channel_tabs_livestreams));
        }
        newSet.add(context.getString(R.string.show_channel_tabs_podcasts));
        preferences.edit().putStringSet(key, newSet).apply();
    }

    private static void migrateLegacyVideoTabs(final Context context,
                                               final SharedPreferences preferences) {
        final String key = context.getString(R.string.video_tabs_key);
        if (preferences.contains(key)) {
            return;
        }

        final Set<String> tabs = new HashSet<>();
        if (preferences.getBoolean(context.getString(R.string.show_comments_key), true)) {
            tabs.add("comments");
        }
        if (preferences.getBoolean(context.getString(R.string.show_next_video_key), true)) {
            tabs.add("related");
        }
        if (preferences.getBoolean(context.getString(R.string.show_description_key), true)) {
            tabs.add("description");
        }
        if (preferences.getBoolean(context.getString(R.string.sponsor_block_enable_key), true)) {
            tabs.add("sponsorblock");
        }

        preferences.edit().putStringSet(key, tabs).apply();
    }

    private static void migrateLegacyListViewMode(final Context context,
                                                  final SharedPreferences preferences) {
        final String migrationKey = context.getString(R.string.list_view_mode_migrated_key);
        if (preferences.getBoolean(migrationKey, false)) {
            return;
        }

        final String listMode = preferences.getString(context.getString(R.string.list_view_mode_key),
                context.getString(R.string.list_view_mode_value));
        final SharedPreferences.Editor editor = preferences.edit();
        final Configuration configuration = context.getResources().getConfiguration();
        final boolean isLargeScreen = configuration.isLayoutSizeAtLeast(
                Configuration.SCREENLAYOUT_SIZE_LARGE);
        final boolean isAuto = listMode.equals(context.getString(R.string.list_view_mode_auto_key));
        final boolean isCard = listMode.equals(context.getString(R.string.list_view_mode_card_key));
        final boolean isList = listMode.equals(context.getString(R.string.list_view_mode_list_key));
        final boolean autoUseGrid = isAuto
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                && isLargeScreen;
        final boolean useGrid = !isCard && !isList && (
                listMode.equals(context.getString(R.string.list_view_mode_grid_key))
                        || listMode.equals(context.getString(R.string.list_view_mode_large_grid_key))
                        || autoUseGrid);

        if (isCard) {
            editor.putString(context.getString(R.string.grid_columns_key), "1");
            editor.putString(context.getString(R.string.grid_columns_landscape_key), "1");
        } else if (isLargeScreen
                && listMode.equals(context.getString(R.string.list_view_mode_grid_key))) {
            editor.putString(context.getString(R.string.grid_columns_key), "4");
            editor.putString(context.getString(R.string.grid_columns_landscape_key), "8");
        } else if (isLargeScreen) {
            editor.putString(context.getString(R.string.grid_columns_key), "3");
            editor.putString(context.getString(R.string.grid_columns_landscape_key), "6");
        } else {
            editor.putString(context.getString(R.string.grid_columns_key), "2");
            editor.putString(context.getString(R.string.grid_columns_landscape_key), "4");
        }

        editor.putBoolean(context.getString(R.string.grid_layout_enabled_key), useGrid);
        editor.putBoolean(context.getString(R.string.card_mode_enabled_key), isCard);
        editor.putBoolean(migrationKey, true).apply();
    }

    private static void migrateShowFutureItemsToFilterFutureItems(
            final Context context,
            final SharedPreferences preferences) {
        final String oldKey = context.getString(R.string.toggle_show_future_items_key);
        if (!preferences.contains(oldKey)) {
            return;
        }

        final String newKey = context.getString(R.string.filter_future_items_key);
        preferences.edit()
                .putBoolean(newKey, !preferences.getBoolean(oldKey, false))
                .remove(oldKey)
                .apply();
    }

    private static void migrateLegacyCommentsInnerScroll(
            final Context context,
            final SharedPreferences preferences) {
        final String oldKey = "comments_inner_scroll_key";
        if (!preferences.contains(oldKey)) {
            return;
        }

        final String newKey = context.getString(R.string.pin_video_to_top_key);
        preferences.edit()
                .putBoolean(newKey, preferences.getBoolean(oldKey, true))
                .remove(oldKey)
                .apply();
    }

    private PipePipeMigrations() { }

    abstract static class Migration {
        public final int oldVersion;
        public final int newVersion;

        protected Migration(final int oldVersion, final int newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        private boolean shouldMigrate(final int currentVersion) {
            return oldVersion >= currentVersion;
        }

        protected abstract void migrate(Context context, SharedPreferences preferences);
    }
}
