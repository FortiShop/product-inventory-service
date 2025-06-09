package org.fortishop.productinventoryservice.Repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductSearchRepositoryImpl implements ProductSearchRepositoryCustom {

    private final ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public Page<ProductDocument> search(String keyword, Pageable pageable) {
        Criteria criteria = new Criteria("name").matches(keyword)
                .or(new Criteria("description").matches(keyword));

        CriteriaQuery query = new CriteriaQuery(criteria, pageable);

        SearchHits<ProductDocument> searchHits =
                elasticsearchTemplate.search(query, ProductDocument.class);

        List<ProductDocument> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }
}
