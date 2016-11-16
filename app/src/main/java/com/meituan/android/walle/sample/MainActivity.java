package com.meituan.android.walle.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.meituan.android.walle.ChannelReader;
import com.meituan.android.walle.internal.Pair;

import java.util.Map;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.read_channel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readChannel();
            }
        });

    }
    private void readChannel() {
        TextView tv = (TextView) findViewById(R.id.tv_channel);
        long startTime = System.currentTimeMillis();
        Pair<String, Map<String, String>> channelInfo= ChannelReader.getChannelInfo(this.getApplicationContext());
        if (channelInfo != null) {
            tv.setText(channelInfo.getFirst() + "\n" +channelInfo.getSecond());
        }
        Toast.makeText(this, "ChannelReader takes " + (System.currentTimeMillis() - startTime) + " milliseconds", Toast.LENGTH_SHORT).show();
    }
}
