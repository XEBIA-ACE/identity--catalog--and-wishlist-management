package com.xebia.ace.shopping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShoppingCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCoreApplication.class, args);
    }
}
