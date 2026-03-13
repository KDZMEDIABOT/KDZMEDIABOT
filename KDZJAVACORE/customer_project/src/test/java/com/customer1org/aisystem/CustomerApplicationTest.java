package com.customer1org.aisystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class CustomerApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context loads successfully with test profile
        // Test profile uses H2 database and has blue-green disabled
    }

}
