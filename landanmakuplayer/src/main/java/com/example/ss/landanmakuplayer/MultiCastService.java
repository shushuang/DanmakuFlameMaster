package com.example.ss.landanmakuplayer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.io.IOException;

/**
 * Created by ss on 4/17/16.
 */
public class MultiCastService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        return super.onStartCommand(intent, flags, startId);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
