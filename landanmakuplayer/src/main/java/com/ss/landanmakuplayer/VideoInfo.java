package com.ss.landanmakuplayer;

import java.io.Serializable;

/**
 * Created by ss on 4/17/16.
 */
public class VideoInfo implements Serializable {
    public String name;
    public String path;

    @Override
    public boolean equals(Object o) {
        if(o instanceof VideoInfo) {
            VideoInfo temp = (VideoInfo) o;
            return temp.name.equals(this.name);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
