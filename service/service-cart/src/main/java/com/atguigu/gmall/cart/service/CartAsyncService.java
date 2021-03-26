package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

//同步操作redis，异步更新mysql
public interface CartAsyncService {

    /**
     * 异步修改购物车
     * @param cartInfo
     */
    void updateCartInfo(CartInfo cartInfo);

    /**
     * 异步保存购物车
     * @param cartInfo
     */
    void saveCartInfo(CartInfo cartInfo);

    /**
     * 异步删除购物车（清空临时数据）
     * @param userTempId
     */
    void deleteCartInfo(String userTempId);

    /**
     * 异步删除购物车
     * @param userId
     * @param skuId
     */
    void deleteCartInfo(String userId,Long skuId);

    /**
     * 选中状态变更
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId,Integer isChecked,Long skuId);
}
