package net.lasertag.lasertagserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppConfig {
	@Bean(name = "daemonExecutor")
	public ThreadPoolTaskExecutor daemonExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(25);
		executor.setThreadNamePrefix("Async-");
		executor.setDaemon(true);
		executor.initialize();
		return executor;
	}

}
