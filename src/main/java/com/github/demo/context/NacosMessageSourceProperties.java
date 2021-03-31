package com.github.demo.context;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class NacosMessageSourceProperties {

    private String namespace = "public";

    private String basename = "messages";

    private String group = "DEFAULT_GROUP";

    private long timeoutMs = 3000;

    private long delayMs = 60000;

    private Charset encoding = StandardCharsets.UTF_8;

    private boolean fallbackToSystemLocale = true;

    private boolean alwaysUseMessageFormat = false;

    private boolean useCodeAsDefaultMessage = false;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getBasename() {
        return basename;
    }

    public void setBasename(String basename) {
        this.basename = basename;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public boolean isFallbackToSystemLocale() {
        return fallbackToSystemLocale;
    }

    public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
        this.fallbackToSystemLocale = fallbackToSystemLocale;
    }

    public boolean isAlwaysUseMessageFormat() {
        return alwaysUseMessageFormat;
    }

    public void setAlwaysUseMessageFormat(boolean alwaysUseMessageFormat) {
        this.alwaysUseMessageFormat = alwaysUseMessageFormat;
    }

    public boolean isUseCodeAsDefaultMessage() {
        return useCodeAsDefaultMessage;
    }

    public void setUseCodeAsDefaultMessage(boolean useCodeAsDefaultMessage) {
        this.useCodeAsDefaultMessage = useCodeAsDefaultMessage;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
