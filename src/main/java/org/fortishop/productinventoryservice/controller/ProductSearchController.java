package org.fortishop.productinventoryservice.controller;

import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.Repository.ProductSearchRepository;
import org.fortishop.productinventoryservice.domain.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductSearchController {
    private final ProductSearchRepository searchRepository;

    @GetMapping("/search")
    public Page<ProductDocument> search(@RequestParam(name = "keyword") String keyword,
                                        @PageableDefault Pageable pageable) {
        return searchRepository.findByNameContainingOrDescriptionContaining(keyword, keyword, pageable);
    }
}
