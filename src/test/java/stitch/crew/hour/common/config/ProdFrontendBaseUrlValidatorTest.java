package stitch.crew.hour.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("ProdFrontendBaseUrlValidator의")
class ProdFrontendBaseUrlValidatorTest {

	@Test
	@DisplayName("It: prod 프로필에서 프론트 URL이 localhost이면 예외를 던진다")
	void it_throws_when_frontend_base_url_is_localhost_in_prod() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		ProdFrontendBaseUrlValidator validator = new ProdFrontendBaseUrlValidator(
			environment,
			"http://localhost:5173"
		);

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("FRONTEND_BASE_URL");
	}

	@Test
	@DisplayName("It: prod 프로필에서 배포 프론트 URL이면 통과한다")
	void it_passes_when_frontend_base_url_is_public_in_prod() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		ProdFrontendBaseUrlValidator validator = new ProdFrontendBaseUrlValidator(
			environment,
			"https://www.h-our.shop"
		);

		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("It: prod 프로필에서 프론트 URL이 절대 URL이 아니면 예외를 던진다")
	void it_throws_when_frontend_base_url_is_not_absolute_in_prod() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		ProdFrontendBaseUrlValidator validator = new ProdFrontendBaseUrlValidator(
			environment,
			"www.h-our.shop"
		);

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("absolute URL");
	}

	@Test
	@DisplayName("It: prod 프로필이 아니면 localhost 프론트 URL도 허용한다")
	void it_passes_when_profile_is_not_prod() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("dev");
		ProdFrontendBaseUrlValidator validator = new ProdFrontendBaseUrlValidator(
			environment,
			"http://localhost:5173"
		);

		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}
}
