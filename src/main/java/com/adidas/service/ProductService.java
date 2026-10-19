package com.adidas.service;

import com.adidas.dto.request.ProductRequestDTO;
import com.adidas.dto.request.ProductVariantRequestDTO;
import com.adidas.dto.response.ProductResponseDTO;
import com.adidas.dto.response.ProductVariantResponseDTO;
import com.adidas.entity.*;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.ICategoryRepository;
import com.adidas.repository.IProductImageRepository;
import com.adidas.repository.IProductRepository;
import com.adidas.repository.IProductVariantRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService implements IProductService{

    private IProductRepository productRepository;
    private ICategoryRepository categoryRepository;
    private IProductImageRepository productImageRepository;
    private IProductVariantRepository productVariantRepository;
    private final ModelMapper modelMapper;

    public ProductService(ModelMapper modelMapper, IProductRepository productRepository, ICategoryRepository categoryRepository, IProductImageRepository productImageRepository, IProductVariantRepository productVariantRepository) {
        this.modelMapper = modelMapper;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productImageRepository = productImageRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO) {
//      kiểm tra sự trùng lặp của slug và sku
        if (productRepository.existsBySlug(productRequestDTO.getSlug())) {
            throw new BadRequestException("Đường dẫn của sản phẩm đã tồn tại " + productRequestDTO.getSlug());
        } else if (productRequestDTO.getSku() != null && productRepository.existsBySku(productRequestDTO.getSku())) {
            throw new BadRequestException("Mã SKU của sản phẩm đã tồn tại " + productRequestDTO.getSku());
        }

//      kiểm tra danh mục sản phẩm có tồn tại hay không
        Category category = categoryRepository.findById(productRequestDTO.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục sản phẩm có id là: " + productRequestDTO.getCategoryId()));

//      Map DTO -> Entity và lưu vào bẳng Product
        Products products = modelMapper.map(productRequestDTO, Products.class);

        products.setCategory(category);
        if (products.getStatus() == null) {
            products.setStatus(ProductStatus.ACTIVE);
        }
        Products saveProduct = productRepository.save(products);

//      Lưu hình ảnh vào bảng product_image
        List<ProductImage> imagesToSave = new ArrayList<>();
        if (productRequestDTO.getMainImageUrl() != null && !productRequestDTO.getMainImageUrl().isBlank()) {
            ProductImage mainImage = new ProductImage();
            mainImage.setProduct(saveProduct);
            mainImage.setImageUrl(productRequestDTO.getMainImageUrl());
            mainImage.setMain(true);
            imagesToSave.add(mainImage);
        }
        if (productRequestDTO.getImageUrls() != null && !productRequestDTO.getImageUrls().isEmpty()) {
            for (String url : productRequestDTO.getImageUrls()) {
                ProductImage galleryImg = new ProductImage();
                galleryImg.setProduct(saveProduct);
                galleryImg.setImageUrl(url);
                galleryImg.setMain(false);
                imagesToSave.add(galleryImg);
            }
        }
        if (!imagesToSave.isEmpty()) {
            productImageRepository.saveAll(imagesToSave);
        }

//      Lưu các bến thể của sản phẩm vào bảng product_variants
        List<ProductVariant> imgVariantsToSave = new ArrayList<>();
        if (productRequestDTO.getVariants() != null && !productRequestDTO.getVariants().isEmpty()) {
            for (ProductVariantRequestDTO variantRequestDTO : productRequestDTO.getVariants()) {
                if (productVariantRepository.existsBySku(variantRequestDTO.getSku())) {
                    throw new BadRequestException("Mã SKU biến thể cua sản phẩm đã tồn tại" + variantRequestDTO.getSku());
                }
                ProductVariant variant = modelMapper.map(variantRequestDTO, ProductVariant.class);
                variant.setProduct(saveProduct);

                if (variant.getPrice() == null) {
                    variant.setPrice(saveProduct.getPrice());
                }
                if (variant.getStockQuantity() == null) {
                    variant.setStockQuantity(saveProduct.getStockQuantity());
                }
                imgVariantsToSave.add(variant);
            }
            productVariantRepository.saveAll(imgVariantsToSave);
        }

//      Map Entity -> DTO trả cho frontend
        ProductResponseDTO productResponseDTO = modelMapper.map(saveProduct, ProductResponseDTO.class);
        productResponseDTO.setCategoryId(category.getId());
        productResponseDTO.setCategoryName(category.getName());
        productResponseDTO.setMainImageUrl(productRequestDTO.getMainImageUrl());
        productResponseDTO.setImageUrls(productRequestDTO.getImageUrls());

//      Lấy danh sách DTO của các biến thể
        if(!imgVariantsToSave.isEmpty()) {
            List<ProductVariantResponseDTO> variantResponseDTOs = imgVariantsToSave.stream().map(v -> modelMapper.map(v, ProductVariantResponseDTO.class)).collect(Collectors.toList());
            productResponseDTO.setVariants(variantResponseDTOs);
        }
        return productResponseDTO;
    }

    @Override
    public ProductResponseDTO getProductById(Long id) {
        Products product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm có Id: " + id));
        ProductResponseDTO productResponseDTO = modelMapper.map(product, ProductResponseDTO.class);

        if (product.getCategory() != null) {
            productResponseDTO.setCategoryId(product.getCategory().getId());
            productResponseDTO.setCategoryName(product.getCategory().getName());
        }

        List<ProductImage> images = productImageRepository.findByProductId(id);
        List<String> galleryUrls = new ArrayList<>();
        for (ProductImage img : images) {
            if (Boolean.TRUE.equals(img.getMain())) {
                productResponseDTO.setMainImageUrl(img.getImageUrl());
            } else {
                galleryUrls.add(img.getImageUrl());
            }
        }
        productResponseDTO.setImageUrls(galleryUrls);
//        productResponseDTO.setImageUrls(images.stream().map(ProductImage::getImageUrl).collect(Collectors.toList()));

        List<ProductVariant> productVariants = productVariantRepository.findByProductId(id);
        productResponseDTO.setVariants(productVariants.stream().map(v -> modelMapper.map(v, ProductVariantResponseDTO.class)).collect(Collectors.toList()));

        return productResponseDTO;
    }

    @Override
    public Page<ProductResponseDTO> getAllProducts(int page, int size, String keyword, Long categoryId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Products> productsPage;

        if (keyword != null && !keyword.isBlank()) {
            productsPage = productRepository.findByNameContainingIgnoreCase(keyword, pageable);
        } else if (categoryId != null) {
            productsPage = productRepository.findByCategoryId(categoryId, pageable);
        } else {
            productsPage = productRepository.findAll(pageable);
        }

//        chuyển đổi Page<Product> -> DTO
        return productsPage.map(products -> {
            ProductResponseDTO productResponseDTO = modelMapper.map(products, ProductResponseDTO.class);
            if (products.getCategory() != null) {
                productResponseDTO.setCategoryId(products.getCategory().getId());
                productResponseDTO.setCategoryName(products.getCategory().getName());
            }

            List<ProductImage> productImages = productImageRepository.findByProductId(products.getId());
            List<String> galleryUrls = new ArrayList<>();
            for (ProductImage img : productImages) {
                if (Boolean.TRUE.equals(img.getMain())) {
                    productResponseDTO.setMainImageUrl(img.getImageUrl());
                } else {
                    galleryUrls.add(img.getImageUrl());
                }
            }
            productResponseDTO.setImageUrls(galleryUrls);

            List<ProductVariant> variants = productVariantRepository.findByProductId(products.getId());
            productResponseDTO.setVariants(variants.stream()
                    .map(v -> modelMapper.map(v, ProductVariantResponseDTO.class))
                    .collect(Collectors.toList()));

            return productResponseDTO;
        });
    }

    @Override
    @Transactional
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO productRequestDTO) {
        Products product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm có id là: " + id));
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khng tìm thấy danh mục sản phẩm có id là: " + productRequestDTO.getCategoryId()));

        // cập nhật thông tin
        product.setName(productRequestDTO.getName());
        product.setSlug(productRequestDTO.getSlug());
        product.setDescription(productRequestDTO.getDescription());
        product.setPrice(productRequestDTO.getPrice());
        product.setStockQuantity(productRequestDTO.getStockQuantity());
        product.setSku(productRequestDTO.getSku());
        if (productRequestDTO.getStatus() != null) {
            product.setStatus(productRequestDTO.getStatus());
        }
        product.setCategory(category);

        Products savedProduct = productRepository.save(product);

        // xóa dữ liệu cũ trước khi thêm
        productImageRepository.deleteByProductId(id);
        productVariantRepository.deleteByProductId(id);

        productImageRepository.flush();
        productVariantRepository.flush();

        // lưu ảnh mới
        List<ProductImage> productImages = new ArrayList<>();
        if (productRequestDTO.getMainImageUrl() != null && !productRequestDTO.getMainImageUrl().isBlank()) {
            ProductImage mainImage = new ProductImage();
            mainImage.setProduct(savedProduct);
            mainImage.setImageUrl(productRequestDTO.getMainImageUrl());
            mainImage.setMain(true);
            productImages.add(mainImage);
        }
        if (productRequestDTO.getImageUrls() != null && !productRequestDTO.getImageUrls().isEmpty()) {
            for (String url : productRequestDTO.getImageUrls()) {
                ProductImage images = new ProductImage();
                images.setProduct(savedProduct);
                images.setImageUrl(url);
                images.setMain(false);
                productImages.add(images);
            }
        }
        if (!productImages.isEmpty()) {
            productImageRepository.saveAll(productImages);
        }

        // lưu các biến thể mới
        List<ProductVariant> productVariants = new ArrayList<>();
        if (productRequestDTO.getVariants() != null && !productRequestDTO.getVariants().isEmpty()) {
            for (ProductVariantRequestDTO variantsRequestDTO : productRequestDTO.getVariants()) {
                ProductVariant variant = modelMapper.map(variantsRequestDTO, ProductVariant.class);
                variant.setProduct(savedProduct);
                if (variant.getPrice() == null) {
                    variant.setPrice(savedProduct.getPrice());
                }
                if (variant.getStockQuantity() == null) {
                    variant.setStockQuantity(savedProduct.getStockQuantity());
                }
                productVariants.add(variant);
            }
            productVariantRepository.saveAll(productVariants);
        }
        return getProductById(id);
    }

    @Override
    @Transactional
    public void deleteByProductId(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm có id là: " + id);
        }
        productImageRepository.deleteByProductId(id);
        productVariantRepository.deleteByProductId(id);

        productImageRepository.flush();
        productVariantRepository.flush();

        productRepository.deleteById(id);
    }
}
