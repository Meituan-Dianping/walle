package com.meituan.android.walle.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.meituan.android.walle.ChannelReader;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView) findViewById(R.id.tv_channel);

        long startTime = System.currentTimeMillis();
        JSONObject jsonObject = ChannelReader.getChannelInfo(this.getApplicationContext());
        if (jsonObject != null) {
            tv.setText(jsonObject.toString());
        }
        Toast.makeText(this, "ChannelReader takes " + (System.currentTimeMillis() - startTime) + " milliseconds", Toast.LENGTH_SHORT).show();
    }
}
