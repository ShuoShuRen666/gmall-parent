package com.atguigu.gmall.cart.client;

import com.atguigu.gmall.cart.client.impl.CartDegradeFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;


@FeignClient(value = "service-cart",fallback = CartDegradeFeignClient.class)
public interface CartFeignClient {

    /**
     * 添加购物车
     * @param skuId
     * @param skuNum
     * @return
     */
    @PostMapping("api/cart/addToCart/{skuId}/{skuNum}")
    Result addToCart(@PathVariable Long skuId,@PathVariable Integer skuNum);

    /**
     * 根据用户id查询已选中购物车列表
     * @param userId
     * @return
     */
    @GetMapping("api/cart/getCartCheckedList/{userId}")
    List<CartInfo> getCartCheckedList(@PathVariable String userId);

    /**
     * 通过userId查询购物车，并放入缓存
     *
     * @param userId
     * @return
     */
    @GetMapping("api/cart/loadCartCache/{userId}")
    Result loadCartCache(@PathVariable String userId);
}
