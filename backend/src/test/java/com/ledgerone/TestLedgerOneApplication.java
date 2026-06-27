package com.ledgerone;

import org.springframework.boot.SpringApplication;

public class TestLedgerOneApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerOneApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
