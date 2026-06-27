package com.ledgerone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LedgerOneApplicationTests {

	@Test
	void applicationEntryPointIsAvailable() {
		assertThat(LedgerOneApplication.class).isNotNull();
	}

}
