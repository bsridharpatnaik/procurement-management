package com.sungroup.procurement.service;

import com.sungroup.procurement.constants.ProjectConstants;
import com.sungroup.procurement.dto.request.FilterDataList;
import com.sungroup.procurement.dto.response.ApiResponse;
import com.sungroup.procurement.dto.response.PaginationResponse;
import com.sungroup.procurement.entity.Material;
import com.sungroup.procurement.exception.DuplicateEntityException;
import com.sungroup.procurement.exception.EntityNotFoundException;
import com.sungroup.procurement.exception.ValidationException;
import com.sungroup.procurement.repository.MaterialRepository;
import com.sungroup.procurement.specification.MaterialSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final FilterService filterService;

    // READ Operations
    public ApiResponse<List<Material>> findMaterialsWithFilters(FilterDataList filterData, Pageable pageable) {
        try {
            Specification<Material> spec = buildMaterialSpecification(filterData);
            Page<Material> materialPage = materialRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(materialPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materialPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error fetching materials with filters", e);
            return ApiResponse.error("Failed to fetch materials: " + e.getMessage());
        }
    }

    public ApiResponse<Material> findById(Long id) {
        try {
            Material material = materialRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, material);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching material by id: {}", id, e);
            return ApiResponse.error("Failed to fetch material");
        }
    }

    public ApiResponse<Material> findByName(String name) {
        try {
            Material material = materialRepository.findByNameAndIsDeletedFalse(name)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, material);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error fetching material by name: {}", name, e);
            return ApiResponse.error("Failed to fetch material");
        }
    }

    public ApiResponse<List<String>> getAllUnits() {
        try {
            List<String> units = materialRepository.findAllDistinctUnits();
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, units);
        } catch (Exception e) {
            log.error("Error fetching material units", e);
            return ApiResponse.error("Failed to fetch material units");
        }
    }

    public ApiResponse<List<Material>> findByImportFromChina(Boolean importFromChina) {
        try {
            Specification<Material> spec = MaterialSpecification.isNotDeleted()
                    .and(MaterialSpecification.isImportFromChina(importFromChina));

            List<Material> materials = materialRepository.findAll(spec);
            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materials);
        } catch (Exception e) {
            log.error("Error fetching materials by import flag", e);
            return ApiResponse.error("Failed to fetch materials");
        }
    }

    public ApiResponse<List<Material>> searchMaterials(String keyword, Pageable pageable) {
        try {
            Specification<Material> spec = MaterialSpecification.isNotDeleted()
                    .and(MaterialSpecification.searchByKeyword(keyword));

            Page<Material> materialPage = materialRepository.findAll(spec, pageable);
            PaginationResponse pagination = PaginationResponse.from(materialPage);

            return ApiResponse.success(ProjectConstants.DATA_FETCHED_SUCCESS, materialPage.getContent(), pagination);
        } catch (Exception e) {
            log.error("Error searching materials with keyword: {}", keyword, e);
            return ApiResponse.error("Failed to search materials");
        }
    }

    // CREATE Operations
    @Transactional
    public ApiResponse<Material> createMaterial(Material material) {
        try {
            validateMaterialForCreate(material);

            Material savedMaterial = materialRepository.save(material);
            log.info("Material created successfully: {}", savedMaterial.getName());

            return ApiResponse.success(ProjectConstants.DATA_CREATED_SUCCESS, savedMaterial);
        } catch (DuplicateEntityException | ValidationException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating material", e);
            return ApiResponse.error("Failed to create material");
        }
    }

    // UPDATE Operations
    @Transactional
    public ApiResponse<Material> updateMaterial(Long id, Material materialDetails) {
        try {
            Material existingMaterial = materialRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            validateMaterialForUpdate(materialDetails, existingMaterial);

            // Update fields
            if (materialDetails.getName() != null) {
                existingMaterial.setName(materialDetails.getName());
            }
            if (materialDetails.getUnit() != null) {
                existingMaterial.setUnit(materialDetails.getUnit());
            }
            if (materialDetails.getImportFromChina() != null) {
                existingMaterial.setImportFromChina(materialDetails.getImportFromChina());
            }

            Material updatedMaterial = materialRepository.save(existingMaterial);
            log.info("Material updated successfully: {}", updatedMaterial.getName());

            return ApiResponse.success(ProjectConstants.DATA_UPDATED_SUCCESS, updatedMaterial);
        } catch (EntityNotFoundException | ValidationException | DuplicateEntityException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating material with id: {}", id, e);
            return ApiResponse.error("Failed to update material");
        }
    }

    // DELETE Operations
    @Transactional
    public ApiResponse<String> deleteMaterial(Long id) {
        try {
            Material material = materialRepository.findByIdActive(id)
                    .orElseThrow(() -> new EntityNotFoundException(ProjectConstants.MATERIAL_NOT_FOUND));

            // Soft delete
            material.setIsDeleted(true);
            materialRepository.save(material);

            log.info("Material soft deleted successfully: {}", material.getName());
            return ApiResponse.success(ProjectConstants.DATA_DELETED_SUCCESS, "Material deleted successfully");
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting material with id: {}", id, e);
            return ApiResponse.error("Failed to delete material");
        }
    }

    // UTILITY Methods
    private Specification<Material> buildMaterialSpecification(FilterDataList filterData) {
        Specification<Material> spec = MaterialSpecification.isNotDeleted();

        if (filterData == null || filterData.getFilterData() == null) {
            return spec;
        }

        String name = filterService.getStringValue(filterData, "name");
        String unit = filterService.getStringValue(filterData, "unit");
        List<String> units = filterService.getStringValues(filterData, "units");
        Boolean importFromChina = filterService.getBooleanValue(filterData, "importFromChina");
        List<Long> ids = filterService.getLongValues(filterData, "ids");
        String createdBy = filterService.getStringValue(filterData, "createdBy");
        String keyword = filterService.getStringValue(filterData, "keyword");

        FilterService.DateRange createdDateRange = filterService.getDateRange(filterData, "startDate", "endDate");

        if (name != null) {
            spec = spec.and(MaterialSpecification.hasName(name));
        }
        if (unit != null) {
            spec = spec.and(MaterialSpecification.hasUnit(unit));
        }
        if (units != null && !units.isEmpty()) {
            spec = spec.and(MaterialSpecification.hasUnits(units));
        }
        if (importFromChina != null) {
            spec = spec.and(MaterialSpecification.isImportFromChina(importFromChina));
        }
        if (ids != null && !ids.isEmpty()) {
            spec = spec.and(MaterialSpecification.hasIds(ids));
        }
        if (createdBy != null) {
            spec = spec.and(MaterialSpecification.createdBy(createdBy));
        }
        if (keyword != null) {
            spec = spec.and(MaterialSpecification.searchByKeyword(keyword));
        }
        if (createdDateRange != null) {
            spec = spec.and(MaterialSpecification.createdBetween(
                    createdDateRange.getStartDate(), createdDateRange.getEndDate()));
        }

        return spec;
    }

    private void validateMaterialForCreate(Material material) {
        if (material.getName() == null || material.getName().trim().isEmpty()) {
            throw new ValidationException("Material name is required");
        }

        // Check for duplicates
        if (materialRepository.existsByNameAndIsDeletedFalse(material.getName())) {
            throw new DuplicateEntityException("Material name already exists");
        }
    }

    private void validateMaterialForUpdate(Material materialDetails, Material existingMaterial) {
        if (materialDetails.getName() != null && !materialDetails.getName().equals(existingMaterial.getName())) {
            if (materialRepository.existsByNameAndIsDeletedFalse(materialDetails.getName())) {
                throw new DuplicateEntityException("Material name already exists");
            }
        }
    }
}