package com.ss.landanmakuplayer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by ss on 2/29/16.
 */
public class H264VideoServer extends NanoHTTPD {
    private String m_videoName;
    public H264VideoServer(
            String videoName
    ){
        super(8089);
        this.m_videoName = videoName;
    }

    @Override
    public Response serve(String uri, Method method, Map<String, String> headers, Map<String, String> parms, Map<String, String> files) {
        File f = new File(this.m_videoName);
        String mimeType =  "video/mp4";
        return serveFile(uri, headers, f, mimeType);
//        FileInputStream fis = null;
//        try {
//            fis = new FileInputStream(Environment.getExternalStorageDirectory()
//                    + "/Test.mp4");
//        } catch (FileNotFoundException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//        return new NanoHTTPD.Response(Response.Status.OK, "video/mp4", fis);
    }
    //Announce that the file server accepts partial content requests
    private Response createResponse(Response.Status status, String mimeType,
                                    InputStream message) {
        Response res = new Response(status, mimeType, message);
        res.addHeader("Accept-Ranges", "bytes");
        return res;
    }

    /**
     * Serves file from homeDir and its' subdirectories (only). Uses only URI,
     * ignores all headers and HTTP parameters.
     */
    private Response serveFile(String uri, Map<String, String> header,
                               File file, String mime) {
        Response res = null;
        try {
            // Calculate etag
            String etag = Integer.toHexString((file.getAbsolutePath()
                    + file.lastModified() + "" + file.length()).hashCode());

            // Support (simple) skipping:
            long startFrom = 0;
            long endAt = -1;
            String range = header.get("range");
            if (range != null) {
                if (range.startsWith("bytes=")) {
                    range = range.substring("bytes=".length());
                    int minus = range.indexOf('-');
                    try {
                        if (minus > 0) {
                            startFrom = Long.parseLong(range
                                    .substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Change return code and add Content-Range header when skipping is
            // requested
            long fileLen = file.length();
            if (range != null && startFrom >= 0) {
                if (startFrom >= fileLen) {
                    res = createResponse(Response.Status.RANGE_NOT_SATISFIABLE,
                            NanoHTTPD.MIME_PLAINTEXT, new ByteArrayInputStream("".getBytes("UTF-8")));
                    res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
                    res.addHeader("ETag", etag);
                } else {
                    if (endAt < 0) {
                        endAt = fileLen - 1;
                    }
                    long newLen = endAt - startFrom + 1;
                    if (newLen < 0) {
                        newLen = 0;
                    }

                    final long dataLen = newLen;
                    FileInputStream fis = new FileInputStream(file) {
                        @Override
                        public int available() throws IOException {
                            return (int) dataLen;
                        }
                    };
                    try {
                        fis.skip(startFrom);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    res = createResponse(Response.Status.PARTIAL_CONTENT, mime,
                            fis);
                    res.addHeader("Content-Length", "" + dataLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-"
                            + endAt + "/" + fileLen);
                    res.addHeader("ETag", etag);
                }
            } else {
                if (etag.equals(header.get("if-none-match")))
                    res = createResponse(Response.Status.NOT_MODIFIED, mime, new ByteArrayInputStream("".getBytes("UTF-8")));
                else {
                    res = createResponse(Response.Status.OK, mime,
                            new FileInputStream(file));
                    res.addHeader("Content-Length", "" + fileLen);
                    res.addHeader("ETag", etag);
                }
            }
        } catch (IOException ioe) {
            try {
                res = createResponse(Response.Status.FORBIDDEN,
                        NanoHTTPD.MIME_PLAINTEXT,new ByteArrayInputStream( "FORBIDDEN: Reading file failed.".getBytes("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return res;
    }
}
