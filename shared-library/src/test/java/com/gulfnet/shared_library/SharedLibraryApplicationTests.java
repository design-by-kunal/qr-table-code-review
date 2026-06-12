package com.gulfnet.shared_library;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;


@Disabled("Disabled - requires encryption key configuration")
@SpringBootTest
@Import(DisableLiquibaseTestConfig.class)
class SharedLibraryApplicationTests {

	@Test
	void contextLoads() {
		/*
		 * Verifies that the Spring application context starts; no further assertions are required
		 * for this smoke test (module is normally @Disabled due to encryption key requirements).
		 */
	}

}
