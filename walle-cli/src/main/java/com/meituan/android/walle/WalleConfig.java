package com.meituan.android.walle;

import java.util.List;
import java.util.Map;

public class WalleConfig {

    private Map<String, String> defaultExtraInfo;

    public List<ChannelInfo> getChannelInfoList() {
        return channelInfoList;
    }

    public void setChannelInfoList(final List<ChannelInfo> channelInfosList) {
        this.channelInfoList = channelInfosList;
    }

    private List<ChannelInfo> channelInfoList;

    public Map<String, String> getDefaultExtraInfo() {
        return defaultExtraInfo;
    }

    public void setDefaultExtraInfo(final Map<String, String> defaultExtraInfo) {
        this.defaultExtraInfo = defaultExtraInfo;
    }

    public static class ChannelInfo {
        private String channel;
        private String alias;

        public String getAlias() {
            return alias;
        }

        public void setAlias(final String alias) {
            this.alias = alias;
        }

        private Map<String, String> extraInfo;

        public String getChannel() {
            return channel;
        }

        public void setChannel(final String channel) {
            this.channel = channel;
        }

        public Map<String, String> getExtraInfo() {
            return extraInfo;
        }

        public void setExtraInfo(final Map<String, String> extraInfo) {
            this.extraInfo = extraInfo;
        }
    }
}
