package stitch.crew.hour.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.logging.log4j.util.Strings;
import stitch.crew.hour.image.service.ImageService;
import stitch.crew.hour.product.constant.ProductStatus;
import stitch.crew.hour.product.domain.Product;
import stitch.crew.hour.product.dto.AdminProductDetailResponse;
import stitch.crew.hour.product.dto.AdminProductSearchResponse;
import stitch.crew.hour.product.dto.ProductStatusUpdateRequest;
import stitch.crew.hour.product.repository.ProductRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAdminService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "viewCount");

    private final ProductRepository productRepository;
    private final ImageService imageService;

    public Page<AdminProductSearchResponse> getProducts(
            int page,
            int size,
            String keyword,
            String categoryName,
            ProductStatus status,
            Boolean isMain
    ) {
        return productRepository.getAdminProducts(
                PageRequest.of(page, size, DEFAULT_SORT),
                keyword,
                categoryName,
                status,
                isMain
        ).map(response -> new AdminProductSearchResponse(
                response.productId(),
                response.name(),
                response.price(),
                createThumbnailUrl(response.thumbnail()),
                response.status(),
                response.categoryName(),
                response.isMain(),
                response.viewCount(),
                response.salesCount(),
                response.createdAt()
        ));
    }

    public AdminProductDetailResponse getProduct(Long productId) {
        Product product = productRepository.findByIdOrThrow(productId);
        return AdminProductDetailResponse.from(product, createThumbnailUrl(product.getThumbnail()));
    }

    @Transactional
    public void updateStatus(
            Long productId,
            ProductStatusUpdateRequest request
    ) {
        Product product = productRepository.findByIdOrThrow(productId);
        product.switchStatus(request.status());
    }

    private String createThumbnailUrl(String thumbnail) {
        if (Strings.isBlank(thumbnail)) {
            return null;
        }
        if (thumbnail.startsWith("http://") || thumbnail.startsWith("https://") || thumbnail.startsWith("/")) {
            return thumbnail;
        }
        return imageService.getPresignedUrl(thumbnail);
    }
}
