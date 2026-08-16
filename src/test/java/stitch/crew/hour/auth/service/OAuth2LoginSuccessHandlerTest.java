package stitch.crew.hour.auth.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import stitch.crew.hour.auth.dto.KeyPair;
import stitch.crew.hour.user.constant.Gender;
import stitch.crew.hour.user.constant.Role;
import stitch.crew.hour.user.domain.User;
import stitch.crew.hour.user.repository.UserRepository;
import stitch.crew.hour.auth.dto.OAuthSignupPayload;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2LoginSuccessHandler의")
class OAuth2LoginSuccessHandlerTest {

	@InjectMocks
	private OAuth2LoginSuccessHandler successHandler;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private SignupTokenStore oauthSignupTokenStore;

	@Mock
	private OAuthSignupCookieManager oAuthSignupCookieManager;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(successHandler, "frontendBaseUrl", "http://localhost:5173");
	}

	@Nested
	@DisplayName("Describe: onAuthenticationSuccess 메서드는")
	class Describe_onAuthenticationSuccess {

		@Test
		@DisplayName("It: 기존 회원이면 OAuth 정보를 연동하고 토큰 콜백 페이지로 리다이렉트한다")
		void it_links_oauth_and_redirects_to_callback_when_user_exists() throws Exception {
			// given
			User user = createUser();
			KeyPair keyPair = new KeyPair("access-token", "refresh-token");
			OAuth2AuthenticationToken authentication = createOAuthAuthentication(
				user.getEmail(),
				"Google User"
			);
			MockHttpServletResponse response = new MockHttpServletResponse();

			given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
			given(jwtTokenProvider.issueKeyPair(user.getEmail(), user.getRole())).willReturn(keyPair);

			// when
			successHandler.onAuthenticationSuccess(
				new MockHttpServletRequest(),
				response,
				authentication
			);

			// then
			assertThat(user.getProvider()).isEqualTo("GOOGLE");
			assertThat(user.getIsAuthLinked()).isTrue();
			assertThat(response.getRedirectedUrl()).contains("http://localhost:5173/oauth/callback");
			assertThat(response.getRedirectedUrl()).contains("accessToken=access-token");
			assertThat(response.getRedirectedUrl()).contains("refreshToken=refresh-token");

			verify(userRepository).save(user);
		}

		@Test
		@DisplayName("It: 신규 회원이면 OAuth 회원가입 페이지로 리다이렉트한다")
		void it_redirects_to_signup_when_user_does_not_exist() throws Exception {
			// given
			OAuth2AuthenticationToken authentication = createOAuthAuthentication(
				"hello@google.com",
				"hihiUser"
			);
			MockHttpServletResponse response = new MockHttpServletResponse();

			given(userRepository.findByEmail("hello@google.com")).willReturn(Optional.empty());
			given(oauthSignupTokenStore.save(any(OAuthSignupPayload.class))).willReturn("signup-token");

			// when
			successHandler.onAuthenticationSuccess(
				new MockHttpServletRequest(),
				response,
				authentication
			);

			// then
			assertThat(response.getRedirectedUrl()).contains("http://localhost:5173/signup");
			assertThat(response.getRedirectedUrl()).doesNotContain("signupToken=");
			assertThat(response.getRedirectedUrl()).doesNotContain("oauth=true");
			assertThat(response.getRedirectedUrl()).doesNotContain("email=");
			assertThat(response.getRedirectedUrl()).doesNotContain("name=");
			assertThat(response.getRedirectedUrl()).doesNotContain("provider=");
			verify(oAuthSignupCookieManager).addSignupTokenCookie(response, "signup-token");

			ArgumentCaptor<OAuthSignupPayload> payloadCaptor =
				ArgumentCaptor.forClass(OAuthSignupPayload.class);
			verify(oauthSignupTokenStore).save(payloadCaptor.capture());

			OAuthSignupPayload payload = payloadCaptor.getValue();
			assertThat(payload.email()).isEqualTo("hello@google.com");
			assertThat(payload.userName()).isEqualTo("hihiUser");
			assertThat(payload.provider()).isEqualTo("GOOGLE");
		}

		@Test
		@DisplayName("It: 배포 프론트 URL이 설정되면 해당 주소의 콜백 페이지로 리다이렉트한다")
		void it_redirects_to_configured_frontend_callback_url() throws Exception {
			// given
			ReflectionTestUtils.setField(successHandler, "frontendBaseUrl", "https://www.h-our.shop/");
			User user = createUser();
			KeyPair keyPair = new KeyPair("access-token", "refresh-token");
			OAuth2AuthenticationToken authentication = createOAuthAuthentication(
				user.getEmail(),
				"Google User"
			);
			MockHttpServletResponse response = new MockHttpServletResponse();

			given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
			given(jwtTokenProvider.issueKeyPair(user.getEmail(), user.getRole())).willReturn(keyPair);

			// when
			successHandler.onAuthenticationSuccess(
				new MockHttpServletRequest(),
				response,
				authentication
			);

			// then
			assertThat(response.getRedirectedUrl()).startsWith("https://www.h-our.shop/oauth/callback");
		}
	}

	private OAuth2AuthenticationToken createOAuthAuthentication(
		String email,
		String name
	) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("email", email);
		attributes.put("name", name);

		OAuth2User oauthUser = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			attributes,
			"email"
		);

		return new OAuth2AuthenticationToken(
			oauthUser,
			oauthUser.getAuthorities(),
			"google"
		);
	}

	private User createUser() {
		return new User(
			"대정수",
			"legend@naver.com",
			"encodedPassword",
			LocalDate.of(2000, 1, 1),
			Role.USER,
			Gender.MALE,
			null,
			"010-1234-5678",
			"KOREA",
			false,
			false
		);
	}
}
