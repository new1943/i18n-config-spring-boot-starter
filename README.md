# i18n Message Source

i18n Message Source provides integration with Consul or Nacos

## Usage

1. To use i18n Message Source , you could add the following dependency:

```xml

<dependency>
    <groupId>com.github.demo</groupId>
    <artifactId>i18n-config-spring-boot-starter</artifactId>
    <version>x.y.z</version>
</dependency>
```

2. Use Nacos or Consul Config Server. create i18n namespace or folder, add messages source file.

> **NOTE**: Message source config must use properties format and the UTF-8 encoding

3. Then Config your project bootstrap or application config file

#### Consul

```yaml
i18n:
  consul:
    # format consul folder/basename
    basename: i18n/messages
```

#### Nacos

```yaml
i18n:
  nacos:
    namespace: i18n
    # default basename: messages
    # default group: DEFAULT_GROUP
```

3. Configuration your  **LocaleResolver**. example accept-language:

```java
public class WebMvcConfig implements WebMvcConfigurer {


    @Bean
    public LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver();
    }
}
```

4. Then you can inject **MessageSource** Instance Or implements **MessageSourceAware** Interface

```java
public class xxx {
    @Autowired
    private MessageSource messageSource;

    @GetMapping(value = "/test")
    public Wrapper test() {
        return WrapMapper.ok(messageSource.getMessage("hello.world", new Object[]{"医百科技"}, Locale.CHINA));
    }
}
```


```java
public class xxxx implements MessageSourceAware {
    private MessageSourceAccessor messageSourceAccessor;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSourceAccessor = new MessageSourceAccessor(messageSource);
    }

    @GetMapping(value = "/test")
    public IResponse test() {
        return IResponse.success(messageSourceAccessor.getMessage("hello.world", new Object[]{"哈哈哈"}));
    }
}
```
