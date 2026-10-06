package com.pickleball.category.controller;

import com.pickleball.category.entity.Category;
import com.pickleball.category.repository.CategoryRepository;
import com.pickleball.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) { this.categories = categories; }

    @GetMapping("/categories")
    public List<Category> publicCategories() { return categories.findByActiveTrueOrderBySortOrderAsc(); }

    @GetMapping("/admin/categories")
    public List<Category> adminCategories() { return categories.findAll(); }

    @PostMapping("/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@Valid @RequestBody CategoryRequest request) {
        return categories.save(new Category(request.name(), request.slug(), request.sortOrder()));
    }

    @PatchMapping("/admin/categories/{id}")
    public Category update(@PathVariable("id") Long id, @Valid @RequestBody CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));
        category.update(request.name(), request.sortOrder(), request.active());
        return categories.save(category);
    }

    public record CategoryRequest(@NotBlank String name, @NotBlank String slug,
                                  int sortOrder, boolean active) {}
}
