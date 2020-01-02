package xyz.kail.demo.mysql.oneproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;

@SpringBootApplication
public class OneProxyApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(OneProxyApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(OneProxyApplication.class);
    }

}
