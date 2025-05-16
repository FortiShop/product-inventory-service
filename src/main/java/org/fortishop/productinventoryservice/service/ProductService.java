package org.fortishop.productinventoryservice.service;

import java.util.List;
import org.fortishop.productinventoryservice.request.ProductRequest;
import org.fortishop.productinventoryservice.response.ProductResponse;
import org.springframework.data.domain.Page;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);

    ProductResponse getProduct(Long id);

    Page<ProductResponse> getProducts(int page, int size);
    
    List<ProductResponse> getPopularProducts(int limit);
}
