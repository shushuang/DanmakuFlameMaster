package com.example.ss.landanmakuplayer;

/**
 * Created by ss on 4/16/16.
 */
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class ServerThread extends Thread{
    private static final String TAG = "ServerThread";
    private final int port;
    private ServerSocketChannel ssc;
    private Selector selector;
    private ByteBuffer buf = ByteBuffer.allocate(256);
    ServerThread(int port) throws IOException {
        this.port = port;
        this.ssc = ServerSocketChannel.open();
        this.ssc.socket().bind(new InetSocketAddress(port));
        this.ssc.configureBlocking(false);
        this.selector = Selector.open();

        this.ssc.register(selector, SelectionKey.OP_ACCEPT);
    }
    @Override
    public void run() {
        try {
            System.out.println("Server starting on port " + this.port);

            Iterator<SelectionKey> iter;
            SelectionKey key;
            while(this.ssc.isOpen()) {
                selector.select();
                iter=this.selector.selectedKeys().iterator();
                while(iter.hasNext()) {
                    key = iter.next();
                    iter.remove();
                    if(key.isAcceptable()) this.handleAccept(key);
                    if(key.isReadable()){
                        // if error happens just close the client
                        try{
                            this.handleRead(key);
                        }catch(IOException e){
                            key.channel().close();
                        }
                    }
                }
            }
        } catch(IOException e) {
            System.out.println("IOException, server of port " +this.port+ " terminating. Stack trace:");
            e.printStackTrace();
        }
    }
    private final ByteBuffer welcomeBuf = ByteBuffer.wrap("Welcome to NioChat!\n".getBytes());
    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
        String address = (new StringBuilder( sc.socket().getInetAddress().toString() )).append(":").append( sc.socket().getPort() ).toString();
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_READ, address);
        sc.write(welcomeBuf);
        welcomeBuf.rewind();
        System.out.println("accepted connection from: "+address);
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();
        buf.clear();
        int read = 0;
        while( (read = ch.read(buf)) > 0 ) {
            buf.flip();
            byte[] bytes = new byte[buf.limit()];
            buf.get(bytes);
            sb.append(new String(bytes));
            buf.clear();
        }
        String msg;
        if(read<0) {
            msg = key.attachment()+" left the chat.\n";
            ch.close();
        }
        else {
            msg = sb.toString();
        }
        Log.i(TAG, "read msg:" + msg);
        broadcast(msg, key);
    }

    private void broadcast(String msg, SelectionKey exceptKey) throws IOException {
        ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
        for(SelectionKey key : selector.keys()) {
            if(key.isValid() && key.channel() instanceof SocketChannel && key!=exceptKey) {
                SocketChannel sch=(SocketChannel) key.channel();
                while(msgBuf.hasRemaining())
                    sch.write(msgBuf);
                Log.i(TAG, "broadcast msg:" + msg);
                msgBuf.rewind();
            }
        }
    }
}
