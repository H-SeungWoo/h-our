package stitch.crew.hour.payment.controller;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import stitch.crew.hour.category.domain.Category;
import stitch.crew.hour.category.repository.CategoryRepository;
import stitch.crew.hour.common.exception.BusinessException;
import stitch.crew.hour.common.exception.ErrorCode;
import stitch.crew.hour.common.response.SuccessCode;
import stitch.crew.hour.order.constant.OrderStatus;
import stitch.crew.hour.order.domain.Order;
import stitch.crew.hour.order.repository.OrderRepository;
import stitch.crew.hour.order.service.OrderService;
import stitch.crew.hour.orderproduct.domain.OrderProduct;
import stitch.crew.hour.orderproduct.repository.OrderProductRepository;
import stitch.crew.hour.payment.constant.PaymentStatus;
import stitch.crew.hour.payment.domain.Payment;
import stitch.crew.hour.payment.dto.PaymentRequestBody;
import stitch.crew.hour.payment.repository.PaymentRepository;
import stitch.crew.hour.payment.service.PaymentService;
import stitch.crew.hour.product.domain.Product;
import stitch.crew.hour.product.repository.ProductRepository;
import stitch.crew.hour.user.constant.Gender;
import stitch.crew.hour.user.constant.Role;
import stitch.crew.hour.user.domain.CurrentUser;
import stitch.crew.hour.user.domain.User;
import stitch.crew.hour.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class PaymentControllerTest {

    final String BASE_URL = "/api/payments";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderProductRepository orderProductRepository;

    User testUser;
    TestingAuthenticationToken token;
    Category testCategory;
    Product testProduct;
    Order testOrder;
    OrderProduct testOrderProduct;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp(){
        testCategory = categoryRepository.save(
                new Category("카테고리명")
        );

        testProduct = productRepository.save(
                new Product(
                        "테스트용 상품",
                        2000L,
                        "상품요약",
                        "설명글",
                        testCategory
                )
        );

        testUser = userRepository.save(
                new User(
                        "이름",
                        "wjdtn747@naver.com",
                        "1234",
                        LocalDate.now(),
                        Role.USER,
                        Gender.MALE,
                        "google",
                        "010",
                        "?",
                        false,
                        false
                )
        );

        testOrder = orderRepository.save(
                new Order(
                        testUser,
                        3500L,
                        "",
                        "",
                        "",
                        "",
                        ""
                )
        );

        testOrderProduct = orderProductRepository.save(
                new OrderProduct(
                        testProduct.getName(),
                        20L,
                        testProduct.getPrice(),
                        testProduct.getId(),
                        "",
                        testOrder
                )
        );

        testOrder.setOrderProduct(testOrderProduct);


        token = new TestingAuthenticationToken(
                CurrentUser.from(testUser),
                null,
                Role.ADMIN.getValue()
        );
    }

    @Nested
    @DisplayName("Describe : GET /api/payments/orders/{orderNumber}/detail")
    class Describe_getPaymentDetailByOrderNumber{

        PaymentRequestBody paymentRequestBody;
        Payment testPayment;
        @BeforeEach
        void setUp(){
            paymentRequestBody = new PaymentRequestBody(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    testOrder.getOrderNumber().toString(),
                    Long.valueOf(testOrder.getTotalPrice())
            );
            SecurityContextHolder.getContext().setAuthentication(token);
            testPayment = paymentService.initPaymentByOrder(
                    paymentRequestBody
            );
        }

        @Nested
        @DisplayName("Context : 올바른 데이터가 주어지는 경우")
        class Context_with_Valid_data{

            @Test
            @DisplayName("It : 정상적으로 결제 단건 조회 성공")
            void It_결제_조회_완료() throws Exception {
                // given
                testPayment.switchPaymentStatus(PaymentStatus.COMPLETED);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                mockMvc.perform(
                        MockMvcRequestBuilders.get(BASE_URL + "/orders/%s/detail".formatted(testOrder.getOrderNumber()))
                ).andDo(print())

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SuccessCode.PAYMENT_READ_SUCCESS.getSuccessMessage()))
                .andExpect(jsonPath("$.data.orderNumber").value(testOrder.getOrderNumber().toString()));
            }

            @Test
            @DisplayName("It : detail 없는 주문번호 기반 결제 단건 조회도 성공")
            void It_결제_조회_호환_URL_완료() throws Exception {
                // given
                testPayment.switchPaymentStatus(PaymentStatus.COMPLETED);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                mockMvc.perform(
                        MockMvcRequestBuilders.get(BASE_URL + "/orders/%s".formatted(testOrder.getOrderNumber()))
                ).andDo(print())

                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SuccessCode.PAYMENT_READ_SUCCESS.getSuccessMessage()))
                .andExpect(jsonPath("$.data.orderNumber").value(testOrder.getOrderNumber().toString()));
            }
        }

        @Nested
        @DisplayName("Context : 올바르지 않은 데이터가 주어지는 경우")
        class Context_with_InValid_data{

            @Test
            @DisplayName("It : 다른 사용자가 조회를 시도 시 차단")
            void It_결제_조회_실패() throws Exception {
                // given
                testPayment.switchPaymentStatus(PaymentStatus.COMPLETED);

                User userTester = userRepository.save(
                        new User(
                                "이d12름",
                                "wjdtDSAFDA2SFSDAn747@naver.com",
                                "1232214",
                                LocalDate.now(),
                                Role.USER,
                                Gender.MALE,
                                "google",
                                "012412312313423140",
                                "?",
                                false,
                                false
                        )
                );

                TestingAuthenticationToken testingToken = new TestingAuthenticationToken(
                        CurrentUser.from(userTester),
                        null,
                        Role.USER.getValue()
                );

                SecurityContextHolder.getContext().setAuthentication(testingToken);


                // when
                mockMvc.perform(
                                MockMvcRequestBuilders.get(BASE_URL + "/orders/%s/detail".formatted(testOrder.getOrderNumber()))
                        ).andDo(print())

                        // then
                        .andExpect(status().is4xxClientError())
                        .andExpect(jsonPath("$.message").value(ErrorCode.PAYMENT_PERMISSION_DENY.getMessage()));
            }
        }

    }

    @Nested
    @DisplayName("Describe : GET /api/payments")
    class Describe_getPaymentSearch{

        @BeforeEach
        void setUp(){
            SecurityContextHolder.getContext().setAuthentication(token);
            for(int i = 0 ; i < 10 ; i ++){
                PaymentRequestBody paymentRequestBody = new PaymentRequestBody(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        testOrder.getOrderNumber().toString(),
                        Long.valueOf(testOrder.getTotalPrice())
                );
                paymentService.initPaymentByOrder(
                        paymentRequestBody
                );
            }
        }

        @Nested
        @DisplayName("Context : 올바른 데이터가 주어지는 경우")
        class Context_with_Valid_data{

            @Test
            @DisplayName("It : 정상적으로 결제 다건 조회 성공")
            void It_결제_조회_완료() throws Exception {

                // when
                mockMvc.perform(
                                MockMvcRequestBuilders.get(BASE_URL)
                                        .param("page", "0")
                                        .param("size","5")
                        ).andDo(print())

                        // then
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message").value(SuccessCode.PAYMENT_READ_SUCCESS.getSuccessMessage()))
                        .andExpect(jsonPath("$.data.totalElements").value(10));
            }
        }

    }



}
