package com.example.ss.landanmakuplayer;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

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
        tab1.setIndicator("",getResources().getDrawable(R.drawable.ic_group_black_18dp));
        tab1.setContent(new Intent(this, MainActivity.class));
        tab2.setIndicator("", getResources().getDrawable(R.drawable.ic_movie_black_18dp));
        tab2.setContent(new Intent(this, LibraryActivity.class));
        tabHost.addTab(tab1);
        tabHost.addTab(tab2);
    }
}
