package cn.aslight.workhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
/**
 * AsyncTaskConfig 模型。
 */
public class AsyncTaskConfig {

    @Bean(name = "workhubTaskExecutor", destroyMethod = "close")
    public Executor workhubTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("workhub-async-");
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(5000);
        return executor;
    }
}
