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

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    Optional<User> findByIdAndIsDeletedFalse(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // For factory dependency check
    List<User> findByAssignedFactoriesIdAndIsDeletedFalse(Long factoryId);

    long countByRoleAndIsDeletedFalseAndIsActiveTrue(UserRole role);

    long countByIsDeletedFalse();

    List<User> findByRoleAndIsDeletedFalseAndIsActiveTrue(UserRole role);
}