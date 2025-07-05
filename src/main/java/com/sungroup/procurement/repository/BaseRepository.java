package com.sungroup.procurement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Base repository interface with common methods for all entities
 * This provides soft delete functionality and specification support
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Find all non-deleted entities
     */
    default List<T> findAllActive() {
        Specification<T> spec = (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
        return findAll(spec);
    }

    /**
     * Find all non-deleted entities with pagination
     */
    default Page<T> findAllActive(Pageable pageable) {
        Specification<T> spec = (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
        return findAll(spec, pageable);
    }

    /**
     * Find by ID excluding deleted entities
     */
    default Optional<T> findByIdActive(ID id) {
        Specification<T> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("id"), id),
                cb.equal(root.get("isDeleted"), false)
        );
        return findOne(spec);
    }

    /**
     * Count non-deleted entities
     */
    default long countActive() {
        Specification<T> spec = (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
        return count(spec);
    }

    /**
     * Soft delete by setting isDeleted = true
     */
    default void softDelete(T entity) {
        // This would need to be implemented in the service layer
        // as we can't directly modify entities in repository interfaces
    }
}