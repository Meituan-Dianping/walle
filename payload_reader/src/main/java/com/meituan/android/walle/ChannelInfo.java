package com.meituan.android.walle;

import java.util.Map;

/**
 * Created by chentong on 17/11/2016.
 */

public class ChannelInfo {
    private String channel;
    private Map<String, String> extraInfo;

    public ChannelInfo(String channel, Map<String, String> extraInfo) {
        this.channel = channel;
        this.extraInfo = extraInfo;
    }

    public String getChannel() {
        return channel;
    }

    public Map<String, String> getExtraInfo() {
        return extraInfo;
    }
}
