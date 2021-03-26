package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

public interface CartService {

    /**
     * 添加购物车
     * @param skuId   商品id
     * @param userId  用户id
     * @param skuNum  商品数量
     */
    void addToCart(Long skuId,String userId,Integer skuNum);

    /**
     * 通过用户id 查询购物车列表
     * @param userId  用户已登录id
     * @param userTempId  用户未登录id  临时id
     * @return
     */
    List<CartInfo> getCartList(String userId,String userTempId);

    /**
     * 获取已登录用户 或 临时用户 购物车
     * @param userId
     * @return
     */
    List<CartInfo> getCartList(String userId);

    /**
     * 更新选中状态
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId,Integer isChecked,Long skuId);

    /**
     * 删除购物车
     * @param skuId
     * @param userId
     */
    void deleteCart(Long skuId,String userId);

    /**
     * 根据用户Id 查询已选中购物车列表
     *
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);

    /**
     * 通过userId查询购物车，并放入缓存
     *
     * @param userId
     * @return
     */
    List<CartInfo> loadCartCache(String userId);
}
