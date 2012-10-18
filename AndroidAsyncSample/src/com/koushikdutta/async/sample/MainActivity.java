package com.koushikdutta.async.sample;

import java.io.File;
import java.util.ArrayList;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpClient.StringCallback;
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.UrlEncodedFormWriter;

public class MainActivity extends Activity {
    ImageView rommanager;
    ImageView tether;
    ImageView desksms;
    ImageView chart;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    private void getFile(final ImageView iv, String url, final String filename) {
        AsyncHttpClient.get(url, filename, new AsyncHttpClient.FileCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse response, File result) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }
                System.out.println(result.getAbsolutePath());
                Bitmap bitmap = BitmapFactory.decodeFile(filename);
                result.delete();
                if (bitmap == null)
                    return;
                BitmapDrawable bd = new BitmapDrawable(bitmap);
                iv.setImageDrawable(bd);
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
        UrlEncodedFormWriter writer = new UrlEncodedFormWriter(pairs);
        try {
            AsyncHttpPost post = new AsyncHttpPost("http://chart.googleapis.com/chart");
            post.setContentWriter(writer);
            AsyncHttpClient.execute(post, filename, new AsyncHttpClient.FileCallback() {
                @Override
                public void onCompleted(Exception e, AsyncHttpResponse response, File result) {
                    if (e != null) {
                        e.printStackTrace();
                        return;
                    }
                    System.out.println(result.getAbsolutePath());
                    Bitmap bitmap = BitmapFactory.decodeFile(filename);
                    result.delete();
                    if (bitmap == null)
                        return;
                    BitmapDrawable bd = new BitmapDrawable(bitmap);
                    iv.setImageDrawable(bd);
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
        ArrayList<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("foo", "bar"));
        UrlEncodedFormWriter writer = new UrlEncodedFormWriter(pairs);
        try {
            AsyncHttpPost post = new AsyncHttpPost("http://192.168.1.2:3000");
            post.setContentWriter(writer);
            AsyncHttpClient.execute(post, new StringCallback() {
                @Override
                public void onCompleted(Exception arg0, AsyncHttpResponse response, String result) {
                    System.out.println(arg0);
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        
        rommanager.setImageBitmap(null);
        tether.setImageBitmap(null);
        desksms.setImageBitmap(null);
        chart.setImageBitmap(null);
        
        getFile(rommanager, "https://raw.github.com/koush/AndroidAsync/master/rommanager.png", getFileStreamPath(randomFile()).getAbsolutePath());
        getFile(tether, "https://raw.github.com/koush/AndroidAsync/master/tether.png", getFileStreamPath(randomFile()).getAbsolutePath());
        getFile(desksms, "https://raw.github.com/koush/AndroidAsync/master/desksms.png", getFileStreamPath(randomFile()).getAbsolutePath());
        getChartFile();
    }
}
