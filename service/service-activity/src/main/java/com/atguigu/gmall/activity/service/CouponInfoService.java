package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface CouponInfoService extends IService<CouponInfo> {

    //分页查询优惠券列表
    IPage<CouponInfo> selectPage(Page<CouponInfo> pageParam);

    //新增优惠券规则（大保存）
    void saveCouponRule(CouponRuleVo couponRuleVo);

    //获取优惠券规则信息
    Map<String, Object> findCouponRuleList(Long couponId);

    //根据关键字获取优惠券列表，活动使用
    List<CouponInfo> findCouponByKeyword(String keyword);

    //获取优惠券信息
    List<CouponInfo> findCouponInfo(Long skuId, Long activityId, Long userId);

    //领取优惠券
    void getCouponInfo(Long couponId, Long userId);

    //我的优惠券
    IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, Long userId);

    /**
     * 获取购物项对应的优惠券列表
     * @param cartInfoList
     * @param skuIdToActivityIdMap  这个skuId是否存在对应的活动
     * @param userId  标识当前用户是否领取优惠劵
     * @return
     */
    Map<Long,List<CouponInfo>> findCartCouponInfo(List<CartInfo> cartInfoList, Map<Long,Long> skuIdToActivityIdMap, Long userId);

    //根据购物项获取对应的优惠券列表
    // 计算优惠券规则需要减去促销金额， 还需要处理活动优惠券，所以把活动信息作为参数传入
    //Map<Long, ActivityRule> activityIdToActivityRuleMap 一个活动id对应一个最优的活动规则
    List<CouponInfo> findTradeCouponInfo(List<OrderDetail> orderDetailList, Map<Long, ActivityRule> activityIdToActivityRuleMap, Long userId);

    //更新优惠券使用状态
    void updateCouponInfoUseStatus(Long couponId, Long userId, Long orderId);

}
