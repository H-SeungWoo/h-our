package stitch.crew.hour.auth.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import stitch.crew.hour.auth.dto.KeyPair;
import stitch.crew.hour.auth.dto.OAuthSignupPayload;
import stitch.crew.hour.common.exception.BusinessException;
import stitch.crew.hour.common.exception.ErrorCode;
import stitch.crew.hour.user.domain.User;
import stitch.crew.hour.user.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final SignupTokenStore oauthSignupTokenStore;
	private final OAuthSignupCookieManager oAuthSignupCookieManager;

	@Value("${app.frontend.base-url}")
	private String frontendBaseUrl;

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException, ServletException {

		OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
		OAuth2User oauthUser = oauthToken.getPrincipal();

		String provider = oauthToken.getAuthorizedClientRegistrationId().toUpperCase(Locale.ROOT);
		String email = oauthUser.getAttribute("email");
		String name = oauthUser.getAttribute("name");

		if (!StringUtils.hasText(email) || !StringUtils.hasText(name)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth2 사용자 정보에 필수 값이 없습니다.");
		}

		// 이메일 기반으로 찾아보고, 있으면 기존 계정으로 로그인
		Optional<User> user = userRepository.findByEmail(email)
			.filter(foundUser -> foundUser.getDeletedAt() == null);

		if (user.isPresent()) {
			KeyPair keyPair = setOAuthAndIssueToken(user.get(), provider);
			response.sendRedirect(createOAuthLoginRedirectUrl(keyPair));
			return;
		}

		String signupToken = oauthSignupTokenStore.save(
			new OAuthSignupPayload(email, name, provider)
		);
		oAuthSignupCookieManager.addSignupTokenCookie(response, signupToken);
		response.sendRedirect(createOAuthSignupRedirectUrl());

	}

	// 기존 회원일때 실행할 메서드 (토큰 발행, DB수정)
	private KeyPair setOAuthAndIssueToken(
		User user,
		String provider
	) {
		user.setOAuth(provider);
		userRepository.save(user);
		return jwtTokenProvider.issueKeyPair(user.getEmail(), user.getRole());
	}

	// 회원가입 페이지
	private String createOAuthSignupRedirectUrl() {
		return UriComponentsBuilder.fromUriString(getPrimaryFrontendBaseUrl())
			.path("/signup")
			.build()
			.encode()
			.toUriString();
	}

	private String createOAuthLoginRedirectUrl(KeyPair keyPair) {
		return UriComponentsBuilder.fromUriString(getPrimaryFrontendBaseUrl())
			.path("/oauth/callback")
			.queryParam("accessToken", keyPair.accessToken())
			.queryParam("refreshToken", keyPair.refreshToken())
			.build()
			.encode()
			.toUriString();
	}

	private String getPrimaryFrontendBaseUrl() {
		String primaryFrontendBaseUrl = Arrays.stream(frontendBaseUrl.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("app.frontend.base-url must not be blank"));

		while (primaryFrontendBaseUrl.endsWith("/")) {
			primaryFrontendBaseUrl = primaryFrontendBaseUrl.substring(0, primaryFrontendBaseUrl.length() - 1);
		}

		return primaryFrontendBaseUrl;
	}
}
