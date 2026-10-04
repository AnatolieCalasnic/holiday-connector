package nl.innolics.holidayadapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks that Spring can build every bean. Catches wiring errors (like a component with two
 * constructors and none marked @Autowired) that unit tests miss because they call constructors directly.
 * The startup sync is switched off so the test does not call sazmanagement or Nager.Date.
 */
@SpringBootTest(properties = "adapter.run-on-startup=false")
class ApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
