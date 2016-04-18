package com.example.ss.landanmakuplayer;

import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.format.Formatter;
import android.widget.TabHost;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by ss on 4/17/16.
 */
public class MainTabActivity extends TabActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tab_host);
        TabHost tabHost = getTabHost();
        TabHost.TabSpec tab1 = tabHost.newTabSpec("First Tab");
        TabHost.TabSpec tab2 = tabHost.newTabSpec("Second Tab");
        tab1.setIndicator("Tab1");
        tab1.setContent(new Intent(this, MainActivity.class));
        tab2.setIndicator("Tab2");
        tab2.setContent(new Intent(this, LibraryActivity.class));
        tabHost.addTab(tab1);
        tabHost.addTab(tab2);
    }



}
