package com.adidas.dto.request;

import com.adidas.entity.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestDTO {
    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;
    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;
    @NotBlank(message = "Slug không được để trống")
    private String slug;

    private String description;
    @NotNull
    private BigDecimal price;
    private Integer stockQuantity;
    private String sku;
    private ProductStatus status;

    private String mainImageUrl;
    private List<String> imageUrls;
    private List<ProductVariantRequestDTO> variants;

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public String getSku() {
        return sku;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public String getMainImageUrl() {
        return mainImageUrl;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public List<ProductVariantRequestDTO> getVariants() {
        return variants;
    }
}
