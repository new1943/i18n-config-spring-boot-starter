package com.github.demo.support;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.github.demo.context.NacosMessageSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractResourceBasedMessageSource;
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

public class NacosConfigMessageSource extends AbstractResourceBasedMessageSource {

    private static final Logger logger = LoggerFactory.getLogger(NacosConfigMessageSource.class);

    private NacosConfigProperties nacosConfigProperties;

    private NacosMessageSourceProperties i18nProperties;

    /**
     * Note: The Nacos config might be null if its initialization failed.
     */
    private ConfigService configService;

    private final ConcurrentMap<String, Long> cachedFetchTime = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, LocalPropertiesHolder> cachedProperties = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Map<Locale, List<String>>> cachedFilenames = new ConcurrentHashMap<>();

    private PropertiesPersister propertiesPersister = new DefaultPropertiesPersister();

    public NacosConfigMessageSource(NacosMessageSourceProperties i18nProperties, NacosConfigProperties configProperties) {
        this.i18nProperties = i18nProperties;
        this.nacosConfigProperties = configProperties;
        initNacosConfigServer(i18nProperties.getNamespace(), configProperties);
    }

    private void initNacosConfigServer(String namespace, NacosConfigProperties properties) {
        try {
            Properties props = new Properties(properties.assembleConfigServiceProperties());
            props.put(PropertyKeyConst.NAMESPACE, namespace);
            this.configService = NacosFactory.createConfigService(props);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                    logger.debug("wait for the delay time to expire. {}", filename);
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
        logger.info("[NacosConfigMessage] Initial properties from nacos : {}", code);
        if (this.configService == null) {
            throw new IllegalStateException("Nacos config server has not been initialized or error occurred");
        }
        try {
            cachedFetchTime.put(code, System.currentTimeMillis());
            String value = this.configService
                    .getConfig(code, i18nProperties.getGroup(), i18nProperties.getTimeoutMs());
            if (!StringUtils.isEmpty(value)) {
                loadProperties(code, value);
                this.configService.addListener(code, i18nProperties.getGroup(), new AbstractSharedListener() {
                    @Override
                    public void innerReceive(String dataId, String group, String configInfo) {
                        logger.info("[NacosConfigMessage] receive {} message source update: {}", dataId, configInfo);
                        loadProperties(dataId, configInfo);
                    }
                });
            }
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }


    private void loadProperties(String code, String source) {
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
        }
    }
}
