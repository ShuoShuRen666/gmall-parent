package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.activity.ActivityInfo;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.ActivityRuleVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.cart.CartInfoVo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.SkuInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface ActivityInfoService extends IService<ActivityInfo> {

    //分页查询活动列表
    IPage<ActivityInfo> getPage(Page<ActivityInfo> pageParam);

    //保存活动规则
    void saveActivityRule (ActivityRuleVo activityRuleVo);

    //根据关键字获取sku列表，活动使用
    List<SkuInfo> findSkuInfoByKeyword(String keyword);

    //获取活动规则
    Map<String,Object> findActivityRuleList(Long activityId);

    //根据skuId找到活动规则列表
    List<ActivityRule> findActivityRule(Long skuId);

    //获取购物项对应的活动规则列表
    List<CartInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap);

    //获取购物项中 活动id对应的最优促销活动规则
    Map<Long,ActivityRule> findTradeActivityRuleMap(List<OrderDetail> orderDetailList);
}
