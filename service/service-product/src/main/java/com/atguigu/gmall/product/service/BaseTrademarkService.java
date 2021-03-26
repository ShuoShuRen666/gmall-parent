package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseTrademarkService extends IService<BaseTrademark> {

    IPage<BaseTrademark> getPage(Page<BaseTrademark> pageParam);

    ////根据关键字获取spu列表，活动使用
    List<BaseTrademark> findBaseTrademarkByKeyword(String keyword);

    //根据trademarkId列表获取trademark列表，活动使用
    List<BaseTrademark> findBaseTrademarkByTrademarkIdList(List<Long> trademarkIdList);
}
