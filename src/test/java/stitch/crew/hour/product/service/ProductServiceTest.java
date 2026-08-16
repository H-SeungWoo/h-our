package stitch.crew.hour.product.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.Null;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import stitch.crew.hour.category.domain.Category;
import stitch.crew.hour.category.repository.CategoryRepository;
import stitch.crew.hour.common.exception.BusinessException;
import stitch.crew.hour.common.exception.ErrorCode;
import stitch.crew.hour.product.constant.ProductStatus;
import stitch.crew.hour.product.domain.Product;
import stitch.crew.hour.product.dto.*;
import stitch.crew.hour.product.repository.ProductRepository;
import stitch.crew.hour.user.constant.Gender;
import stitch.crew.hour.user.constant.Role;
import stitch.crew.hour.user.domain.CurrentUser;
import stitch.crew.hour.user.domain.User;
import stitch.crew.hour.user.repository.UserRepository;
import stitch.crew.hour.util.TestUtil;

import java.time.LocalDate;
import java.util.List;


@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired
    EntityManager em;

    @Autowired
    ProductService service;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    User testUser;
    TestingAuthenticationToken token;
    Category testCategory;
    Product testProduct;
    @Autowired
    private ProductService productService;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(
                new User(
                        "이름",
                        "wjdtn747@naver.com",
                        "1234",
                        LocalDate.now(),
                        Role.ADMIN,
                        Gender.MALE,
                        "010",
                        "?",
                        "대한민국",
                        false,
                        false
                )
        );

        token = new TestingAuthenticationToken(
                CurrentUser.from(testUser),
                null,
                Role.ADMIN.getValue()
        );

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
    }

    @Nested
    @DisplayName("Describe : createProduct()에")
    class Describe_createProduct {

        @Nested
        @DisplayName("Context : 올바른 데이터가 주어진 경우")
        class Context_with_Valid_Data {
            @Test
            @DisplayName("It : 상품을 정상적으로 생성")
            void It_상품_생성__성공() {
                // given
                testUser.changeRole(Role.ADMIN);
                SecurityContextHolder.getContext().setAuthentication(token);
                ProductCreateRequest request = TestUtil.productCreateRequest(
                        "요약",
                        "설명",
                        testCategory.getId()
                );

                // when
                ProductCreateResponse product = service.createProduct(
                        testUser.getId(),
                        null,
                        request
                );

                // then
                Assertions.assertThat(product.productName()).isEqualTo(request.name());
                Assertions.assertThat(product.price()).isEqualTo(request.price());
            }
        }

        @Nested
        @DisplayName("Context : 적합한 권한이 없는 경우")
        class Context_with_InValid_Authorities {
            @Test
            @DisplayName("It : 어드민이 아닌 경우 상품 생성 실패")
            void It_상품_생성__실패() {
                // given
                testUser.changeRole(Role.USER);
                token = new TestingAuthenticationToken(
                        CurrentUser.from(testUser),
                        null,
                        Role.USER.getValue()
                );

                SecurityContextHolder.getContext().setAuthentication(token);
                ProductCreateRequest request = TestUtil.productCreateRequest(
                        "요약",
                        "설명",
                        testCategory.getId()
                );

                // when
                Assertions.assertThatThrownBy(
                                () -> service.createProduct(
                                        testUser.getId(),
                                        null,
                                        request
                                )
                        ).isInstanceOf(BusinessException.class)
                        .hasMessageContaining(ErrorCode.NOT_ADMIN.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe : getProductDetail()에")
    class Describe_getProductDetail {

        @Nested
        @DisplayName("Context : 올바른 데이터가 주어진 경우")
        class Context_with_Valid_Data {

            @Test
            @DisplayName("It : 상품 단건 조회 성공")
            void It_상품_단건조회__성공() {
                // given
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                ProductDetailsResponse founded = productService.getProductDetail(testProduct.getId());

                // then
                Assertions.assertThat(founded.name()).isEqualTo(founded.name());
            }

        }

    }

    @Nested
    @DisplayName("Describe : getProductSearch()에")
    class Describe_getAllProduct {

        @Nested
        @DisplayName("Context : 올바른 데이터가 주어진 경우")
        class Context_with_Valid_Data {

            @Test
            @DisplayName("It : 카테고리가 주어진 경우 상품을 모두 성공적으로 조회")
            void It_상품_모두_조회__성공() {
                // given
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                Page<ProductSearchResponse> founded = productService.getProductSearch(
                        PageRequest.of(0, 20),
                        testCategory.getName()
                );

                // then
                Assertions.assertThat(founded.getContent().size()).isEqualTo(1);
                Assertions.assertThat(founded.getTotalPages()).isEqualTo(1);
                Assertions.assertThat(founded.getContent().getFirst().productId()).isEqualTo(testProduct.getId());
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("It : 카테고리 입력 없어도 상품을 모두 성공적으로 조회")
            void It_상품_모두_조회__성공(String categoryName) {
                // given
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                Page<ProductSearchResponse> founded = productService.getProductSearch(
                        PageRequest.of(0, 20),
                        categoryName
                );

                // then
                Assertions.assertThat(founded.getContent().size()).isEqualTo(1);
                Assertions.assertThat(founded.getTotalPages()).isEqualTo(1);
                Assertions.assertThat(founded.getContent().getFirst().productId()).isEqualTo(testProduct.getId());
            }

            @Test
            @DisplayName("It : 상품 목록을 조회수 내림차순으로 조회")
            void It_상품_목록을_조회수순으로_조회() {
                // given
                testProduct.setViewCount(3L);
                Product highViewProduct = productRepository.save(
                        new Product(
                                "조회수 높은 상품",
                                2000L,
                                "상품요약",
                                "설명글",
                                testCategory
                        )
                );
                highViewProduct.setViewCount(10L);
                Product middleViewProduct = productRepository.save(
                        new Product(
                                "조회수 중간 상품",
                                2000L,
                                "상품요약",
                                "설명글",
                                testCategory
                        )
                );
                middleViewProduct.setViewCount(7L);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                Page<ProductSearchResponse> founded = productService.getProductSearch(
                        PageRequest.of(0, 20),
                        null
                );

                // then
                Assertions.assertThat(founded.getContent())
                        .extracting(ProductSearchResponse::productId)
                        .containsExactly(
                                highViewProduct.getId(),
                                middleViewProduct.getId(),
                                testProduct.getId()
                        );
            }

            @Test
            @DisplayName("It : 다른 카테고리인 경우 조회 차단")
            void It_상품_조회_없음() {
                // given
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                Page<ProductSearchResponse> founded = productService.getProductSearch(
                        PageRequest.of(0, 20),
                        "없는 카테고리"
                );

                // then
                Assertions.assertThat(founded.getContent().size()).isEqualTo(0);
            }

            @ParameterizedTest
            @EnumSource(
                    value = ProductStatus.class,
                    names = {"DEACTIVATED", "DELETED"},
                    mode = EnumSource.Mode.INCLUDE
            )
            @DisplayName("It : 삭제된 상품인 경우 조회 차단")
            void It_삭제된_상품_조회_없음(ProductStatus status) {
                // given
                testProduct.switchStatus(status);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                Page<ProductSearchResponse> founded = productService.getProductSearch(
                        PageRequest.of(0, 20),
                        ""
                );

                // then
                Assertions.assertThat(founded.getContent().size()).isEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("Describe : deleteProduct()에")
    class Describe_deleteProduct {

        @Nested
        @DisplayName("Context : 올바른 데이터가 주어진 경우")
        class Context_with_Valid_Data {

            @Test
            @DisplayName("It : 상품 삭제 성공 ( SOFT DELETE )")
            void It_상품_삭제__성공() {
                // given
                testUser.changeRole(Role.ADMIN);
                SecurityContextHolder.getContext().setAuthentication(token);

                productService.deleteProduct(
                        testUser.getId(),
                        testProduct.getId()
                );

                Product foundedProduct = productRepository.findByIdOrThrow(testProduct.getId());

                Assertions.assertThat(foundedProduct.getStatus()).isEqualTo(ProductStatus.DELETED);
            }
        }
    }

    @Nested
    @DisplayName("Describe : updateProduct()")
    class Describe_UpdateProduct {
        @Nested
        @DisplayName("Context : 올바른 데이터가 주어진 경우")
        class Context_with_Valid_Data {


            @Test
            @DisplayName("It : 상품 수정")
            void It_Product_수정__성공() {
                // given
                ProductUpdateRequest request = new ProductUpdateRequest(
                        "수정된 상품명",
                        2000L,
                        "요약",
                        "설명"
                );

                testUser.changeRole(Role.ADMIN);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                productService.updateProduct(
                        testUser.getId(),
                        testProduct.getId(),
                        null,
                        request
                );

                // then
                Product founded = productRepository.findByIdOrThrow(testProduct.getId());

                Assertions.assertThat(founded.getName()).isEqualTo(request.name());
                Assertions.assertThat(founded.getPrice()).isEqualTo(request.price());
                Assertions.assertThat(founded.getDescription()).isEqualTo(request.description());
            }

            @ParameterizedTest
            @NullAndEmptySource
            @DisplayName("It : 일부 상품 수정")
            void It_Product_일부_수정__성공(String description) {
                // given
                ProductUpdateRequest request = new ProductUpdateRequest(
                        "수정된 상품명",
                        null,
                        description,
                        description
                );
                testUser.changeRole(Role.ADMIN);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                productService.updateProduct(
                        testUser.getId(),
                        testProduct.getId(),
                        null,
                        request
                );

                // then
                Product founded = productRepository.findByIdOrThrow(testProduct.getId());

                Assertions.assertThat(founded.getName()).isEqualTo(request.name());
                Assertions.assertThat(founded.getDescription()).isEqualTo(testProduct.getDescription());
            }
        }

        @Nested
        @DisplayName("Context : 적합한 권한이 없는 경우")
        class Context_with_Invalid_Authroity{

            @Test
            @DisplayName("It : 관리자가 아니므로 상품 수정 차단")
            void It_상품_수정__차단(){
                // given
                testUser.changeRole(Role.USER);

                token = new TestingAuthenticationToken(
                        CurrentUser.from(testUser),
                        null,
                        Role.USER.getValue()
                );

                SecurityContextHolder.getContext().setAuthentication(token);
                ProductUpdateRequest request = new ProductUpdateRequest(
                        "수정된 상품명",
                        2000L,
                        "요약",
                        "설명"
                );

                // when
                Assertions.assertThatThrownBy(
                        ()-> productService.updateProduct(
                                testUser.getId(),
                                testProduct.getId(),
                                null,
                                request
                        )
                )
                        // then
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining(ErrorCode.NOT_ADMIN.getMessage());

            }
        }
    }

    @Nested
    @DisplayName("Describe : switchMain()에")
    class Describe_switchMain{

        @BeforeEach
        void setUp(){
            for(int i = 1 ; i <= 10 ; i++){
                Product save = productRepository.save(
                        new Product(
                                "테스트용 상품" + i,
                                2000L,
                                "상품요약" + i,
                                "설명글" + i,
                                testCategory
                        )
                );
                save.setMain();
            }
        }

        @Nested
        @DisplayName("Context : 올바른 권한을 주는 경우")
        class Context_with_Valid_Authority{

            @Test
            @DisplayName("It : 해당 Product를 Main으로 전환")
            void It_Product를_Main으로_전환(){
                // given
                testUser.changeRole(Role.ADMIN);
                SecurityContextHolder.getContext().setAuthentication(token);

                // when
                productService.switchToMain(
                        testUser.getId(),
                        testProduct.getId()
                );

                // then
                List<Product> mainProducts = productRepository.getMainProducts(testCategory.getId());
                Assertions.assertThat(mainProducts.size()).isEqualTo(10);

                Product foundedProduct = productRepository.findByIdOrThrow(testProduct.getId());
                Assertions.assertThat(foundedProduct.getIsMain()).isEqualTo(true);
            }
        }

    }

    @Nested
    @DisplayName("Context : 적합한 권한이 없는 경우")
    class Context_with_Invalid_Authroity{

        @Test
        @DisplayName("It : 관리자가 아니므로 상품 수정 차단")
        void It_상품_Main_전환__차단(){
            // given
            testUser.changeRole(Role.USER);
            token = new TestingAuthenticationToken(
                    CurrentUser.from(testUser),
                    null,
                    Role.USER.getValue()
            );

            SecurityContextHolder.getContext().setAuthentication(token);
            Product product = productRepository.save(
                    new Product(
                            "테스트용 상품",
                            2000L,
                            "상품요약",
                            "설명글",
                            testCategory
                    )
            );
            testProduct.setMain();

            // when
            Assertions.assertThatThrownBy(
                            ()-> productService.switchToMain(
                                    testUser.getId(),
                                    product.getId()
                            )
                    )
                    // then
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.NOT_ADMIN.getMessage());

        }
    }
}
