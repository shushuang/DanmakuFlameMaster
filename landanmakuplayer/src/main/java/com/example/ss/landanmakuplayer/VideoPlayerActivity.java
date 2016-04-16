package com.example.ss.landanmakuplayer;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.content.pm.ActivityInfo;

import android.os.Bundle;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.ToggleButton;
import android.widget.VideoView;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableStringBuilder;
import android.util.Log;

import java.io.InputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;

import android.os.Handler;

import org.json.JSONException;
import org.json.JSONObject;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.BaseCacheStuffer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser;
import master.flame.danmaku.danmaku.util.IOUtils;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;


public class VideoPlayerActivity extends AppCompatActivity implements View.OnClickListener {
    private IDanmakuView mDanmakuView;
    private DanmakuContext mContext;
    private BaseDanmakuParser mParser;
    private String mVideoName;
    private String mVidAddress;
    private String mServerIp;
    private Boolean mIamServer;

    private View mMediaController;
    private VideoView mVideoView;
    WifiManager.MulticastLock multicastLock;
    private static final String TAG = "VideoPlayerActivity";
    private Switch mSwitch_hs;
    private ImageButton mBtnPauseOrResume;
    private ImageButton mBtnSendDanmaku;
    private RadioGroup mGroupDanmakuType;
    private RadioGroup mGroupDanmakuColor;
    private EditText mInputText;
    private ClientThread clientThread;

    private Handler mainHandler = new MainHandler(this);

    static class MainHandler extends Handler {
        private final WeakReference<VideoPlayerActivity> mActivity;

        MainHandler(VideoPlayerActivity activity) {
            mActivity = new WeakReference<VideoPlayerActivity>(activity);
        }
        @Override
        public void handleMessage(Message msg)
        {
            VideoPlayerActivity activity = mActivity.get();
            if (activity != null) {
                activity.handleMessage(msg);
            }
        }
    }

    public void handleMessage(Message msg) {
        if (msg.what == 0) {
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject((String)msg.obj);
                Log.d("mainThread", jsonObject.getString("content"));
                addNewDanmaku(jsonObject.getString("content"),
                        jsonObject.getInt("color"),
                        jsonObject.getInt("danmaku_type"),
                        false);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    static final RadioGroup.OnCheckedChangeListener ToggleListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final RadioGroup radioGroup, final int i) {
            for (int j = 0; j < radioGroup.getChildCount(); j++) {
                final ToggleButton view = (ToggleButton) radioGroup.getChildAt(j);
                view.setChecked(view.getId() == i);
            }
        }
    };

    public void onToggle(View view){
        ((RadioGroup)view.getParent()).check(view.getId());
    }

