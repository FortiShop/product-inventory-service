package org.fortishop.productinventoryservice.Repository;

import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long>,
        ProductSearchRepositoryCustom {
}
