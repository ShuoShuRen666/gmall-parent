package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.SkuInfo;

import java.util.List;

public interface SkuInfoService {

    //根据关键字获取sku列表
    List<SkuInfo> findSkuInfoByKeyword(String keyword);
}
