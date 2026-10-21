package com.adidas.service;

import com.adidas.dto.request.CartItemRequestDTO;
import com.adidas.dto.request.CartRequestDTO;
import com.adidas.dto.response.CartItemResponseDTO;
import com.adidas.dto.response.CartResponseDTO;
import com.adidas.entity.*;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService implements ICartService{

    private final ICartRepository cartRepository;

    private final IProductImageRepository productImageRepository;

    private final ICartItemRepository cartItemRepository;

    private final IProductRepository productRepository;

    private final IProductVariantRepository productVariantRepository;

    public CartService(ICartRepository cartRepository, IProductImageRepository productImageRepository, ICartItemRepository cartItemRepository, IProductRepository productRepository, IProductVariantRepository productVariantRepository) {
        this.cartRepository = cartRepository;
        this.productImageRepository = productImageRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDTO getCartByUserId(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);

        if (cart == null) {
            return CartResponseDTO.builder().userId(userId).totalPrice(BigDecimal.ZERO).cartItems(new ArrayList<>()).status("ACTIVE").build();
        }

        // xử lý Map dữ liệu từng mặt hàng
        List<CartItemResponseDTO> itemResponseDTOS = new ArrayList<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (CartItem item: cart.getCartItems()) {
            BigDecimal subTotal = item.getPrice().multiply(new BigDecimal(item.getQuantity()));
            calculatedTotal = calculatedTotal.add(subTotal);

            // Tìm ảnh chính
            String mainImageUrl = null;
            List<ProductImage> images = productImageRepository.findByProductId(item.getProduct().getId());
            for (ProductImage img : images) {
                if (Boolean.TRUE.equals(img.getMain())) {
                    mainImageUrl = img.getImageUrl();
                    break;
                }
            }

            // Đóng gói dữ liệu
            CartItemResponseDTO itemDTO = new CartItemResponseDTO(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getProduct().getSlug(),
                    mainImageUrl,
                    item.getProductVariant().getId(),
                    item.getProductVariant().getVariantName(),
                    item.getQuantity(),
                    item.getPrice(),
                    subTotal
            );
            itemResponseDTOS.add(itemDTO);
        }
        return CartResponseDTO.builder().id(cart.getId()).userId(cart.getUser().getId()).status(cart.getStatus().name()).totalPrice(calculatedTotal).cartItems(itemResponseDTOS).build();
    }

    @Override
    @Transactional
    public CartResponseDTO addToCart(Long userId, CartItemRequestDTO cartItemRequestDTO) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            cart = new Cart();
            cart.setTotalPrice(BigDecimal.ZERO);
            cart = cartRepository.save(cart);
        }

        // hiển thị sản phẩm và biến thể
        Products product = productRepository.findById(cartItemRequestDTO.getProductId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        ProductVariant productVariant = productVariantRepository.findById(cartItemRequestDTO.getProductId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân loại hàng"));

        // check biến thể này thuộc về sản phẩm kia
        if (!productVariant.getProduct().getId().equals(product.getId())) {
            throw new BadRequestException("Phân loại hàng không khớp với sản phẩm");
        }

        // xử lý sản phẩm trong giỏ (tăng số lượng hoặc thêm mới)
        CartItem cartItem = cartItemRepository.findByCartIdAndProductVariantId(cart.getId(), productVariant.getId()).orElse(null);

        if (cartItem != null) {
            int newQuantity = cartItem.getQuantity() + cartItemRequestDTO.getQuantity();
            if (newQuantity > productVariant.getStockQuantity()) {
                throw new BadRequestException("Số lượng sản phẩm vượt quá số lượng hàng hóa còn tồn tại trong kho");
            }
            cartItem.setQuantity(newQuantity);
        } else {
            if (cartItemRequestDTO.getQuantity() > productVariant.getStockQuantity()) {
                throw new BadRequestException("Số lượng sản phẩm vượt quá số lượng hàng hóa còn tồn tại trong kho");
            }

            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setProductVariant(productVariant);
            cartItem.setQuantity(cartItemRequestDTO.getQuantity());
            cartItem.setPrice(productVariant.getPrice());

            cartItemRepository.save(cartItem);
            cart.getCartItems().add(cartItem);
        }

        // Tính toán lại tổng tiền và lưu
        BigDecimal newTotal = BigDecimal.ZERO;
        for (CartItem item : cart.getCartItems()) {
            newTotal = newTotal.add(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
        }
        cart.setTotalPrice(newTotal);
        cartRepository.save(cart);

        return getCartByUserId(userId);
    }
}
