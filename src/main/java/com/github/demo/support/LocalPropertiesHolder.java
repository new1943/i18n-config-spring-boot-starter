package com.github.demo.support;

import org.springframework.lang.Nullable;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LocalPropertiesHolder {
    @Nullable
    private final Properties properties;

    /** Cache to hold already generated MessageFormats per message code. */
    private final ConcurrentMap<String, Map<Locale, MessageFormat>> cachedMessageFormats =
            new ConcurrentHashMap<>();

    public LocalPropertiesHolder() {
        this.properties = null;
    }

    public LocalPropertiesHolder(Properties properties) {
        this.properties = properties;
    }

    @Nullable
    public Properties getProperties() {
        return this.properties;
    }

    @Nullable
    public String getProperty(String code) {
        if (this.properties == null) {
            return null;
        }
        return this.properties.getProperty(code);
    }

    @Nullable
    public MessageFormat getMessageFormat(String code, Locale locale) {
        if (this.properties == null) {
            return null;
        }
        Map<Locale, MessageFormat> localeMap = this.cachedMessageFormats.get(code);
        if (localeMap != null) {
            MessageFormat result = localeMap.get(locale);
            if (result != null) {
                return result;
            }
        }
        String msg = this.properties.getProperty(code);
        if (msg != null) {
            if (localeMap == null) {
                localeMap = new ConcurrentHashMap<>();
                Map<Locale, MessageFormat> existing = this.cachedMessageFormats.putIfAbsent(code, localeMap);
                if (existing != null) {
                    localeMap = existing;
                }
            }
            MessageFormat result = new MessageFormat(msg, locale);
            localeMap.put(locale, result);
            return result;
        }
        return null;
    }
}