    private BaseCacheStuffer.Proxy mCacheStufferAdapter = new BaseCacheStuffer.Proxy() {
        private Drawable mDrawable;

        @Override
        public void prepareDrawing(final BaseDanmaku danmaku, boolean fromWorkerThread) {
            if (danmaku.text instanceof Spanned) {
                // 根据你的条件检查是否需要需要更新弹幕
                // FIXME 这里只是简单启个线程来加载远程url图片，请使用你自己的异步线程池，最好加上你的缓存池
                new Thread() {
                    @Override
                    public void run() {
                        String url = "http://www.bilibili.com/favicon.ico";
                        InputStream inputStream = null;
                        Drawable drawable = mDrawable;
                        if (drawable == null) {
                            try {
                                URLConnection urlConnection = new URL(url).openConnection();
                                inputStream = urlConnection.getInputStream();
                                drawable = BitmapDrawable.createFromStream(inputStream, "bitmap");
                                mDrawable = drawable;
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                IOUtils.closeQuietly(inputStream);
                            }
                        }
                        if (drawable != null) {
                            drawable.setBounds(0, 0, 100, 100);
                            SpannableStringBuilder spannable = createSpannable(drawable);
                            danmaku.text = spannable;
                            if (mDanmakuView != null) {
                                mDanmakuView.invalidateDanmaku(danmaku, false);
                            }
                            return;
                        }
                    }
                }.start();
            }
        }

        @Override
        public void releaseResource(BaseDanmaku danmaku) {
            // TODO 重要:清理含有ImageSpan的text中的一些占用内存的资源 例如drawable
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vp);
        Context context = getApplicationContext();
        mVidAddress = "http://127.0.0.1:8089";
        Intent inputIntent = getIntent();
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if(wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("multicast.test");
            multicastLock.acquire();
        }
        if (inputIntent.getIntExtra(MainActivity.SOURCE_TYPE, MainActivity.LOCAL_VIDEO)
                == MainActivity.LOCAL_VIDEO) {
            Intent i = new Intent(context, VidServerService.class);
            // potentially add data to the intent
            mVideoName = inputIntent.getStringExtra(MainActivity.VIDEO_FILE);
            i.putExtra(MainActivity.VIDEO_FILE, mVideoName);
            //i.putExtra("KEY1", "Value to be used by the service");
            context.startService(i);
            Log.w("Httpd", "Web server initialized.");
            mIamServer = true;
            try {
                new ServerThread(5454).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            mVidAddress = inputIntent.getStringExtra(MainActivity.VIDEO_URL);
            mIamServer = false;
        }
        mServerIp = mVidAddress.split(":")[1].substring(2);
//        senderThread = new SenderThread();
//        receiverThread = new ReceiverThread();
//        senderThread.start();
//        receiverThread.start();
        clientThread = new ClientThread(mainHandler, mServerIp);
        clientThread.start();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        findViews();
    }

    private void findViews() {
        mMediaController = findViewById(R.id.media_controller);
        mVideoView = (VideoView) findViewById(R.id.videoview);
        mGroupDanmakuType = (RadioGroup) findViewById(R.id.group_danmaku_type);
        mGroupDanmakuColor = (RadioGroup) findViewById(R.id.group_danmaku_color);
        mGroupDanmakuType.setOnCheckedChangeListener(ToggleListener);
        mGroupDanmakuColor.setOnCheckedChangeListener(ToggleListener);

        mSwitch_hs = (Switch) findViewById(R.id.switch_hs);
        mBtnPauseOrResume = (ImageButton) findViewById(R.id.btn_pr);
        mBtnSendDanmaku = (ImageButton) findViewById(R.id.btn_send);

        mInputText = (EditText) findViewById(R.id.danmuku_input);

        mMediaController.setOnClickListener(this);
        mSwitch_hs.setOnClickListener(this);
        mBtnPauseOrResume.setOnClickListener(this);
        mBtnSendDanmaku.setOnClickListener(this);

        mDanmakuView = (IDanmakuView) findViewById(R.id.sv_danmaku);
        mContext = DanmakuContext.create();
        // 设置最大显示行数
        HashMap<Integer, Integer> maxLinesPair = new HashMap<Integer, Integer>();
        maxLinesPair.put(BaseDanmaku.TYPE_SCROLL_RL, 5); // 滚动弹幕最大显示5行
        // 设置是否禁止重叠
        HashMap<Integer, Boolean> overlappingEnablePair = new HashMap<Integer, Boolean>();
        overlappingEnablePair.put(BaseDanmaku.TYPE_SCROLL_RL, true);
        overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_TOP, true);

        mContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
                .setDuplicateMergingEnabled(false)
                .setScrollSpeedFactor(1.2f)
                .setScaleTextSize(1.2f)
                .setCacheStuffer(new SpannedCacheStuffer(), mCacheStufferAdapter)
                .setMaximumLines(maxLinesPair)
                .preventOverlapping(overlappingEnablePair);

        if (mDanmakuView != null) {
            mParser = createParser(null);
            mDanmakuView.setCallback(new DrawHandler.Callback() {
                @Override
                public void updateTimer(DanmakuTimer timer) {

                }

                @Override
                public void drawingFinished() {

                }

                @Override
                public void danmakuShown(BaseDanmaku danmaku) {
//                    Log.d("DFM", "danmakuShown(): text=" + danmaku.text);
                }

                @Override
                public void prepared() {
                    mDanmakuView.start();
                }
            });
            mDanmakuView.setOnDanmakuClickListener(new IDanmakuView.OnDanmakuClickListener() {
                @Override
                public void onDanmakuClick(BaseDanmaku latest) {
                    Log.d("DFM", "onDanmakuClick text:" + latest.text);
                }

                @Override
                public void onDanmakuClick(IDanmakus danmakus) {
                    Log.d("DFM", "onDanmakuClick danmakus size:" + danmakus.size());
                }
            });
            mDanmakuView.prepare(mParser, mContext);
            mDanmakuView.showFPS(true);
            mDanmakuView.enableDanmakuDrawingCache(true);
            // 点击显示控制栏
            ((View) mDanmakuView).setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    mMediaController.setVisibility(View.VISIBLE);
                }
            });
        }
        if (mVideoView != null) {
            mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            if(mIamServer){
                mVideoView.setVideoPath(mVideoName);
            }
            else{
                Uri vidUri = Uri.parse(mVidAddress);
                mVideoView.setVideoURI(vidUri);
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mDanmakuView != null && mDanmakuView.isPrepared()) {
            mDanmakuView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDanmakuView != null && mDanmakuView.isPrepared() && mDanmakuView.isPaused()) {
            mDanmakuView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDanmakuView != null) {
            // dont forget release!
            mDanmakuView.release();
            mDanmakuView = null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mDanmakuView != null) {
            // dont forget release!
            mDanmakuView.release();
            mDanmakuView = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void _hideSoftKeyBoard(){
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mInputText.getWindowToken(), 0);
    }
    @Override
    public void onClick(View v) {
        if (v == mMediaController) {
            mMediaController.setVisibility(View.GONE);
            _hideSoftKeyBoard();
        }
        if (mDanmakuView == null || !mDanmakuView.isPrepared())
            return;
        else if (v == mSwitch_hs) {
            if (mDanmakuView.isShown())
                mDanmakuView.hide();
                // mPausedPosition = mDanmakuView.hideAndPauseDrawTask();
            else
                mDanmakuView.show();
            // mDanmakuView.showAndResumeDrawTask(mPausedPosition); // sync to the video time in your practice
        } else if (v == mBtnPauseOrResume) {
            if (mDanmakuView.isPaused()) {
                mDanmakuView.resume();
                mBtnPauseOrResume.setBackgroundResource(
                        android.R.drawable.ic_media_pause);
            } else {
                mDanmakuView.pause();
                mBtnPauseOrResume.setBackgroundResource(
                        android.R.drawable.ic_media_play
                );
            }

        } else if (v == mBtnSendDanmaku) {
            int  danmakuType = BaseDanmaku.TYPE_FIX_TOP;
            if(mGroupDanmakuType.getCheckedRadioButtonId() == R.id.btn_FB){
                danmakuType = BaseDanmaku.TYPE_FIX_BOTTOM;
            }else if(mGroupDanmakuType.getCheckedRadioButtonId() == R.id.btn_FT){
                danmakuType = BaseDanmaku.TYPE_FIX_TOP;
            }else if(mGroupDanmakuType.getCheckedRadioButtonId() == R.id.btn_RToL){
                danmakuType = BaseDanmaku.TYPE_SCROLL_RL;
            }else if(mGroupDanmakuType.getCheckedRadioButtonId() == R.id.btn_LToR){
                danmakuType = BaseDanmaku.TYPE_SCROLL_LR;
            }
            int textColor = Color.RED;
            if(mGroupDanmakuColor.getCheckedRadioButtonId() == R.id.btn_red)
                textColor = Color.RED;
            else if(mGroupDanmakuColor.getCheckedRadioButtonId() == R.id.btn_green)
                textColor = Color.GREEN;
            else if(mGroupDanmakuColor.getCheckedRadioButtonId() == R.id.btn_yellow)
                textColor = Color.YELLOW;
            String content = mInputText.getText().toString();
            BaseDanmaku danmaku = addNewDanmaku(content,textColor,danmakuType,true);
            // 视频继续播放
            if (!mVideoView.isPlaying()) {
                mVideoView.start();
            }
            mInputText.setText("");
            mMediaController.setVisibility(View.GONE);
            this._hideSoftKeyBoard();
            Message msg = new Message();
            JSONObject object = new JSONObject();
            try{
                object.put("content", danmaku.text);
                object.put("color", danmaku.textColor);
                object.put("danmaku_type",danmakuType);
            }catch(JSONException e){
                e.printStackTrace();
            }
            Log.d(TAG, object.toString());
            msg.what = 0;
            msg.obj = object.toString();
            clientThread.senderHandler.sendMessage(msg);
        }
    }

    private SpannableStringBuilder createSpannable(Drawable drawable) {
        String text = "bitmap";
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        ImageSpan span = new ImageSpan(drawable);
        spannableStringBuilder.setSpan(span, 0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        spannableStringBuilder.append("图文混排");
        spannableStringBuilder.setSpan(new BackgroundColorSpan(Color.parseColor("#8A2233B1")), 0, spannableStringBuilder.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return spannableStringBuilder;
    }

    private BaseDanmakuParser createParser(InputStream stream) {
        if (stream == null) {
            return new BaseDanmakuParser() {
                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }

        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);

        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }


    public void sendMessageToMainThread(String content) {
        Message msg = new Message();
        msg.what = 0;
        msg.obj = content;
        mainHandler.sendMessage(msg);
    }

    public  BaseDanmaku addNewDanmaku(String content, int color, int danmakuType, boolean border){
        BaseDanmaku danmaku = mContext.mDanmakuFactory.createDanmaku(danmakuType);
        if (danmaku == null || mDanmakuView == null) {
            return null;
        }
        danmaku.text = content;
        danmaku.padding = 5;
        danmaku.priority = 1;
        danmaku.isLive = true;
        danmaku.time = mDanmakuView.getCurrentTime() + 1200;
        danmaku.textSize = 25f * (mParser.getDisplayer().getDensity() - 0.6f);
        danmaku.textColor = color;
        danmaku.textShadowColor = Color.WHITE;
        if(border)
            danmaku.borderColor = Color.GREEN;
        mDanmakuView.addDanmaku(danmaku);
        return danmaku;
    }
}
