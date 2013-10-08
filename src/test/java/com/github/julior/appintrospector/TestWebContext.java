package com.github.julior.appintrospector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 *
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {"com.github.julior"})
public class TestWebContext {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer propsConfigurer = new PropertySourcesPlaceholderConfigurer();
        propsConfigurer.setLocation(new ClassPathResource("application.properties"));
        return propsConfigurer;
    }
}

@Component("dummyBean")
class DummyBean{

    public String echo(String str){
        return str;
    }
}

