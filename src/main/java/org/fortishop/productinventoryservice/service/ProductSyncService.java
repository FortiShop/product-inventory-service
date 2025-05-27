package org.fortishop.productinventoryservice.service;

import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.dto.request.ProductRequest;

public interface ProductSyncService {
    void index(Product product);

    void update(Long productId, ProductRequest request);

    void delete(Long productId);
}
