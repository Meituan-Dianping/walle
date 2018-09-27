package com.meituan.android.walle;

import java.util.List;
import java.util.Map;

public class WalleConfig {

    /**
     * strategy:
     * 1. ifNone (默认适用此策略) : 仅当对应channel没有extraInfo时生效
     * 2. always : 所有channel都生效，channel中extraInfo的key与defaultExtraInfo重复时，覆盖defaultExtraInfo中的内容。
     */
    public static final String STRATEGY_IF_NONE = "ifNone";
    public static final String STRATEGY_ALWAYS = "always";
    private String defaultExtraInfoStrategy = STRATEGY_IF_NONE;

    public String getDefaultExtraInfoStrategy() {
        return defaultExtraInfoStrategy;
    }

    public void setDefaultExtraInfoStrategy(String defaultExtraInfoStrategy) {
        this.defaultExtraInfoStrategy = defaultExtraInfoStrategy;
    }

    private Map<String, String> defaultExtraInfo;

    public List<ChannelInfo> getChannelInfoList() {
        return channelInfoList;
    }

    public void setChannelInfoList(List<ChannelInfo> channelInfoList) {
        this.channelInfoList = channelInfoList;
    }

    private List<ChannelInfo> channelInfoList;

    public Map<String, String> getDefaultExtraInfo() {
        return defaultExtraInfo;
    }

    public void setDefaultExtraInfo(Map<String, String> defaultExtraInfo) {
        this.defaultExtraInfo = defaultExtraInfo;
    }

    public static class ChannelInfo {
        private String channel;
        private String alias;

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        /**
         * 强制声明不使用defaultExtraInfo参数
         */
        private boolean excludeDefaultExtraInfo = false;

        public boolean isExcludeDefaultExtraInfo() {
            return excludeDefaultExtraInfo;
        }

        private Map<String, String> extraInfo;

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public Map<String, String> getExtraInfo() {
            return extraInfo;
        }

        public void setExtraInfo(Map<String, String> extraInfo) {
            this.extraInfo = extraInfo;
        }
    }
}
