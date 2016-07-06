package com.ss.landanmakuplayer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.IOException;

public class VidServerService extends Service {
    private H264VideoServer server;
    public VidServerService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        server = new H264VideoServer( intent.getStringExtra(AppConstant.VIDEO_FILE));
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
