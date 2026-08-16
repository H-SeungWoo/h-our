package stitch.crew.hour.common.config;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import stitch.crew.hour.policy.domain.LessonPolicy;
import stitch.crew.hour.policy.domain.ShippingPolicy;
import stitch.crew.hour.policy.repository.LessonPolicyRepository;
import stitch.crew.hour.policy.repository.ShippingPolicyRepository;
import stitch.crew.hour.category.domain.Category;
import stitch.crew.hour.category.repository.CategoryRepository;
import stitch.crew.hour.order.domain.Order;
import stitch.crew.hour.order.dto.OrderSearchResponse;
import stitch.crew.hour.order.repository.OrderBoundaryRepository;
import stitch.crew.hour.orderproduct.domain.OrderProduct;
import stitch.crew.hour.product.domain.Product;
import stitch.crew.hour.product.repository.ProductRepository;
import stitch.crew.hour.user.constant.Gender;
import stitch.crew.hour.user.constant.Role;
import stitch.crew.hour.user.domain.User;
import stitch.crew.hour.user.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class DataInitConfig {
    private static final String SAMPLE_USER_EMAIL = "payment-test@hour.test";
    private static final String SAMPLE_USER_PASSWORD = "password1234!";
    private static final String SAMPLE_CATEGORY_NAME = "테스트 상품";

    private final ShippingPolicyRepository shippingPolicyRepository;
    private final LessonPolicyRepository lessonPolicyRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OrderBoundaryRepository orderBoundaryRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Bean
    @Transactional
    CommandLineRunner init() {
        return _ -> {
            if (shippingPolicyRepository.findActive().isEmpty()) {
                shippingPolicyRepository.save(
                        new ShippingPolicy(
                                3_500L,
                                2_000L,
                                true
                        )
                );
            }
            lessonPolicyRepository.save(
                    new LessonPolicy(21,
                            3,
                            1,
                            10000,
                            LocalTime.of(9,0),
                            LocalTime.of(18,0),
                            Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
            );

            createInitialAdminIfConfigured();

            if (environment.matchesProfiles("dev")) {
                createSampleOrderData();
            }
        };
    }

    private void createSampleOrderData() {
        User user = userRepository.findByEmail(SAMPLE_USER_EMAIL)
                .orElseGet(() -> userRepository.save(
                        new User(
                                "결제테스트",
                                SAMPLE_USER_EMAIL,
                                passwordEncoder.encode(SAMPLE_USER_PASSWORD),
                                LocalDate.of(1998, 7, 9),
                                Role.USER,
                                Gender.MALE,
                                null,
                                "01012345678",
                                "KOREA",
                                false,
                                false
                        )
                ));

        Page<OrderSearchResponse> existingOrders = orderBoundaryRepository.findOrderByUserId(
                user.getId(),
                PageRequest.of(0, 1)
        );

        if (existingOrders.hasContent() && existingOrders.getContent().getFirst().totalPrice() > 0) {
            OrderSearchResponse existingOrder = existingOrders.getContent().getFirst();
            log.info("Sample payment test account: email={}, password={}", SAMPLE_USER_EMAIL, SAMPLE_USER_PASSWORD);
            log.info("Sample payment orderNumber={}", existingOrder.orderNumber());
            log.info("Sample payment page=/api/payments/orders/{}/detail", existingOrder.orderNumber());
            return;
        }

        Category category = categoryRepository.findByName(SAMPLE_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository.save(new Category(SAMPLE_CATEGORY_NAME)));

        Product hoodie = productRepository.save(
                new Product(
                        "H-OUR 시그니처 후드",
                        100L,
                        "결제 테스트용 샘플 상품",
                        "토스 결제 위젯 연동을 확인하기 위한 샘플 상품입니다.",
                        category
                )
        );

        Product cap = productRepository.save(
                new Product(
                        "H-OUR 볼캡",
                        300L,
                        "결제 테스트용 샘플 상품",
                        "주문 상품 여러 건 표시를 확인하기 위한 샘플 상품입니다.",
                        category
                )
        );

        ShippingPolicy activeShippingPolicy = shippingPolicyRepository.findActiveOrThrow();
        Order order = orderBoundaryRepository.saveOrder(
                new Order(
                        user,
                        activeShippingPolicy.getDeliveryFee(),
                        "서울특별시 강남구 테헤란로 123",
                        "06134",
                        "결제테스트",
                        "문 앞에 놓아주세요.",
                        "01012345678"
                )
        );

        OrderProduct hoodieOrderProduct = orderBoundaryRepository.saveOrderProduct(
                new OrderProduct(
                        hoodie.getName(),
                        1L,
                        hoodie.getPrice(),
                        hoodie.getId(),
                        "BLACK / L",
                        order
                )
        );

        OrderProduct capOrderProduct = orderBoundaryRepository.saveOrderProduct(
                new OrderProduct(
                        cap.getName(),
                        2L,
                        cap.getPrice(),
                        cap.getId(),
                        "NAVY",
                        order
                )
        );

        order.setOrderProduct(hoodieOrderProduct);
        order.setOrderProduct(capOrderProduct);
        orderBoundaryRepository.saveOrder(order);

        log.info("Sample payment test account: email={}, password={}", SAMPLE_USER_EMAIL, SAMPLE_USER_PASSWORD);
        log.info("Sample payment orderNumber={}", order.getOrderNumber());
        log.info("Sample payment page=/api/payments/orders/{}/detail", order.getOrderNumber());
    }

    private void createInitialAdminIfConfigured() {
        String email = environment.getProperty("INITIAL_ADMIN_EMAIL");
        String password = environment.getProperty("INITIAL_ADMIN_PASSWORD");
        String userName = environment.getProperty("INITIAL_ADMIN_USER_NAME", "Initial Admin");
        String phoneNumber = environment.getProperty("INITIAL_ADMIN_PHONE_NUMBER");
        String nationality = environment.getProperty("INITIAL_ADMIN_NATIONALITY", "KOREA");
        LocalDate birthDate = environment.getProperty(
                "INITIAL_ADMIN_BIRTH_DATE",
                LocalDate.class,
                LocalDate.of(2000, 1, 31)
        );
        Gender gender = environment.getProperty("INITIAL_ADMIN_GENDER", Gender.class, Gender.MALE);

        if (
                !StringUtils.hasText(email)
                        || !StringUtils.hasText(password)
                        || !StringUtils.hasText(userName)
                        || !StringUtils.hasText(phoneNumber)
                        || !StringUtils.hasText(nationality)
        ) {
            log.warn("초기 어드민 생성 패쓰.");
            return;
        }

        if (userRepository.existsByEmail(email)) {
            return;
        }

        User admin = new User(
                userName,
                email,
                passwordEncoder.encode(password),
                birthDate,
                Role.ADMIN,
                gender,
                null,
                phoneNumber,
                nationality,
                false,
                false
        );

        userRepository.save(admin);
    }
}
