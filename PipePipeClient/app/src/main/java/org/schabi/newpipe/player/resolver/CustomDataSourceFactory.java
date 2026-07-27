package org.schabi.newpipe.player.resolver;

import android.content.Context;

import androidx.annotation.Nullable;

import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;

public class CustomDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final @Nullable TransferListener listener;
    private byte[] subtitles;

    public CustomDataSourceFactory(
            Context context,
            @Nullable TransferListener listener,
            byte[] subtitles) {
        this.context = context.getApplicationContext();
        this.subtitles = subtitles;
        this.listener = listener;
    }

    @Override
    public DataSource createDataSource() {
        ByteArrayDataSource dataSource = new ByteArrayDataSource(subtitles);
        if (listener != null) {
            dataSource.addTransferListener(listener);
        }
        return dataSource;
    }
}