package com.ss.landanmakuplayer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


/**
 * Created by ss on 4/16/16.
 */

public class ClientThread extends Thread{
    private static final String TAG = "ClientThread";
    private SocketChannel sc;
    private String serverIP;
    public Handler senderHandler;
    private Handler mainHandler;

    public ClientThread(Handler mainHandler, String serverIP){
        this.mainHandler = mainHandler;
        this.serverIP = serverIP;
    }

    public void run(){
        Looper.prepare();
        senderHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == 0) {
                    String content = (String) msg.obj;
                    ByteBuffer buf = ByteBuffer.wrap(content.getBytes());
                    try {
                        sc.write(buf);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        };
        try {
            sc  = SocketChannel.open();
            sc.connect(new InetSocketAddress(serverIP, 5454));
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Connect success");
        new Thread(new Receiver()).start();
        Looper.loop();
    }

    class Receiver implements Runnable{
        @Override
        public void run() {
            // TODO Auto-generated method stub
            ByteBuffer readbuf = ByteBuffer.allocate(1024);
            readbuf.clear();
            while(true){
                try {
                    StringBuilder sb = new StringBuilder();
                    sc.read(readbuf);
                    String content = new String(readbuf.array(), "UTF-8");
                    Log.i(TAG, "receive msg:" + content);
                    Message msg = new Message();
                    msg.what = 0;
                    msg.obj = content;
                    mainHandler.sendMessage(msg);
                    readbuf.clear();
                } catch (IOException e) {
                    Log.e(TAG, "sc read error!");
                    e.printStackTrace();
                }
            }
        }
    }
}
