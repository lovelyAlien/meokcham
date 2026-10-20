package com.meokcham;

import org.springframework.boot.SpringApplication;

public class TestMeokchamApplication {

	public static void main(String[] args) {
		SpringApplication.from(MeokchamApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
