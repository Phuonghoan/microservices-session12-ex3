package org.example.productservice.service;

import org.example.productservice.entity.Product;
import org.example.productservice.repository.ProductRepository;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

@Service
public class FlashSaleService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;

    public FlashSaleService(
            ProductRepository productRepository,
            RedissonClient redissonClient,
            CacheManager cacheManager,
            PlatformTransactionManager transactionManager
    ) {
        this.productRepository = productRepository;
        this.redissonClient = redissonClient;
        this.cacheManager = cacheManager;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public void sellProduct(Long productId, Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng mua phải lớn hơn 0"
            );
        }

        // Mỗi sản phẩm có một khóa riêng
        String lockKey = "lock:product:" + productId;

        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;

        try {

            // Chờ tối đa 10 giây, khóa tồn tại tối đa 30 giây
            locked = lock.tryLock(
                    10,
                    30,
                    TimeUnit.SECONDS
            );

            if (!locked) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Hệ thống đang bận, vui lòng thử lại"
                );
            }

            System.out.println("Đã lấy Lock: " + lockKey);

            // Transaction được commit khi vẫn giữ Redis Lock
            transactionTemplate.executeWithoutResult(status -> {

                Product product = productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Không tìm thấy sản phẩm ID: " + productId
                                )
                        );

                if (product.getStockQuantity() == null
                        || product.getStockQuantity() < quantity) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Sản phẩm đã hết hàng"
                    );
                }

                int updated = productRepository.decreaseStock(
                        productId,
                        quantity
                );

                if (updated == 0) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Sản phẩm đã hết hàng"
                    );
                }
            });

            // Database đã commit thành công
            // Xóa Redis Cache sản phẩm để tránh trả stock cũ
            Cache cache = cacheManager.getCache("products");

            if (cache != null) {
                cache.evict(productId);
            }

            System.out.println(
                    "Đã bán thành công sản phẩm ID: "
                            + productId
                            + " | Số lượng: "
                            + quantity
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Yêu cầu bị gián đoạn khi chờ Lock"
            );

        } finally {

            // Chỉ mở khóa nếu thread hiện tại vẫn sở hữu
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();

                System.out.println(
                        "Đã giải phóng Lock: " + lockKey
                );
            }
        }
    }
}
