package com.ss.landanmakuplayer;

/**
 * Created by ss on 7/6/16.
 */
public class Live555VideoServer {
    private String m_videoName;
    public native String  startServerMain(String fileName);

    static{
        System.loadLibrary("stream");
    }

    public Live555VideoServer(
            String videoName
    ){
        this.m_videoName = videoName;
    }

    public void start(){
        startServerMain(m_videoName);
    }
}
