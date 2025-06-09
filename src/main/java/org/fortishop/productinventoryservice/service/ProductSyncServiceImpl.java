package org.fortishop.productinventoryservice.service;

import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.Repository.ProductSearchRepository;
import org.fortishop.productinventoryservice.domain.Product;
import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.fortishop.productinventoryservice.dto.request.ProductRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductSyncServiceImpl implements ProductSyncService {
    private final ProductSearchRepository searchRepository;

    @Override
    public void index(Product product) {
        ProductDocument doc = ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .quantity(0)
                .category(product.getCategory())
                .build();
        searchRepository.save(doc);
    }

    @Override
    public void update(Long productId, ProductRequest request) {
        ProductDocument existing = searchRepository.findById(productId).orElseThrow();

        ProductDocument updated = ProductDocument.builder()
                .id(productId)
                .name(request.getName() != null ? request.getName() : existing.getName())
                .description(request.getDescription() != null ? request.getDescription() : existing.getDescription())
                .price(request.getPrice() != null ? request.getPrice() : existing.getPrice())
                .quantity(existing.getQuantity())
                .category(request.getCategory() != null ? request.getCategory() : existing.getCategory())
                .build();

        searchRepository.save(updated);
    }

    @Override
    public void updateQuantity(Long productId, Integer quantity) {
        searchRepository.findById(productId).ifPresent(doc -> {
            doc.setQuantity(quantity);
            searchRepository.save(doc);
        });
    }

    @Override
    public void delete(Long productId) {
        searchRepository.deleteById(productId);
    }
}
