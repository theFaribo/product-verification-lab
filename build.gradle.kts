plugins {
	kotlin("jvm") version "2.3.21" apply false
	kotlin("plugin.spring") version "2.3.21" apply false
	kotlin("plugin.jpa") version "2.3.21" apply false
	id("org.springframework.boot") version "4.1.0" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false

}

allprojects {
	group = "ru.example.productverification"
	version = "0.1.0-SNAPSHOT"
}

subprojects {
	tasks.withType<Test>().configureEach {
		useJUnitPlatform()
	}
}
