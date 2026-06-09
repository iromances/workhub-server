package cn.aslight.workhub;

import cn.aslight.workhub.config.JwtProperties;
import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.config.PaymentProperties;
import cn.aslight.workhub.config.ReminderProperties;
import cn.aslight.workhub.config.StorageProperties;
import cn.aslight.workhub.config.WecomRobotProperties;
import cn.aslight.workhub.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, WecomRobotProperties.class, ReminderProperties.class, AiProperties.class, StorageProperties.class, PaymentProperties.class, McpProperties.class})
/**
 * WorkHub 后端启动入口。
 */
public class WorkhubServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkhubServerApplication.class, args);
    }
}
