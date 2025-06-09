package org.fortishop.productinventoryservice.Repository;

import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductSearchRepositoryCustom {
    Page<ProductDocument> search(String keyword, Pageable pageable);
}
