package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.cart.CartInfoVo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderTradeVo;

import java.util.List;
import java.util.Map;

public interface ActivityService {

    //根据skuId获取促销与优惠券信息
    Map<String, Object> findActivityAndCoupon(Long skuId, Long userId);

    //获取满足条件的促销与优惠券信息
    List<CartInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId);

    //获取交易满足条件的促销与优惠券信息
    OrderTradeVo findTradeActivityAndCoupon(List<OrderDetail> orderDetailList, Long userId);
}
