package nl.innolics.holidayprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HolidayProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HolidayProcessorApplication.class, args);
    }
}
