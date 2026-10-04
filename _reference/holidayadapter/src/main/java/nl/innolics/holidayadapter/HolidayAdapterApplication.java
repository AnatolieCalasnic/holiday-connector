package nl.innolics.holidayadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class HolidayAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(HolidayAdapterApplication.class, args);
    }
}
