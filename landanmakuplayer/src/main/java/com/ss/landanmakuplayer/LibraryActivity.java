package com.ss.landanmakuplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by ss on 4/17/16.
 */
public class LibraryActivity extends AppCompatActivity  implements AdapterView.OnItemClickListener{
    private static final int MENU_HTTPPLAY = 0;
    private static final int MENU_LIVE555_MULTICAST = 1;
    private static final int MENU_DELETE = 2;
    private Menu libMenu;
    List<VideoInfo> mp4Rows;
    LinkedHashSet<VideoInfo> mp4Set;
    BaseAdapter mAdapter;
    private String fname = "cache.txt";
    private static final int MULTICAST_PORT = 5100;
    private static final String GROUP_ID = "224.5.9.7";
    private SenderThread sender;
    WifiManager.MulticastLock multicastLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        ListView listView = (ListView)findViewById(R.id.ListView);
        mp4Rows = new ArrayList<VideoInfo>();
        mp4Set = new LinkedHashSet<VideoInfo>();
        LoadFromCache();
        mAdapter = new Mp4GalleryAdapter(this, mp4Rows);
        listView.setAdapter(mAdapter);
        registerForContextMenu(listView);

        sender = new SenderThread();
        sender.start();
        listView.setOnItemClickListener(this);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId()==R.id.ListView) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle(mp4Rows.get(info.position).name);
            String[] menuItems = getResources().getStringArray(R.array.array_menu);
            for (int i = 0; i<menuItems.length; i++) {
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int menuItemIndex = item.getItemId();
        //String[] menuItems = getResources().getStringArray(R.array.array_menu);
        //String menuItemName = menuItems[menuItemIndex];
        String path = mp4Rows.get(info.position).path;
        if(menuItemIndex == MENU_HTTPPLAY){
            // Http 单播视频
            // 发送正在组播的信息
            MainActivity.state = "Playing Http";
            Message message = new Message();
            message.what = 0;
            message.obj = MainActivity.state;
            sender.senderHandler.sendMessage(message);
            // start activity
            Intent intent = new Intent(LibraryActivity.this, VideoPlayerActivity.class);
            intent.putExtra(AppConstant.VIDEO_FILE, path);
            intent.putExtra(AppConstant.SOURCE_TYPE, AppConstant.LOCAL_VIDEO);
            startActivity(intent);
        }
        else if(menuItemIndex == MENU_LIVE555_MULTICAST){
            // live555 组播视频
            MainActivity.state = "Playing Live555";
            Message message = new Message();
            message.what = 0;
            message.obj = MainActivity.state;
            sender.senderHandler.sendMessage(message);
            // start activity
            Intent intent = new Intent(LibraryActivity.this, VideoPlayerActivity.class);
            intent.putExtra(AppConstant.VIDEO_FILE, path);
            intent.putExtra(AppConstant.SOURCE_TYPE, AppConstant.LOCAL_VIDEO);
            intent.putExtra("LIVE555", 1);
            startActivity(intent);
        }
        else{
            // 删除视频
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.lib_menu, menu);
        libMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.action_refresh:
                new LongOperation().execute("");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void startRefreshAnimation(){
        MenuItem m = libMenu.findItem(R.id.action_refresh);
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView iv = (ImageView)inflater.inflate(R.layout.iv_refresh, null);
        Animation rotation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
        rotation.setRepeatCount(Animation.INFINITE);
        iv.startAnimation(rotation);
        m.setActionView(iv);
    }

    public void stopRefreshAnimation()
    {
        // Get our refresh item from the menu
        MenuItem m = libMenu.findItem(R.id.action_refresh);
        if(m.getActionView()!=null)
        {
            // Remove the animation.
            m.getActionView().clearAnimation();
            m.setActionView(null);
        }
    }

    public void LoadFromCache(){
        try {
            FileInputStream fis = this.openFileInput(fname);
            ObjectInputStream is = new ObjectInputStream(fis);
            mp4Rows = (List<VideoInfo>)is.readObject();
            is.close();
            fis.close();
        }catch(Exception e){
            e.printStackTrace();;
        }
    }

    public void SaveToCache(){
        try {
            FileOutputStream fos = this.openFileOutput(fname, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(mp4Rows);
            os.close();
            fos.close();
        }catch(Exception e){
            e.printStackTrace();;
        }

    }
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        VideoInfo vi = mp4Rows.get(position);
        // 发送正在组播的信息
        MainActivity.state = "Playing";
        Message message = new Message();
        message.what = 0;
        message.obj = MainActivity.state;
        sender.senderHandler.sendMessage(message);
        // start activity
        Intent intent = new Intent(LibraryActivity.this, VideoPlayerActivity.class);
        intent.putExtra(AppConstant.VIDEO_FILE, vi.path);
        intent.putExtra(AppConstant.SOURCE_TYPE, AppConstant.LOCAL_VIDEO);
        startActivity(intent);
    }

    private class LongOperation extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            startRefreshAnimation();
        }

        @Override
        protected String doInBackground(String... params) {
            walkin(new File("/storage"));
            mp4Rows.clear();
            mp4Rows.addAll(mp4Set);
            System.out.println(mp4Rows);
            SaveToCache();
            return "execute";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            stopRefreshAnimation();
            mAdapter.notifyDataSetChanged();
        }
    }

    public void walkin(File dir) {
        String pattern = ".mp4";
        String otherpattern = ".mkv";
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (int i=0; i<listFile.length; i++) {
                if (listFile[i].isDirectory()) {
                    walkin(listFile[i]);
                } else {
                    if (listFile[i].getName().endsWith(pattern)
                            || listFile[i].getName().endsWith(otherpattern)) {
                        System.out.println(listFile[i].getPath());
                        VideoInfo newvi = new VideoInfo();
                        newvi.name = listFile[i].getName();
                        newvi.path = listFile[i].getPath();
                        mp4Set.add(newvi);
                    }
                }
            }
        }
    }

    class Mp4GalleryAdapter extends BaseAdapter{
        Context context;
        List<VideoInfo> mp4Rows;
        LayoutInflater inflater;
        public Mp4GalleryAdapter(Context context, List<VideoInfo> mp4Rows)
        {
            this.context = context;
            this.mp4Rows = mp4Rows;
            inflater = (LayoutInflater)context.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE
            );
        }

        @Override
        public int getCount() {
            return mp4Rows.size();
        }

        @Override
        public Object getItem(int position) {
            return mp4Rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View videoRow = inflater.inflate(R.layout.list_item, null);
            ImageView videoThumb = (ImageView) videoRow.findViewById(R.id.ImageView);
            TextView videoTitle = (TextView) videoRow.findViewById(R.id.TextView);
//            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(mp4Rows.get(position).path,
//                   MediaStore.Images.Thumbnails.MINI_KIND);
//            videoThumb.setImageBitmap(thumb);
            videoTitle.setText(mp4Rows.get(position).name);
            return videoRow;
        }
    }

    private class SenderThread extends Thread{
        public Handler senderHandler;
        private MulticastSocket multicastSocket;
        private String ip;
        private InetAddress group;
        private void send(String content) {
            // send my own ip
            byte[] sendData = content.getBytes();
            DatagramPacket packet =
                    new DatagramPacket(sendData, sendData.length, group, MULTICAST_PORT);
            try {
                multicastSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run(){
            Looper.prepare();
            senderHandler = new Handler(){
                public void handleMessage(Message msg) {
                    if(msg.what == 0){
                        if(multicastSocket!=null){
                            send((String)msg.obj);
                        }
                    }
                    // one client is playing video
                    if(msg.what == 1){
                        if(multicastSocket!=null)
                            send((String)msg.obj);
                    }
                }
            };
            try{
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if(wifiManager != null) {
                    multicastLock = wifiManager.createMulticastLock("multicast.test");
                    multicastLock.acquire();
                }
                multicastSocket = new MulticastSocket(MULTICAST_PORT);
                try {
                    multicastSocket.setLoopbackMode(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                try {
                    group = InetAddress.getByName(GROUP_ID);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                multicastSocket.joinGroup(group);
                ip = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                send(MainActivity.state);
            }catch(IOException e){
                e.printStackTrace();
            }
            Looper.loop();
        }
    }
}
