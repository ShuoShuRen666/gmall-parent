package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.activity.service.ActivityService;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.cart.CartInfoVo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderTradeVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private ActivityInfoService activityInfoService;

    @Autowired
    private CouponInfoService couponInfoService;

    //根据skuId获取促销与优惠券信息
    @Override
    public Map<String, Object> findActivityAndCoupon(Long skuId, Long userId) {
        // 一个sku只能有一个促销活动，一个活动有多个活动规则（如满赠，满100送10，满500送50）
        //根据skuId 获取当前商品促销活动的规则列表
        List<ActivityRule> activityRuleList = activityInfoService.findActivityRule(skuId);
        Long activityId = null;
        if (!CollectionUtils.isEmpty(activityRuleList)) {
            // 只有一个活动id
            activityId = activityRuleList.get(0).getActivityId();
        }
        //获取优惠券信息
        List<CouponInfo> couponInfoList = couponInfoService.findCouponInfo(skuId,activityId,userId);
        Map<String, Object> map = new HashMap<>();
        //前端页面需要的数据  907 908 行
        map.put("activityRuleList",activityRuleList);
        map.put("couponInfoList",couponInfoList);
        return map;
    }

    //获取购物车满足条件的促销与优惠券信息
    @Override
    public List<CartInfoVo> findCartActivityAndCoupon(List<CartInfo> cartInfoList, Long userId) {
        //需要调用活动 + 优惠券
        //声明一个参数 skuIdToActivityIdMap  map<skuId,activityId>
        Map<Long,Long> skuIdToActivityIdMap = new HashMap<>();
        //通过 findCartActivityRuleMap()方法调用之后，skuIdToActivityIdMap就有值了
        List<CartInfoVo> cartInfoVoList = activityInfoService.findCartActivityRuleMap(cartInfoList, skuIdToActivityIdMap);
        //获取skuId对应的优惠券列表集合
        Map<Long, List<CouponInfo>> skuIdToCouponInfoListMap = couponInfoService.findCartCouponInfo(cartInfoList, skuIdToActivityIdMap, userId);

        //处理没有参与活动的 促销列表规则
        //声明一个集合来记录当前没有活动的cartInfo
        List<CartInfo> noJoinCartInfoList = new ArrayList<>();
        //skuIdToActivityIdMap 是记录的 哪些skuId（商品） 是参与活动的
        for (CartInfo cartInfo : cartInfoList) {
            if(!skuIdToActivityIdMap.containsKey(cartInfo.getSkuId())){
                //如果不存在 说明没有参与活动
                noJoinCartInfoList.add(cartInfo);
            }
        }
        //处理没有参与活动的数据
        if(!CollectionUtils.isEmpty(noJoinCartInfoList)){
            //给cartInfoVo赋值
            CartInfoVo cartInfoVo = new CartInfoVo();
            cartInfoVo.setCartInfoList(noJoinCartInfoList);
            //没有参与活动  就没有活动规则
            cartInfoVo.setActivityRuleList(null);
            cartInfoVoList.add(cartInfoVo);
        }
        //处理参与活动的优惠券规则
        for (CartInfoVo cartInfoVo : cartInfoVoList) {
            List<CartInfo> cartInfoList1 = cartInfoVo.getCartInfoList();
            for (CartInfo cartInfo : cartInfoList1) {
                cartInfo.setCouponInfoList(skuIdToCouponInfoListMap.get(cartInfo.getSkuId()));
            }
        }
        return cartInfoVoList;
    }

    //获取交易满足条件的促销与优惠券信息
    @Override
    public OrderTradeVo findTradeActivityAndCoupon(List<OrderDetail> orderDetailList, Long userId) {
        //记录购物项activityId对应的最优促销活动规则  map<activityId,ActivityRule>
        Map<Long, ActivityRule> activityIdToActivityRuleMap = activityInfoService.findTradeActivityRuleMap(orderDetailList);
        // map<skuId,orderDetail>
        Map<Long, OrderDetail> skuIdToCartInfoMap = new HashMap<>();
        for (OrderDetail orderDetail : orderDetailList) {
            skuIdToCartInfoMap.put(orderDetail.getSkuId(),orderDetail);
        }

        //记录有活动的购物项sku
        List<Long> activitySkuId = new ArrayList<>();
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        //活动满减金额
        BigDecimal activityReduceAmount = new BigDecimal("0");
        //有活动的购物项
        if(!CollectionUtils.isEmpty(activityIdToActivityRuleMap)){
            Iterator<Map.Entry<Long, ActivityRule>> iterator = activityIdToActivityRuleMap.entrySet().iterator();
            //活动id 对应的最优规则
            while (iterator.hasNext()){
                Map.Entry<Long, ActivityRule> entry = iterator.next();
                //一个活动的最优规则
                ActivityRule activityRule = entry.getValue();
                //这个活动对应的skuId列表
                List<Long> skuIdList = activityRule.getSkuIdList();
                //存储当前活动的 订单详情
                List<OrderDetail> detailList = new ArrayList<>();
                for (Long skuId : skuIdList) {
                    detailList.add(skuIdToCartInfoMap.get(skuId));
                }
                //赋值当前活动满减金额
                activityReduceAmount = activityReduceAmount.add(activityRule.getReduceAmount());

                //给OrderDetailVo对象赋值
                OrderDetailVo orderDetailVo = new OrderDetailVo();
                orderDetailVo.setActivityRule(activityRule);
                orderDetailVo.setOrderDetailList(detailList);
                orderDetailVoList.add(orderDetailVo);

                //给有活动的skuId赋值
                activitySkuId.addAll(skuIdList);
            }
        }

        //没活动的购物项
        List<OrderDetail> detailList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            //如果有活动的skuId集合里没有这个skuId 说明这个skuId没参与活动
            if(!activitySkuId.contains(orderDetail.getSkuId())){
                detailList.add(skuIdToCartInfoMap.get(orderDetail.getSkuId()));
            }
        }
        OrderDetailVo orderDetailVo = new OrderDetailVo();
        orderDetailVo.setActivityRule(null);
        orderDetailVo.setOrderDetailList(detailList);
        orderDetailVoList.add(orderDetailVo);

        //优惠券处理，获取购物项能使用的优惠券
        List<CouponInfo> couponInfoList = couponInfoService.findTradeCouponInfo(orderDetailList, activityIdToActivityRuleMap, userId);

        //组装vo对象，给页面提供数据
        OrderTradeVo orderTradeVo = new OrderTradeVo();
        orderTradeVo.setActivityReduceAmount(activityReduceAmount);
        orderTradeVo.setOrderDetailVoList(orderDetailVoList);
        orderTradeVo.setCouponInfoList(couponInfoList);
        return orderTradeVo;
    }
}
