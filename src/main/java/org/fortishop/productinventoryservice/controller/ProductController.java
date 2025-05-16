package org.fortishop.productinventoryservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.dto.request.ProductRequest;
import org.fortishop.productinventoryservice.dto.response.ProductResponse;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.fortishop.productinventoryservice.exception.Product.ProductExceptionType;
import org.fortishop.productinventoryservice.global.Responder;
import org.fortishop.productinventoryservice.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private void validateAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-ROLE");
        if (!ADMIN_ROLE.equals(role)) {
            throw new ProductException(ProductExceptionType.UNAUTHORIZED_USER);
        }
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid ProductRequest request,
            HttpServletRequest httpRequest
    ) {
        validateAdmin(httpRequest);
        ProductResponse response = productService.createProduct(request);
        return Responder.success(response, HttpStatus.CREATED);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable(name = "productId") Long productId,
            @RequestBody @Valid ProductRequest request,
            HttpServletRequest httpRequest
    ) {
        validateAdmin(httpRequest);
        return Responder.success(productService.updateProduct(productId, request));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable(name = "productId") Long productId,
            HttpServletRequest httpRequest
    ) {
        validateAdmin(httpRequest);
        productService.deleteProduct(productId);
        return Responder.success(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable(name = "productId") Long productId) {
        return Responder.success(productService.getProduct(productId));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return Responder.success(productService.getProducts(page, size));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<ProductResponse>> getPopularProducts(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return Responder.success(productService.getPopularProducts(limit));
    }
}

