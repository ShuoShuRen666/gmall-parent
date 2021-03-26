package com.atguigu.gmall.activity.client.impl;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.cart.CartInfoVo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderTradeVo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class ActivityDegradeFeignClient implements ActivityFeignClient {
    @Override
    public Result findAll() {
        return Result.fail();
    }

    @Override
    public Result getSeckillGoods(Long skuId) {
        return Result.fail();
    }

    @Override
    public Result trade() {
        return Result.fail();
    }

    @Override
    public List<CartInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId) {
        List<CartInfoVo> cartInfoVoList = new ArrayList<>();
        CartInfoVo cartInfoVo = new CartInfoVo();
        cartInfoVo.setCartInfoList(cartInfoList);
        cartInfoVo.setActivityRuleList(null);
        cartInfoVoList.add(cartInfoVo);
        return cartInfoVoList;
    }

    //获取下单交易满足条件的促销与优惠券信息  如果远程调用失败，给用户返回一个没有优惠信息的页面
    @Override
    public OrderTradeVo findTradeActivityAndCoupon(List<OrderDetail> orderDetailList, Long userId) {
        OrderTradeVo orderTradeVo = new OrderTradeVo();
        OrderDetailVo orderDetailVo = new OrderDetailVo();
        orderDetailVo.setOrderDetailList(orderDetailList);
        orderDetailVo.setActivityRule(null);
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        orderDetailVoList.add(orderDetailVo);

        orderTradeVo.setCouponInfoList(null);
        orderTradeVo.setOrderDetailVoList(orderDetailVoList);
        orderTradeVo.setActivityReduceAmount(new BigDecimal(0));
        return orderTradeVo;
    }

    @Override
    public Boolean updateCouponInfoUseStatus(Long couponId, Long userId, Long orderId) {
        return null;
    }
}
