package org.fortishop.productinventoryservice.global;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.productinventoryservice.Repository.ProductRepository;
import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.dto.response.ProductResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCacheLoader implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PRODUCT_VIEW_KEY = "product:views";
    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product::detail::";

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 애플리케이션 시작 시 인기 상품 캐시 Preload 시작");

        Set<Object> rawIds = redisTemplate.opsForZSet().reverseRange(PRODUCT_VIEW_KEY, 0, 9);
        if (rawIds == null || rawIds.isEmpty()) {
            log.info("⚠️ 조회수 기반 인기 상품이 존재하지 않아 preload 생략됨");
            return;
        }

        List<Long> productIds = rawIds.stream()
                .map(String::valueOf)
                .map(Long::parseLong)
                .collect(Collectors.toList());

        List<Product> products = productRepository.findAllById(productIds);

        for (Product product : products) {
            String cacheKey = PRODUCT_DETAIL_KEY_PREFIX + product.getId();
            ProductResponse response = ProductResponse.of(product);
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(10));
            log.info("✅ 캐시 preload 완료: {}", cacheKey);
        }

        log.info("인기 상품 캐시 preload 완료");
    }
}
