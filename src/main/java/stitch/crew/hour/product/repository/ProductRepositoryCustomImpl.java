package stitch.crew.hour.product.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import stitch.crew.hour.category.domain.QCategory;
import stitch.crew.hour.product.constant.ProductStatus;
import stitch.crew.hour.product.domain.Product;
import stitch.crew.hour.product.domain.QProduct;
import stitch.crew.hour.product.dto.AdminProductSearchResponse;
import stitch.crew.hour.product.dto.ProductSearchResponse;
import stitch.crew.hour.product.dto.QProductSearchResponse;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    private final QProduct qProduct = QProduct.product;
    private final QCategory qCategory = QCategory.category;

    @Override
    public Page<ProductSearchResponse> getAllProduct(
            Pageable pageable,
            String categoryName
    ) {
        BooleanBuilder booleanBuilder = new BooleanBuilder()
                .and(containsCategoryName(categoryName))
                .and(qProduct.status.ne(ProductStatus.DELETED))
                .and(qProduct.status.ne(ProductStatus.DEACTIVATED));

        List<ProductSearchResponse> founded = jpaQueryFactory.select(
                        new QProductSearchResponse(
                                qProduct.id,
                                qProduct.name,
                                qProduct.price,
                                qProduct.thumbnail,
                                qProduct.status.stringValue(),
                                qProduct.summary,
                                qProduct.category.name,
                                qProduct.isMain,
                                qProduct.viewCount,
                                qProduct.salesCount
                        )
                ).from(qProduct)
                .where(booleanBuilder)
                .orderBy(qProduct.viewCount.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long totalCnt = jpaQueryFactory.select(qProduct.count())
                .from(qProduct)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<ProductSearchResponse>(
                founded,
                pageable,
                totalCnt == null ? 0L : totalCnt
        );
    }

    @Override
    public Page<AdminProductSearchResponse> getAdminProducts(
            Pageable pageable,
            String keyword,
            String categoryName,
            ProductStatus status,
            Boolean isMain
    ) {
        BooleanBuilder booleanBuilder = new BooleanBuilder()
                .and(containsProductName(keyword))
                .and(containsCategoryName(categoryName))
                .and(eqStatusOrExcludeDeleted(status))
                .and(eqIsMain(isMain));

        List<Product> products = jpaQueryFactory.selectFrom(qProduct)
                .join(qProduct.category, qCategory)
                .fetchJoin()
                .where(booleanBuilder)
                .orderBy(qProduct.viewCount.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long totalCnt = jpaQueryFactory.select(qProduct.count())
                .from(qProduct)
                .join(qProduct.category, qCategory)
                .where(booleanBuilder)
                .fetchOne();

        return new PageImpl<>(
                products.stream()
                        .map(AdminProductSearchResponse::from)
                        .toList(),
                pageable,
                totalCnt == null ? 0L : totalCnt
        );
    }

    @Override
    public List<Product> getMainProducts(Long categoryId) {

        return jpaQueryFactory.selectFrom(qProduct)
                .join(qCategory)
                .on(qProduct.category.id.eq(qCategory.id))
                .fetchJoin()
                .where(
                        qCategory.id.eq(categoryId)
                                .and(qProduct.isMain.eq(true))
                )
                .fetch();
    }

    public BooleanExpression containsCategoryName(String categoryName){
        return (Strings.isNotBlank(categoryName))?
                qProduct.category.name.containsIgnoreCase(categoryName)
                : null;
    }

    private BooleanExpression containsProductName(String keyword) {
        return Strings.isNotBlank(keyword)
                ? qProduct.name.containsIgnoreCase(keyword)
                : null;
    }

    private BooleanExpression eqStatusOrExcludeDeleted(ProductStatus status) {
        return status == null
                ? qProduct.status.ne(ProductStatus.DELETED)
                : qProduct.status.eq(status);
    }

    private BooleanExpression eqIsMain(Boolean isMain) {
        return isMain == null
                ? null
                : qProduct.isMain.eq(isMain);
    }

}
