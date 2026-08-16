package stitch.crew.hour.common.config;

import java.net.URI;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProdFrontendBaseUrlValidator implements ApplicationRunner {

	private final Environment environment;
	private final String frontendBaseUrl;

	public ProdFrontendBaseUrlValidator(
		Environment environment,
		@Value("${app.frontend.base-url}") String frontendBaseUrl
	) {
		this.environment = environment;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!environment.acceptsProfiles(Profiles.of("prod"))) {
			return;
		}

		String primaryFrontendBaseUrl = Arrays.stream(frontendBaseUrl.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("FRONTEND_BASE_URL must not be blank in prod profile."));

		String host = URI.create(primaryFrontendBaseUrl).getHost();

		if (!StringUtils.hasText(host)) {
			throw new IllegalStateException("FRONTEND_BASE_URL must be an absolute URL in prod profile.");
		}

		if (isLocalHost(host)) {
			throw new IllegalStateException("FRONTEND_BASE_URL must not point to localhost in prod profile.");
		}
	}

	private boolean isLocalHost(String host) {
		return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
	}
}
