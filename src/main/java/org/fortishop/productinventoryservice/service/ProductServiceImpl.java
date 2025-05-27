package org.fortishop.productinventoryservice.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.Repository.InventoryRepository;
import org.fortishop.productinventoryservice.Repository.ProductRepository;
import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.dto.request.ProductRequest;
import org.fortishop.productinventoryservice.dto.response.ProductResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.fortishop.productinventoryservice.exception.Product.ProductExceptionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    private final InventoryRepository inventoryRepository;

    private final ProductSyncService productSyncService;

    private final RedisTemplate<String, String> redisTemplate;

    private static final String PRODUCT_VIEW_KEY = "product:views";

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .isActive(request.isActive())
                .build();
        productSyncService.index(product);
        return ProductResponse.of(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));
        product.update(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getCategory(),
                request.getImageUrl(),
                request.isActive()
        );
        productSyncService.update(id, request);
        return ProductResponse.of(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND);
        }
        if (inventoryRepository.findByProductId(id).isPresent()) {
            inventoryRepository.deleteById(id);
        }
        productRepository.deleteById(id);
        productSyncService.delete(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException(ProductExceptionType.PRODUCT_NOT_FOUND));

        redisTemplate.opsForZSet().incrementScore(PRODUCT_VIEW_KEY, id.toString(), 1);

        return ProductResponse.of(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return productRepository.findAllByIsActiveTrue(pageable)
                .map(ProductResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getPopularProducts(int limit) {
        Set<String> idStrings = redisTemplate.opsForZSet()
                .reverseRange(PRODUCT_VIEW_KEY, 0, limit - 1);

        if (idStrings == null || idStrings.isEmpty()) {
            return List.of();
        }

        List<Long> ids = idStrings.stream().map(Long::valueOf).toList();
        List<Product> products = productRepository.findAllById(ids);

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(ProductResponse::of)
                .toList();
    }
}
