package com.localmesalevel.aisystemtakeone.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ArticleGenerationExecutionConfig {

    @Value("${article-generation.jobs.pool-size:5}")
    private int articleGenerationJobsPoolSize;

    @Bean(name = "articleGenerationJobExecutor")
    public ThreadPoolTaskExecutor articleGenerationJobExecutor() {
        int poolSize = Math.max(articleGenerationJobsPoolSize, 1);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("article-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
