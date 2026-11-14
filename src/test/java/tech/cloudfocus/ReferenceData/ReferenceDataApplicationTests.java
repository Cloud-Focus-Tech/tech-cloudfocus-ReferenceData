package tech.cloudfocus.ReferenceData;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import tech.cloudfocus.ReferenceData.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import tech.cloudfocus.ReferenceData.repository.CollectionRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {"spring.config.import=", "app.storage.refresh-interval-ms=3600000"})
class ReferenceDataApplicationTests {

    @TestBean(methodName = "fixtureProvider")
    StorageProvider storageProvider;

    static StorageProvider fixtureProvider() throws Exception { return FixtureStorage.fromData(); }

    @Autowired
    CollectionRepository repository;

	@Test
	void contextLoads() {
		assertEquals(7, repository.findAll().size());
	}

}
