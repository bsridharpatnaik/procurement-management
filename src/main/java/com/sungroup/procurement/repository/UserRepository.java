package com.sungroup.procurement.repository;

import com.sungroup.procurement.entity.User;
import com.sungroup.procurement.entity.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // Basic finder methods
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndIsDeletedFalse(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Role-based queries
    List<User> findByRoleAndIsDeletedFalse(UserRole role);

    List<User> findByRoleInAndIsDeletedFalse(List<UserRole> roles);

    // Factory-related queries
    @Query("SELECT u FROM User u JOIN u.assignedFactories f WHERE f.id = :factoryId AND u.isDeleted = false")
    List<User> findByFactoryId(@Param("factoryId") Long factoryId);

    @Query("SELECT u FROM User u JOIN u.assignedFactories f WHERE f.id IN :factoryIds AND u.isDeleted = false")
    List<User> findByFactoryIds(@Param("factoryIds") List<Long> factoryIds);

    // Active users
    List<User> findByIsActiveAndIsDeletedFalse(Boolean isActive);

    // Complex queries with specifications (inherited from JpaSpecificationExecutor)
    // These methods are automatically available:
    // - findAll(Specification<User> spec)
    // - findAll(Specification<User> spec, Pageable pageable)
    // - findAll(Specification<User> spec, Sort sort)
    // - findOne(Specification<User> spec)
    // - count(Specification<User> spec)

    // Custom query examples (if needed for performance)
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND " +
            "(:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
            "(:fullName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :fullName, '%'))) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:isActive IS NULL OR u.isActive = :isActive)")
    Page<User> findUsersWithFilters(@Param("username") String username,
                                    @Param("fullName") String fullName,
                                    @Param("role") UserRole role,
                                    @Param("isActive") Boolean isActive,
                                    Pageable pageable);

    // For factory dependency check
    List<User> findByAssignedFactoriesIdAndIsDeletedFalse(Long factoryId);
    boolean existsByAssignedFactoriesIdAndIsDeletedFalse(Long factoryId);
    long countByRoleAndIsDeletedFalseAndIsActiveTrue(UserRole role);
    long countByRoleAndIsDeletedFalse(UserRole role);
}