package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.SeckillGoods;

import java.util.List;

public interface SeckillGoodsService {

    //返回秒杀商品列表
    List<SeckillGoods> findAll();

    //根据当前商品 id（skuId） 获取秒杀商品详情
    SeckillGoods getSeckillGoods(Long id);

    //根据用户和商品ID实现秒杀下单
    void seckillOrder(Long skuId, String userId);

    //根据商品id与用户ID  检查订单
    Result checkOrder(Long skuId, String userId);
}
