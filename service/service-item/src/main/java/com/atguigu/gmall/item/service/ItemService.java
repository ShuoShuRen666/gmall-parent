package com.atguigu.gmall.item.service;

import java.util.Map;

public interface ItemService {

    /**
     * 获取sku详细信息
     * @param skuId
     * @return
     */
    Map<String,Object> getBySkuId(Long skuId);
}
