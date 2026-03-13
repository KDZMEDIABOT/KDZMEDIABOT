package com.localmesalevel.aisystemtakeone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class AiSystemApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context loads successfully with dev profile
        // Dev profile uses H2 database in test resources (test-scoped)
        // Blue-green is disabled in dev profile
    }

}
