package org.fortishop.productinventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.fortishop.productinventoryservice.Repository.ProductRepository;
import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.dto.request.ProductRequest;
import org.fortishop.productinventoryservice.dto.response.ProductResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @InjectMocks
    private ProductServiceImpl productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private static final String PRODUCT_VIEW_KEY = "product:views";

    @Test
    @DisplayName("상품 생성 성공")
    void createProduct_success() {
        ProductRequest request = new ProductRequest("name", "desc", BigDecimal.TEN, "cat", "url", true);

        Product saved = Product.builder()
                .name("name")
                .description("desc")
                .price(BigDecimal.TEN)
                .category("cat")
                .imageUrl("url")
                .isActive(true)
                .build();

        given(productRepository.save(any())).willReturn(saved);

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getName()).isEqualTo("name");
        verify(productRepository).save(any());
    }

    @Test
    @DisplayName("상품 조회 시 Redis 조회수 증가")
    void getProduct_success() {
        Product product = Product.builder().name("item").isActive(true).build();
        ReflectionTestUtils.setField(product, "id", 1L);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);

        ProductResponse result = productService.getProduct(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(zSetOps).incrementScore(PRODUCT_VIEW_KEY, "1", 1.0);
    }

    @Test
    @DisplayName("상품 수정 성공")
    void updateProduct_success() {
        Product product = Product.builder().name("old").isActive(true).build();
        ReflectionTestUtils.setField(product, "id", 1L);
        ProductRequest request = new ProductRequest("new", "desc", BigDecimal.ONE, "cat", "url", true);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        ProductResponse result = productService.updateProduct(1L, request);

        assertThat(result.getName()).isEqualTo("new");
        verify(productRepository).findById(1L);
    }

    @Test
    @DisplayName("상품 삭제 성공")
    void deleteProduct_success() {
        given(productRepository.existsById(1L)).willReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("상품 삭제 실패 - 존재하지 않음")
    void deleteProduct_fail() {
        given(productRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> productService.deleteProduct(1L))
                .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("전체 상품 조회 - 페이징")
    void getProducts_success() {
        Page<Product> productPage = new PageImpl<>(List.of(
                Product.builder().name("A").build()
        ));

        ReflectionTestUtils.setField(productPage.getContent().get(0), "id", 1L);

        given(productRepository.findAllByIsActiveTrue(any(Pageable.class)))
                .willReturn(productPage);

        Page<ProductResponse> result = productService.getProducts(0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("A");
    }

    @Test
    @DisplayName("인기 상품 조회 - Redis → DB → 정렬 유지")
    void getPopularProducts_success() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
        given(zSetOps.reverseRange(eq("product:views"), anyLong(), anyLong()))
                .willReturn(Set.of("1", "2"));

        Product p1 = Product.builder().name("p1").build();
        Product p2 = Product.builder().name("p2").build();
        ReflectionTestUtils.setField(p1, "id", 1L);
        ReflectionTestUtils.setField(p2, "id", 2L);

        given(productRepository.findAllById(anyList()))
                .willReturn(List.of(p1, p2));

        List<ProductResponse> result = productService.getPopularProducts(2);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(ProductResponse::getId)).contains(1L, 2L);
    }

}
