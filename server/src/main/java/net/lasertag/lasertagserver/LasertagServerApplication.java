package net.lasertag.lasertagserver;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;

@SpringBootApplication
@EnableScheduling
public class LasertagServerApplication {

	public static void main(String[] args) throws Exception {
		UIManager.setLookAndFeel(
			UIManager.getSystemLookAndFeelClassName());

		SpringApplicationBuilder builder = new SpringApplicationBuilder(LasertagServerApplication.class);
		builder.headless(false);
		builder.run(args);
	}

}
