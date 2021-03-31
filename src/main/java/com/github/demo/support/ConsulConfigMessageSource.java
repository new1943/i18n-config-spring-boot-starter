package com.github.demo.support;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.github.demo.context.ConsulMessageSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.consul.config.ConsulConfigProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.AbstractResourceBasedMessageSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.DefaultPropertiesPersister;
import org.springframework.util.PropertiesPersister;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 *
 */
public class ConsulConfigMessageSource extends AbstractResourceBasedMessageSource implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(ConsulConfigMessageSource.class);

    private final ConcurrentMap<String, Long> cachedFetchTime = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, LocalPropertiesHolder> cachedProperties = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Map<Locale, List<String>>> cachedFilenames = new ConcurrentHashMap<>();

    private ConsulClient consul;

    private ConsulMessageSourceProperties i18nProperties;

    private ConsulConfigProperties properties;

    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> watchFuture;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final LinkedHashMap<String, Long> consulIndexes = new LinkedHashMap<>();

    private PropertiesPersister propertiesPersister = new DefaultPropertiesPersister();

    public ConsulConfigMessageSource(ConsulMessageSourceProperties i18nProperties, ConsulConfigProperties properties, ConsulClient consul) {
        this(i18nProperties, properties, consul, getTaskScheduler());
    }

    public ConsulConfigMessageSource(ConsulMessageSourceProperties i18nProperties, ConsulConfigProperties properties, ConsulClient consul, TaskScheduler taskScheduler) {
        this.consul = consul;
        this.i18nProperties = i18nProperties;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        setAlwaysUseMessageFormat(i18nProperties.isAlwaysUseMessageFormat());
        setUseCodeAsDefaultMessage(i18nProperties.isUseCodeAsDefaultMessage());
        setFallbackToSystemLocale(i18nProperties.isFallbackToSystemLocale());
    }

    @Override
    protected String resolveCodeWithoutArguments(String code, Locale locale) {
        List<String> filenames = calculateAllFilenames(i18nProperties.getBasename(), locale);

        for (String filename : filenames) {
            LocalPropertiesHolder holder = cachedProperties.get(filename);
            if (holder == null) {
                Long lastTime = cachedFetchTime.get(filename);
                if (lastTime != null && System.currentTimeMillis() - lastTime < i18nProperties.getDelayMs()) {
                    logger.info("wait for the waiting time end.{}", filename);
                    continue;
                }
                loadInitialProperties(filename);
                holder = cachedProperties.get(filename);
            }
            if (holder != null) {
                String result = holder.getProperty(code);
                if (!StringUtils.isEmpty(result)) {
                    return result;
                }
            }
        }


        return null;
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        List<String> filenames = calculateAllFilenames(i18nProperties.getBasename(), locale);
        for (String filename : filenames) {
            LocalPropertiesHolder holder = cachedProperties.get(filename);
            if (holder == null) {
                Long lastTime = cachedFetchTime.get(filename);
                if (lastTime != null && System.currentTimeMillis() - lastTime < i18nProperties.getDelayMs()) {
                    logger.info("wait for the waiting time end.{}", filename);
                    continue;
                }
                loadInitialProperties(filename);
                holder = cachedProperties.get(filename);
            }
            if (holder != null) {
                MessageFormat result = holder.getMessageFormat(code, locale);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }


    protected List<String> calculateAllFilenames(String basename, Locale locale) {
        Map<Locale, List<String>> localeMap = this.cachedFilenames.get(basename);
        if (localeMap != null) {
            List<String> filenames = localeMap.get(locale);
            if (filenames != null) {
                return filenames;
            }
        }

        // Filenames for given Locale
        List<String> filenames = new ArrayList<>(7);
        filenames.addAll(calculateFilenamesForLocale(basename, locale));

        // Filenames for default Locale, if any
        Locale defaultLocale = getDefaultLocale();
        if (defaultLocale != null && !defaultLocale.equals(locale)) {
            List<String> fallbackFilenames = calculateFilenamesForLocale(basename, defaultLocale);
            for (String fallbackFilename : fallbackFilenames) {
                if (!filenames.contains(fallbackFilename)) {
                    // Entry for fallback locale that isn't already in filenames list.
                    filenames.add(fallbackFilename);
                }
            }
        }

        // Filename for default bundle file
        filenames.add(basename);

        if (localeMap == null) {
            localeMap = new ConcurrentHashMap<>();
            Map<Locale, List<String>> existing = this.cachedFilenames.putIfAbsent(basename, localeMap);
            if (existing != null) {
                localeMap = existing;
            }
        }
        localeMap.put(locale, filenames);
        return filenames;
    }


    protected List<String> calculateFilenamesForLocale(String basename, Locale locale) {
        List<String> result = new ArrayList<>(3);
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String variant = locale.getVariant();
        StringBuilder temp = new StringBuilder(basename);

        temp.append('_');
        if (language.length() > 0) {
            temp.append(language);
            result.add(0, temp.toString());
        }

        temp.append('_');
        if (country.length() > 0) {
            temp.append(country);
            result.add(0, temp.toString());
        }

        if (variant.length() > 0 && (language.length() > 0 || country.length() > 0)) {
            temp.append('_').append(variant);
            result.add(0, temp.toString());
        }

        return result;
    }


    public void loadInitialProperties(String code) {
        logger.info("[ConsulConfigMessage] Initial properties from consul : {}", code);
        if (this.consul == null) {
            throw new IllegalStateException("Consul has not been initialized or error occurred");
        }
        cachedFetchTime.put(code, System.currentTimeMillis());
        Response<GetValue> response = consul.getKVValue(code, this.properties.getAclToken(), QueryParams.DEFAULT);
        if (response == null) {
            return;
        }
        logger.debug("[ConsulConfigMessage] receive {} properties {} from consul", code, response);
        GetValue getValue = response.getValue();
        Long currentIndex = response.getConsulIndex();
        if (getValue != null) {
            loadProperties(code, getValue.getDecodedValue(), currentIndex);
        }
    }

    private void loadProperties(String code, String source, Long index) {
        if (!StringUtils.isEmpty(source)) {
            final Properties props = new Properties();
            try {
                // Must use the ISO-8859-1 encoding because Properties.load(stream)
                // expects it.
                // props.load(new ByteArrayInputStream(source.getBytes("ISO-8859-1")));
                propertiesPersister.load(props, new InputStreamReader(new ByteArrayInputStream(source.getBytes()), "UTF-8"));
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        source + " can't be encoded using ISO-8859-1");
            }
            cachedProperties.put(code, new LocalPropertiesHolder(props));
            consulIndexes.put(code, index);
        }
    }

    private static ThreadPoolTaskScheduler getTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setThreadNamePrefix("i18n-consul-ds-watcher");
        taskScheduler.initialize();
        return taskScheduler;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void start() {
        if (this.running.compareAndSet(false, true)) {
            this.watchFuture = this.taskScheduler.scheduleWithFixedDelay(
                    this::listenerProperties, this.properties.getWatch().getDelay());
        }
    }

    @Override
    public void stop() {
        if (this.running.compareAndSet(true, false) && this.watchFuture != null) {
            this.watchFuture.cancel(true);
        }
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    public void listenerProperties() {
        if (this.running.get()) {
            for (String context : this.consulIndexes.keySet()) {

                Long lastIndex = this.consulIndexes.get(context);

                // It will be blocked until watchTimeout(s) if rule data has no update.
                logger.debug("[ConsulConfigMessage] watch {} config properties", context);
                Response<GetValue> response = this.consul.getKVValue(context,
                        this.properties.getAclToken(),
                        new QueryParams(this.properties.getWatch().getWaitTime(),
                                lastIndex));
                if (response == null) {
                    logger.debug("[ConsulConfigMessage] {} config properties no update", context);
                    continue;
                }
                GetValue getValue = response.getValue();
                Long currentIndex = response.getConsulIndex();
                if (currentIndex == null || currentIndex <= lastIndex) {
                    logger.debug("[ConsulConfigMessage] {} config properties no update", context);
                    continue;
                }
                if (getValue != null) {
                    logger.info("[ConsulConfigMessage] new {} config properties received ({} - {})", context, lastIndex, currentIndex);
                    loadProperties(context, getValue.getDecodedValue(), currentIndex);
                }
            }
        }
    }


}
