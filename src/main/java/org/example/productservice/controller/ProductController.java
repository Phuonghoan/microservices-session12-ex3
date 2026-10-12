package org.example.productservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.example.productservice.dto.ProductRequestDTO;
import org.example.productservice.dto.ProductResponseDTO;
import org.example.productservice.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.productservice.service.FlashSaleService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final FlashSaleService flashSaleService;

    // POST /api/v1/products - Tạo sản phẩm
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestBody ProductRequestDTO request
    ) {
        return ResponseEntity.status(201)
                .body(productService.createProduct(request));
    }

    // GET /api/v1/products/{id} - Lấy chi tiết từ Redis / PostgreSQL
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    // GET /api/v1/products - Lấy danh sách
    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // PUT /api/v1/products/{id} - Cập nhật tên sản phẩm và xóa cache
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return ResponseEntity.ok(
                productService.updateProduct(id, request.name())
        );
    }

    @PutMapping("/{id}/decrease-stock")
    public ResponseEntity<Void> decreaseStock(
            @PathVariable Long id,
            @RequestBody DecreaseStockRequest request
    ) {

        flashSaleService.sellProduct(
                id,
                request.quantity()
        );

        return ResponseEntity.noContent().build();
    }

    public record DecreaseStockRequest(Integer quantity) {
    }

    public record UpdateProductRequest(
            @NotBlank(message = "Tên sản phẩm không được để trống")
            String name
    ) {
    }
}
