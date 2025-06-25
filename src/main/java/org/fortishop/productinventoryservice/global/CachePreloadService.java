package org.fortishop.productinventoryservice.global;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.Repository.ProductRepository;
import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.dto.response.ProductResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CachePreloadService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PRODUCT_VIEW_KEY = "product:views";
    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product::detail::";

    @Scheduled(cron = "0 */5 * * * *")
    public void preloadPopularProducts() {
        Set<Object> rawIds = redisTemplate.opsForZSet().reverseRange(PRODUCT_VIEW_KEY, 0, 9);
        if (rawIds == null || rawIds.isEmpty()) {
            return;
        }

        List<Long> productIds = rawIds.stream()
                .map(String::valueOf)
                .map(Long::parseLong)
                .toList();

        List<Product> products = productRepository.findAllById(productIds);

        for (Product product : products) {
            redisTemplate.opsForValue().set(
                    PRODUCT_DETAIL_KEY_PREFIX + product.getId(),
                    ProductResponse.of(product),
                    Duration.ofMinutes(10)
            );
        }
    }
}
