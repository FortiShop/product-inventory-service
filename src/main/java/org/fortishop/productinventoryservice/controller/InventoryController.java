package org.fortishop.productinventoryservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.fortishop.productinventoryservice.exception.Product.ProductException;
import org.fortishop.productinventoryservice.exception.Product.ProductExceptionType;
import org.fortishop.productinventoryservice.global.Responder;
import org.fortishop.productinventoryservice.request.InventoryRequest;
import org.fortishop.productinventoryservice.response.InventoryResponse;
import org.fortishop.productinventoryservice.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private void validateAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-ROLE");
        if (!ADMIN_ROLE.equals(role)) {
            throw new ProductException(ProductExceptionType.UNAUTHORIZED_USER);
        }
    }

    @GetMapping("/inventory/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable(name = "productId") Long productId) {
        return Responder.success(inventoryService.getInventory(productId));
    }

    @PutMapping("/inventory/{productId}")
    public ResponseEntity<InventoryResponse> setInventory(
            @PathVariable(name = "productId") Long productId,
            @RequestBody @Valid InventoryRequest request,
            HttpServletRequest httpRequest
    ) {
        validateAdmin(httpRequest);
        return Responder.success(inventoryService.setInventory(productId, request));
    }
}
