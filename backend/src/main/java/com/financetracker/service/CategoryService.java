package com.financetracker.service;

import com.financetracker.dto.CategoryDTO;
import com.financetracker.model.Category;
import com.financetracker.repository.CategoryRepository;
import com.financetracker.util.EntityMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final EntityMapper entityMapper;
    
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories() {
        log.debug("Fetching all categories");
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();
        return entityMapper.toCategoryDTOList(categories);
    }
    
    @Transactional(readOnly = true)
    public CategoryDTO getCategoryById(Long id) {
        log.debug("Fetching category with id: {}", id);
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return entityMapper.toCategoryDTO(category);
    }
    
    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        log.info("Creating category: {}", dto.getName());
        
        if (categoryRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Category already exists with name: " + dto.getName());
        }
        
        Category category = Category.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .icon(dto.getIcon())
            .color(dto.getColor())
            .build();
        
        Category saved = categoryRepository.save(category);
        log.info("Category created with id: {}", saved.getId());
        
        return entityMapper.toCategoryDTO(saved);
    }
    
    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        log.info("Updating category with id: {}", id);
        
        Category existing = categoryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        
        if (!existing.getName().equals(dto.getName()) && 
            categoryRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Category already exists with name: " + dto.getName());
        }
        
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setIcon(dto.getIcon());
        existing.setColor(dto.getColor());
        
        Category updated = categoryRepository.save(existing);
        log.info("Category updated: {}", id);
        
        return entityMapper.toCategoryDTO(updated);
    }
    
    @Transactional
    public void deleteCategory(Long id) {
        log.info("Deleting category with id: {}", id);
        
        if (!categoryRepository.existsById(id)) {
            throw new RuntimeException("Category not found with id: " + id);
        }
        
        categoryRepository.deleteById(id);
        log.info("Category deleted: {}", id);
    }
}
