package com.koushikdutta.async.sample;

import java.io.File;
import java.io.UnsupportedEncodingException;

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
import com.koushikdutta.async.http.AsyncHttpPost;
import com.koushikdutta.async.http.AsyncHttpResponse;

public class MainActivity extends Activity {
    ImageView rommanager;
    ImageView tether;
    ImageView desksms;
    ImageView googlechart;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Button b = (Button)findViewById(R.id.go);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    refresh();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        });
        
        rommanager = (ImageView)findViewById(R.id.rommanager);
        tether = (ImageView)findViewById(R.id.tether);
        desksms = (ImageView)findViewById(R.id.desksms);
        googlechart = (ImageView)findViewById(R.id.googlechart);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    private void downloadFile(final ImageView iv, String url, final String filename) {
        AsyncHttpClient.download(url, filename, new AsyncHttpClient.FileCallback() {
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
    
    private void downloadFileWithPost(final ImageView iv, String url, final String filename,  byte[] data, String contentType, String contentEncoding){
        AsyncHttpPost postRequest = null;
        try {
            postRequest = new AsyncHttpPost(url, data, contentType, contentEncoding);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        AsyncHttpClient.download(postRequest, filename, new AsyncHttpClient.FileCallback() {
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
    
    private String randomFile() {
        return ((Long)Math.round(Math.random() * 1000)).toString() + ".png";
    }
    
    private void refresh() throws UnsupportedEncodingException {
        rommanager.setImageBitmap(null);
        tether.setImageBitmap(null);
        desksms.setImageBitmap(null);
        googlechart.setImageBitmap(null);
        
        downloadFile(rommanager, "https://raw.github.com/koush/AndroidAsync/master/rommanager.png", getFileStreamPath(randomFile()).getAbsolutePath());
        downloadFile(tether, "https://raw.github.com/koush/AndroidAsync/master/tether.png", getFileStreamPath(randomFile()).getAbsolutePath());
        downloadFile(desksms, "https://raw.github.com/koush/AndroidAsync/master/desksms.png", getFileStreamPath(randomFile()).getAbsolutePath());
        downloadFileWithPost(googlechart, 
                "http://chart.googleapis.com/chart",
                getFileStreamPath(randomFile()).getAbsolutePath(),
                "cht=lc&chtt=This+is+%7C+google+api+chart&chs=300x200&chxt=x&chd=t%3A40%2C20%2C50%2C20%2C100".getBytes("UTF-8"),
                "application/x-www-form-urlencoded",
                "UTF-8");
    }
}
