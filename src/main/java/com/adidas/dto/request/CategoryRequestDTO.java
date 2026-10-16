package com.adidas.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequestDTO {
    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;
    private String description;
    private Long parentId;
}
