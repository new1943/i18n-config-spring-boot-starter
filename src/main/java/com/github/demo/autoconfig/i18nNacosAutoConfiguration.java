package com.github.demo.autoconfig;


import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.github.demo.context.NacosMessageSourceProperties;
import com.github.demo.support.NacosConfigMessageSource;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "i18n.nacos.enabled", matchIfMissing = true)
@ConditionalOnClass(NacosConfigProperties.class)
public class i18nNacosAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "i18n.nacos")
    public NacosMessageSourceProperties nacosMessageSourceProperties() {
        return new NacosMessageSourceProperties();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.nacos.config.enabled", matchIfMissing = true)
    public MessageSource messageSource(NacosMessageSourceProperties i18nProperties, NacosConfigProperties properties) {
        return new NacosConfigMessageSource(i18nProperties, properties);
    }

}
