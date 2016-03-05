package com.example.ss.landanmakuplayer;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;



public class MainActivity extends AppCompatActivity {
    public static final String VIDEO_FILE="videofile";
    public static final String SOURCE_TYPE="sourcetype";
    public static final String VIDEO_URL= "videourl";
    public static final int LOCAL_VIDEO = 0;
    public static final int REMOTE_URL = 1;
    private String state = "Online";
    private ListView listView;
    private Map<String, String> peersMap = new TreeMap<String, String>();
    private Set<String> setItems = new TreeSet<>();
    private ArrayList<String> listItems = new ArrayList<String>();
    private ArrayAdapter<String> adapter;
    private Button refreshBtn;
    private Button fileDialogBtn;
    private SenderThread sender;
    private ReceiverThread receiver;

    private static final String TAG = "MainActivity";
    private static final int MULTICAST_PORT = 5100;
    private static final String GROUP_ID = "224.5.9.7";

    WifiManager.MulticastLock multicastLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView)this.findViewById(R.id.list);
        refreshBtn = (Button)this.findViewById(R.id.refreshBtn);
        fileDialogBtn = (Button)this.findViewById(R.id.fileDialogBtn);
        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                listItems);
        listView.setAdapter(adapter);

        sender = new SenderThread();
        receiver = new ReceiverThread();
        sender.start();
        receiver.start();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String item = (String)listView.getItemAtPosition(position);
                Log.d("click item", item);
                if(item.contains("Playing"))
                {
                    String ip = item.split(":")[0];
                    String play_url = "http:/"+ip+":8089";
                    Intent i = new Intent(MainActivity.this, VideoPlayerActivity.class);
                    i.putExtra(SOURCE_TYPE, REMOTE_URL);
                    i.putExtra(VIDEO_URL, play_url);
                    startActivity(i);
                }
            }
        });
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Message msg = new Message();
                msg.what = 0;
                msg.obj = state;
                sender.senderHandler.sendMessage(msg);
            }
        });
        fileDialogBtn.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                SimpleFileDialog fileOpenDialog =  new SimpleFileDialog(
                        MainActivity.this,
                        "FileOpen..",
                        new SimpleFileDialog.SimpleFileDialogListener()
                        {
                            @Override
                            public void onChosenDir(String chosenDir)
                            {
                                if(!chosenDir.endsWith(".mp4")){
                                    Toast.makeText(MainActivity.this, "视频格式错误, 请选择视频文件",  Toast.LENGTH_SHORT).show();
                                }
                                else {
                                    Message msg = new Message();
                                    // send what is playing to other clients
                                    state = "Playing";
                                    msg.what = 1;
                                    msg.obj = "Playing";
                                    sender.senderHandler.sendMessage(msg);
                                    Intent intent = new Intent(MainActivity.this, VideoPlayerActivity.class);
                                    intent.putExtra(VIDEO_FILE, chosenDir);
                                    intent.putExtra(SOURCE_TYPE, LOCAL_VIDEO);
                                    startActivity(intent);
                                }
                                // The code in this function will be executed when the dialog OK button is pushed
//                                editFile.setText(chosenDir);
                            }
                        }
                );
                //You can change the default filename using the public variable "Default_File_Name"
                fileOpenDialog.default_file_name = Environment.getExternalStorageDirectory().toString();
                fileOpenDialog.chooseFile_or_Dir(fileOpenDialog.default_file_name);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        state = "Online";
    }

    private Handler myMainHandler = new Handler(){
        public void handleMessage(Message msg){
           //
            if(msg.what == 0){
                String msgstr = (String)msg.obj;
                addItem(msgstr);
                // now call the senderThread to send my own state
                Message message = new Message();
                message.what = 0;
                message.obj = state;
                sender.senderHandler.sendMessage(message);
            }
        }
        private void addItem(String msgstr){
            String[] values = msgstr.split(":");
            String key = values[0];
            String val = values[1];
            peersMap.put(key, val);
            listItems.clear();
            for(Map.Entry<String, String> entry:peersMap.entrySet())
            {
                listItems.add(entry.getKey() + ":" + entry.getValue());
            }

            adapter.notifyDataSetChanged();
        }
    };

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
                send(state);
            }catch(IOException e){
                e.printStackTrace();
            }
            Looper.loop();
        }
    }
    private class ReceiverThread extends Thread{
        public Handler receiverHandler;
        public void run() {
            Looper.prepare();
            receiverHandler = new Handler() {
                public void handleMessage(Message msg) {
                    if (msg.what == 0) {

                    }
                }
            };
            try {
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    multicastLock = wifiManager.createMulticastLock("multicast.test");
                    multicastLock.acquire();
                }
                String content = null;

                MulticastSocket multicastSocket = null;
                multicastSocket = new MulticastSocket(MULTICAST_PORT);
                try {
                    multicastSocket.setLoopbackMode(true);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
                InetAddress group = null;
                try {
                    group = InetAddress.getByName(GROUP_ID);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                multicastSocket.joinGroup(group);
                byte[] receiveData = new byte[1024];
                DatagramPacket recv = new DatagramPacket(receiveData, receiveData.length);
                while (true) {
                    multicastSocket.receive(recv);
                    Log.d(TAG, "receive from" + recv.getAddress());
                    recv.setLength(receiveData.length);
//                    multicastSocket.leaveGroup(group);
                    StringBuilder packetContent = new StringBuilder();
                    for (int i = 0; i < receiveData.length; i++) {
                        if (receiveData[i] == 0)
                            break;
                        packetContent.append((char) receiveData[i]);
                    }
                    content = packetContent.toString();
                    Arrays.fill(receiveData, (byte) 0);
                    Log.d(TAG, "packet content is:" + content);
                    // call main Thread to render
                    Message msg = new Message();
                    msg.what = 0;
                    String str = recv.getAddress() + ":" + content;
                    msg.obj = str;
                    myMainHandler.sendMessage(msg);
                }
//                multicastLock.release();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Looper.loop();
        }
    }
}
