package com.koushikdutta.async.sample;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.BasicNameValuePair;
import com.koushikdutta.async.http.NameValuePair;
import com.koushikdutta.async.http.body.UrlEncodedFormBody;
import com.koushikdutta.async.http.cache.ResponseCacheMiddleware;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends Activity {
    static ResponseCacheMiddleware cacher;
    
    ImageView rommanager;
    ImageView tether;
    ImageView desksms;
    ImageView chart;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (cacher == null) {
            try {
                cacher = ResponseCacheMiddleware.addCache(AsyncHttpClient.getDefaultInstance(), getFileStreamPath("asynccache"), 1024 * 1024 * 10);
                cacher.setCaching(false);
            }
            catch (IOException e) {
                Toast.makeText(getApplicationContext(), "unable to create cache", Toast.LENGTH_SHORT).show();
            }
        }
        setContentView(R.layout.activity_main);
        
        Button b = (Button)findViewById(R.id.go);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        
        rommanager = (ImageView)findViewById(R.id.rommanager);
        tether = (ImageView)findViewById(R.id.tether);
        desksms = (ImageView)findViewById(R.id.desksms);
        chart = (ImageView)findViewById(R.id.chart);
        
        showCacheToast();
    }

    void showCacheToast() {
        boolean caching = cacher.getCaching();
        Toast.makeText(getApplicationContext(), "Caching: " + caching, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Toggle Caching").setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                cacher.setCaching(!cacher.getCaching());
                showCacheToast();
                return true;
            }
        });
        return true;
    }
    
    private void assignImageView(final ImageView iv, final BitmapDrawable bd) {
        iv.getHandler().post(new Runnable() {
          @Override
          public void run() {
              iv.setImageDrawable(bd);
          }
        });
    }

    private void getFile(final ImageView iv, String url, final String filename) {
        AsyncHttpClient.getDefaultInstance().executeFile(new AsyncHttpGet(url), filename, new AsyncHttpClient.FileCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse response, File result) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(filename);
                result.delete();
                if (bitmap == null)
                    return;
                BitmapDrawable bd = new BitmapDrawable(bitmap);
                assignImageView(iv, bd);
            }
        });
    }

    private void getChartFile() {
        final ImageView iv = chart;
        final String filename = getFileStreamPath(randomFile()).getAbsolutePath();
        ArrayList<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("cht", "lc"));
        pairs.add(new BasicNameValuePair("chtt", "This is a google chart"));
        pairs.add(new BasicNameValuePair("chs", "512x512"));
        pairs.add(new BasicNameValuePair("chxt", "x"));
        pairs.add(new BasicNameValuePair("chd", "t:40,20,50,20,100"));
        UrlEncodedFormBody writer = new UrlEncodedFormBody(pairs);
        try {
            AsyncHttpPost post = new AsyncHttpPost("http://chart.googleapis.com/chart");
            post.setBody(writer);
            AsyncHttpClient.getDefaultInstance().executeFile(post, filename, new AsyncHttpClient.FileCallback() {
                @Override
                public void onCompleted(Exception e, AsyncHttpResponse response, File result) {
                    if (e != null) {
                        e.printStackTrace();
                        return;
                    }
                    Bitmap bitmap = BitmapFactory.decodeFile(filename);
                    result.delete();
                    if (bitmap == null)
                        return;
                    BitmapDrawable bd = new BitmapDrawable(bitmap);
                    assignImageView(iv, bd);
                }
            });
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private String randomFile() {
        return ((Long)Math.round(Math.random() * 1000)).toString() + ".png";
    }
    
    private void refresh() {
        rommanager.setImageBitmap(null);
        tether.setImageBitmap(null);
        desksms.setImageBitmap(null);
        chart.setImageBitmap(null);
        
        getFile(rommanager, "https://raw.github.com/koush/AndroidAsync/master/rommanager.png", getFileStreamPath(randomFile()).getAbsolutePath());
        getFile(tether, "https://raw.github.com/koush/AndroidAsync/master/tether.png", getFileStreamPath(randomFile()).getAbsolutePath());
        getFile(desksms, "https://raw.github.com/koush/AndroidAsync/master/desksms.png", getFileStreamPath(randomFile()).getAbsolutePath());
        getChartFile();
        
        Log.i(LOGTAG, "cache hit: " + cacher.getCacheHitCount());
        Log.i(LOGTAG, "cache store: " + cacher.getCacheStoreCount());
        Log.i(LOGTAG, "conditional cache hit: " + cacher.getConditionalCacheHitCount());
        Log.i(LOGTAG, "network: " + cacher.getNetworkCount());
    }
    
    private static final String LOGTAG = "AsyncSample";
}
