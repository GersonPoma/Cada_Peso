package com.presupuesto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void laZonaHorariaPorDefectoDeLaJvmEsUtc() {
		assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
	}

}
