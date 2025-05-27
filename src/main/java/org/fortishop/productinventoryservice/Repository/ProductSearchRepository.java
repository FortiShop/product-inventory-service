package org.fortishop.productinventoryservice.Repository;

import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {

    Page<ProductDocument> findByNameContainingOrDescriptionContaining(String name, String desc, Pageable pageable);

}
