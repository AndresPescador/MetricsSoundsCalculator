package com.ud.metricssoundscalculator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
public class MetricsSoundsCalculatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsSoundsCalculatorApplication.class, args);
    }

}
