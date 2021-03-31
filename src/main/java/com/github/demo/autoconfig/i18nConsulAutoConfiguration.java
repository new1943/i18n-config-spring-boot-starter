package com.github.demo.autoconfig;


import com.ecwid.consul.v1.ConsulClient;
import com.github.demo.context.ConsulMessageSourceProperties;
import com.github.demo.support.ConsulConfigMessageSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.consul.ConditionalOnConsulEnabled;
import org.springframework.cloud.consul.config.ConsulConfigProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnConsulEnabled
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "i18n.consul.enabled", matchIfMissing = true)
@ConditionalOnClass(ConsulConfigProperties.class)
public class i18nConsulAutoConfiguration {

    public static final String CONFIG_WATCH_TASK_SCHEDULER_NAME = "consulMessageSourceTaskScheduler";

    @Bean
    @ConfigurationProperties(prefix = "i18n.consul")
    public ConsulMessageSourceProperties consulMessageSourceProperties() {
        return new ConsulMessageSourceProperties();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.consul.config.watch.enabled",
            matchIfMissing = true)
    public MessageSource messageSource(ConsulMessageSourceProperties i18nProperties, ConsulConfigProperties properties , ConsulClient consul, @Qualifier(CONFIG_WATCH_TASK_SCHEDULER_NAME) TaskScheduler taskScheduler) {
        return new ConsulConfigMessageSource(i18nProperties , properties , consul , taskScheduler);
    }

    @Bean(name = CONFIG_WATCH_TASK_SCHEDULER_NAME)
    @ConditionalOnProperty(name = "spring.cloud.consul.config.watch.enabled",
            matchIfMissing = true)
    public TaskScheduler configWatchTaskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

}
