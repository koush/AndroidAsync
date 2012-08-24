package com.koushikdutta.test;

import java.io.File;

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
import com.koushikdutta.async.test.R;

public class MainActivity extends Activity {
    ImageView rommanager;
    ImageView tether;
    ImageView desksms;
    
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    private void downloadFile(final ImageView iv, String url, final String filename) {
        AsyncHttpClient.download(url, filename, new AsyncHttpClient.FileCallback() {
            @Override
            public void onCompleted(Exception e, File result) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }
                System.out.println(result.getAbsolutePath());
                Bitmap bitmap = BitmapFactory.decodeFile(filename);
                if (bitmap == null)
                    return;
                BitmapDrawable bd = new BitmapDrawable(bitmap);
                iv.setImageDrawable(bd);
            }
        });
    }
    
    private void refresh() {
        rommanager.setImageBitmap(null);
        tether.setImageBitmap(null);
        desksms.setImageBitmap(null);
        
        downloadFile(rommanager, "https://raw.github.com/koush/AndroidAsync/master/rommanager.png", getFileStreamPath("rommanager.png").getAbsolutePath());
        downloadFile(tether, "https://raw.github.com/koush/AndroidAsync/master/tether.png", getFileStreamPath("tether.png").getAbsolutePath());
        downloadFile(desksms, "https://raw.github.com/koush/AndroidAsync/master/desksms.png", getFileStreamPath("desksms.png").getAbsolutePath());
    }
}
