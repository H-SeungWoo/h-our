package stitch.crew.hour.product.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import stitch.crew.hour.category.domain.Category;
import stitch.crew.hour.category.repository.CategoryRepository;
import stitch.crew.hour.common.exception.BusinessException;
import stitch.crew.hour.common.exception.ErrorCode;
import stitch.crew.hour.product.constant.ProductStatus;
import stitch.crew.hour.product.domain.Product;
import stitch.crew.hour.product.dto.AdminProductDetailResponse;
import stitch.crew.hour.product.dto.AdminProductSearchResponse;
import stitch.crew.hour.product.dto.ProductStatusUpdateRequest;
import stitch.crew.hour.product.repository.ProductRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@DisplayName("ProductAdminService의")
class ProductAdminServiceTest {

    @Autowired
    ProductAdminService productAdminService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    EntityManager entityManager;

    Category testCategory;
    Category subCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(
                new Category("가방")
        );
        subCategory = categoryRepository.save(
                new Category("신발")
        );
    }

    @Nested
    @DisplayName("Describe : getProducts()는")
    class Describe_getProducts {

        @Nested
        @DisplayName("Context : 필터 조건이 없는 경우")
        class Context_without_filter {

            @Test
            @DisplayName("It : 상품 목록을 조회수 내림차순으로 조회")
            void It_상품_목록을_조회수순으로_조회() {
                // given
                Product firstProduct = saveProduct("첫번째 가방", ProductStatus.ACTIVATED, testCategory, todayAt(9), 3L);
                Product thirdProduct = saveProduct("세번째 가방", ProductStatus.SOLD_OUT, testCategory, todayAt(10), 10L);
                Product secondProduct = saveProduct("두번째 가방", ProductStatus.DEACTIVATED, testCategory, todayAt(11), 7L);

                // when
                Page<AdminProductSearchResponse> response = productAdminService.getProducts(
                        0,
                        20,
                        null,
                        null,
                        null,
                        null
                );

                // then
                Assertions.assertThat(response.getContent())
                        .extracting(AdminProductSearchResponse::productId)
                        .containsSubsequence(
                                thirdProduct.getId(),
                                secondProduct.getId(),
                                firstProduct.getId()
                        );
            }

            @Test
            @DisplayName("It : 삭제된 상품을 제외하고 조회")
            void It_삭제된_상품을_제외하고_조회() {
                // given
                saveProduct("활성 상품", ProductStatus.ACTIVATED, testCategory, todayAt(9));
                saveProduct("삭제 상품", ProductStatus.DELETED, testCategory, todayAt(10));

                // when
                Page<AdminProductSearchResponse> response = productAdminService.getProducts(
                        0,
                        20,
                        null,
                        null,
                        null,
                        null
                );

                // then
                Assertions.assertThat(response.getTotalElements()).isEqualTo(1L);
                Assertions.assertThat(response.getContent().getFirst().status()).isEqualTo(ProductStatus.ACTIVATED.name());
            }
        }

        @Nested
        @DisplayName("Context : DELETED 상태가 주어진 경우")
        class Context_with_deleted_status {

            @Test
            @DisplayName("It : 삭제된 상품만 조회")
            void It_삭제된_상품만_조회() {
                // given
                saveProduct("활성 상품", ProductStatus.ACTIVATED, testCategory, todayAt(9));
                saveProduct("삭제 상품", ProductStatus.DELETED, testCategory, todayAt(10));

                // when
                Page<AdminProductSearchResponse> response = productAdminService.getProducts(
                        0,
                        20,
                        null,
                        null,
                        ProductStatus.DELETED,
                        null
                );

                // then
                Assertions.assertThat(response.getTotalElements()).isEqualTo(1L);
                Assertions.assertThat(response.getContent().getFirst().status()).isEqualTo(ProductStatus.DELETED.name());
            }
        }

        @Nested
        @DisplayName("Context : keyword가 주어진 경우")
        class Context_with_keyword {

            @Test
            @DisplayName("It : 상품명으로 상품을 조회")
            void It_상품명으로_상품을_조회() {
                // given
                saveProduct("여행 가방", ProductStatus.ACTIVATED, testCategory, todayAt(9));
                saveProduct("운동화", ProductStatus.ACTIVATED, subCategory, todayAt(10));

                // when
                Page<AdminProductSearchResponse> response = productAdminService.getProducts(
                        0,
                        20,
                        "가방",
                        null,
                        null,
                        null
                );

                // then
                Assertions.assertThat(response.getTotalElements()).isEqualTo(1L);
                Assertions.assertThat(response.getContent().getFirst().name()).isEqualTo("여행 가방");
            }
        }

        @Nested
        @DisplayName("Context : categoryName이 주어진 경우")
        class Context_with_category_name {

            @Test
            @DisplayName("It : 카테고리명으로 상품을 조회")
            void It_카테고리명으로_상품을_조회() {
                // given
                saveProduct("여행 가방", ProductStatus.ACTIVATED, testCategory, todayAt(9));
                saveProduct("운동화", ProductStatus.ACTIVATED, subCategory, todayAt(10));

                // when
                Page<AdminProductSearchResponse> response = productAdminService.getProducts(
                        0,
                        20,
                        null,
                        "가방",
                        null,
                        null
                );

                // then
                Assertions.assertThat(response.getTotalElements()).isEqualTo(1L);
                Assertions.assertThat(response.getContent().getFirst().categoryName()).isEqualTo(testCategory.getName());
            }
        }

        @Nested
        @DisplayName("Context : isMain=true가 주어진 경우")
        class Context_with_is_main {

            @Test
            @DisplayName("It : 메인 상품만 조회")
            void It_메인_상품만_조회() {
                // given
                Product mainProduct = saveProduct("메인 상품", ProductStatus.ACTIVATED, testCategory, todayAt(9));
                productRepository.findByIdOrThrow(mainProduct.getId()).setMain();
                saveProduct("일반 상품", ProductStatus.ACTIVATED, testCategory, todayAt(10));

                // when
                Page<AdminProductSearchResponse> response = productAdminService.getProducts(
                        0,
                        20,
                        null,
                        null,
                        null,
                        true
                );

                // then
                Assertions.assertThat(response.getTotalElements()).isEqualTo(1L);
                Assertions.assertThat(response.getContent().getFirst().isMain()).isTrue();
                Assertions.assertThat(response.getContent().getFirst().productId()).isEqualTo(mainProduct.getId());
            }
        }
    }

    @Nested
    @DisplayName("Describe : getProduct()는")
    class Describe_getProduct {

        @Nested
        @DisplayName("Context : 비활성, 품절, 삭제 상품이 주어진 경우")
        class Context_with_hidden_status {

            @Test
            @DisplayName("It : 상품 상태와 관계없이 상세를 조회")
            void It_상품상태와_관계없이_상세를_조회() {
                // given
                Product deactivatedProduct = saveProduct("비활성 상품", ProductStatus.DEACTIVATED, testCategory, todayAt(9));
                Product soldOutProduct = saveProduct("품절 상품", ProductStatus.SOLD_OUT, testCategory, todayAt(10));
                Product deletedProduct = saveProduct("삭제 상품", ProductStatus.DELETED, testCategory, todayAt(11));

                // when
                AdminProductDetailResponse deactivatedResponse = productAdminService.getProduct(deactivatedProduct.getId());
                AdminProductDetailResponse soldOutResponse = productAdminService.getProduct(soldOutProduct.getId());
                AdminProductDetailResponse deletedResponse = productAdminService.getProduct(deletedProduct.getId());

                // then
                Assertions.assertThat(deactivatedResponse.status()).isEqualTo(ProductStatus.DEACTIVATED.name());
                Assertions.assertThat(soldOutResponse.status()).isEqualTo(ProductStatus.SOLD_OUT.name());
                Assertions.assertThat(deletedResponse.status()).isEqualTo(ProductStatus.DELETED.name());
            }
        }

        @Nested
        @DisplayName("Context : 존재하지 않는 상품 ID가 주어진 경우")
        class Context_with_not_existing_product_id {

            @Test
            @DisplayName("It : PRODUCT_NOT_FOUND 예외가 발생")
            void It_PRODUCT_NOT_FOUND_예외_발생() {
                // when
                BusinessException exception = assertThrows(
                        BusinessException.class,
                        () -> productAdminService.getProduct(Long.MAX_VALUE)
                );

                // then
                Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
            }
        }
    }

    @Nested
    @DisplayName("Describe : updateStatus()는")
    class Describe_updateStatus {

        @Nested
        @DisplayName("Context : 올바른 상품 ID와 상태가 주어진 경우")
        class Context_with_valid_data {

            @Test
            @DisplayName("It : 상품 상태를 변경")
            void It_상품상태를_변경() {
                // given
                Product product = saveProduct("상태 변경 상품", ProductStatus.ACTIVATED, testCategory, todayAt(9));
                ProductStatusUpdateRequest request = new ProductStatusUpdateRequest(ProductStatus.SOLD_OUT);

                // when
                productAdminService.updateStatus(
                        product.getId(),
                        request
                );
                entityManager.flush();
                entityManager.clear();

                // then
                Product foundedProduct = productRepository.findByIdOrThrow(product.getId());
                Assertions.assertThat(foundedProduct.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
            }
        }
    }

    private Product saveProduct(
            String name,
            ProductStatus status,
            Category category,
            LocalDateTime createdAt
    ) {
        Product product = productRepository.save(
                new Product(
                        name,
                        2_000L,
                        "요약",
                        "설명",
                        category
                )
        );
        product.switchStatus(status);
        setProductCreatedAt(product, createdAt);

        return product;
    }

    private Product saveProduct(
            String name,
            ProductStatus status,
            Category category,
            LocalDateTime createdAt,
            Long viewCount
    ) {
        Product product = productRepository.save(
                new Product(
                        name,
                        2_000L,
                        "요약",
                        "설명",
                        category
                )
        );
        product.switchStatus(status);
        product.setViewCount(viewCount);
        setProductCreatedAt(product, createdAt);

        return product;
    }

    private void setProductCreatedAt(Product product, LocalDateTime createdAt) {
        entityManager.flush();

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaUpdate<Product> update = criteriaBuilder.createCriteriaUpdate(Product.class);
        Root<Product> root = update.from(Product.class);
        Path<LocalDateTime> createdAtPath = root.get("createdAt");
        Path<Long> idPath = root.get("id");

        update.set(createdAtPath, createdAt);
        update.where(criteriaBuilder.equal(idPath, product.getId()));

        entityManager.createQuery(update).executeUpdate();
        entityManager.clear();
    }

    private LocalDateTime todayAt(int hour) {
        return LocalDate.now().atTime(hour, 0);
    }
}
