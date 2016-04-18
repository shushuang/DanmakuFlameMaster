package com.example.ss.landanmakuplayer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ss on 4/17/16.
 */
public class LibraryActivity extends AppCompatActivity  implements AdapterView.OnItemClickListener{
    List<VideoInfo> mp4Rows;
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
        Button refreshBtn = (Button)findViewById(R.id.btn_refreshCache);
        mp4Rows = new ArrayList<VideoInfo>();
        File f = new File(fname);
        LoadFromCache();
        mAdapter = new Mp4GalleryAdapter(this, mp4Rows);
        listView.setAdapter(mAdapter);;
        sender = new SenderThread();
        sender.start();
        listView.setOnItemClickListener(this);

        refreshBtn.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View v) {
                                              new LongOperation().execute("");
                                          }
                                      }
        );

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
        intent.putExtra(MainActivity.VIDEO_FILE, vi.path);
        intent.putExtra(MainActivity.SOURCE_TYPE, MainActivity.LOCAL_VIDEO);
        startActivity(intent);
    }

    private class LongOperation extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            walkin(new File("/storage"));
            System.out.println(mp4Rows);
            SaveToCache();
            return "execute";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            mAdapter.notifyDataSetChanged();
        }
    }

    public void walkin(File dir) {
        String pattern = ".mp4";
        File listFile[] = dir.listFiles();
        if (listFile != null) {
            for (int i=0; i<listFile.length; i++) {
                if (listFile[i].isDirectory()) {
                    walkin(listFile[i]);
                } else {
                    if (listFile[i].getName().endsWith(pattern)) {
                        System.out.println(listFile[i].getPath());
                        VideoInfo newvi = new VideoInfo();
                        newvi.name = listFile[i].getName();
                        newvi.path = listFile[i].getPath();
                        mp4Rows.add(newvi);
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
