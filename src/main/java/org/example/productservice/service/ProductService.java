package org.example.productservice.service;

import lombok.RequiredArgsConstructor;
import org.example.productservice.dto.ProductRequestDTO;
import org.example.productservice.dto.ProductResponseDTO;
import org.example.productservice.entity.Product;
import org.example.productservice.exception.ResourceNotFoundException;
import org.example.productservice.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // POST /api/v1/products - Tạo sản phẩm
    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {
        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .build();

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    // GET /api/v1/products/{id} - Redis Cache theo ID
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#p0")
    public ProductResponseDTO getProductById(Long id) {
        // Chỉ xuất hiện khi Redis Cache MISS
        System.out.println(">>> ĐANG TRUY VẤN POSTGRESQL - PRODUCT ID: " + id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sản phẩm với ID " + id + " không tồn tại!"
                ));

        return mapToResponse(product);
    }

    // GET /api/v1/products - Lấy danh sách (không cache theo đề)
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // PUT /api/v1/products/{id} - Cập nhật tên và xóa cache theo ID
    @Transactional
    @CacheEvict(value = "products", key = "#p0")
    public ProductResponseDTO updateProduct(Long id, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tên sản phẩm không được để trống"
            );
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sản phẩm với ID " + id + " không tồn tại!"
                ));

        product.setName(newName);
        Product updatedProduct = productRepository.save(product);

        System.out.println(">>> ĐÃ CẬP NHẬT PRODUCT ID: " + id);
        return mapToResponse(updatedProduct);
    }

    @Transactional
    @CacheEvict(value = "products", key = "#p0")
    public void decreaseStock(Long productId, Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng phải lớn hơn 0"
            );
        }

        int updated = productRepository.decreaseStock(
                productId,
                quantity
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm không tồn tại hoặc không đủ tồn kho"
            );
        }

        System.out.println(
                "Đã trừ " + quantity
                        + " sản phẩm ID " + productId
        );
    }

    // Entity -> DTO, không trả trực tiếp JPA Entity ra API
    private ProductResponseDTO mapToResponse(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity()
        );
    }
}
