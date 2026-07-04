package uz.sonic.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync(proxyTargetClass = true)
public class HrApplication {

    static void main(String[] args) {
        SpringApplication.run(HrApplication.class, args);
    }
}
