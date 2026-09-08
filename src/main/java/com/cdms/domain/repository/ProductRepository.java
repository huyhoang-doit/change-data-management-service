package com.cdms.domain.repository;

import com.cdms.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Product} entities.
 *
 * <p>Primary lookup pattern: by {@code (externalId, sku)} — the UNIQUE key pair.
 * This is the standard way CDMS resolves which product to update on each ingestion.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find a product by its Vietful external ID and SKU.
     * Used by {@code ChangeProcessingService} to determine if a product already exists.
     *
     * @param externalId Vietful internal product ID
     * @param sku        product SKU
     * @return the product if it exists
     */
    Optional<Product> findByExternalIdAndSku(String externalId, String sku);

    /**
     * Find a product by external ID only (when SKU is not available).
     *
     * @param externalId Vietful internal product ID
     * @return the product if it exists
     */
    Optional<Product> findByExternalId(String externalId);

    /**
     * Find a product by SKU only.
     *
     * @param sku product SKU
     * @return the product if it exists
     */
    Optional<Product> findBySku(String sku);

    /**
     * Check if a product exists by external ID and SKU.
     * Cheaper than {@link #findByExternalIdAndSku} — no entity hydration.
     */
    boolean existsByExternalIdAndSku(String externalId, String sku);

    /**
     * Count products by external ID and SKU — dùng trong Concurrency Test
     * để assert DB chỉ có đúng 1 product record sau 100 concurrent requests.
     */
    long countByExternalIdAndSku(String externalId, String sku);

    /**
     * Update product version atomically.
     * Used when bumping version after a successful change is applied.
     */
    @Modifying
    @Query("UPDATE Product p SET p.version = :version, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    int updateVersion(@Param("id") Long id, @Param("version") Long version);
}
