package com.ss.landanmakuplayer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.IOException;

/**
 * Created by ss on 7/6/16.
 */
public class Live555ServerService extends Service {
    private Live555VideoServer server;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        server = new Live555VideoServer( intent.getStringExtra(AppConstant.VIDEO_FILE));
        server.start();
        return super.onStartCommand(intent, flags, startId);
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
