package net.java21.flyschedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // SearchJobService가 끝난 작업을 주기적으로 정리
public class FlyscheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlyscheduleApplication.class, args);
    }

}
