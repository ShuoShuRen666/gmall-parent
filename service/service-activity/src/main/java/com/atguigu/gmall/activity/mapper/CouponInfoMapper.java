package com.atguigu.gmall.activity.mapper;

import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CouponInfoMapper extends BaseMapper<CouponInfo> {

    // 获取普通优惠券
    List<CouponInfo> selectCouponInfoList(@Param("spuId") Long spuId,
                                          @Param("category3Id") Long category3Id,
                                          @Param("tmId") Long tmId,
                                          @Param("userId") Long userId);


    // 获取活动优惠券
    List<CouponInfo> selectActivityCouponInfoList(@Param("spuId") Long spuId,
                                                  @Param("category3Id") Long category3Id,
                                                  @Param("tmId") Long tmId,
                                                  @Param("activityId") Long activityId,
                                                  @Param("userId") Long userId);

    //我的优惠券
    IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, @Param("userId") Long userId);

    //获取购物车sku对应的优惠券列表
    List<CouponInfo> selectCartCouponInfoList(@Param("skuInfoList") List<SkuInfo> skuInfoList, @Param("userId") Long userId);

    //获取购物车下单sku对应的优惠券列表（只获取用户已领取的优惠券）
    List<CouponInfo> selectTradeCouponInfoList(@Param("skuInfoList") List<SkuInfo> skuInfoList, @Param("userId") Long userId);
}
