package com.atguigu.gmall.item.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.impl.ItemDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "service-item", fallback = ItemDegradeFeignClient.class)
public interface ItemFeignClient {

    //发布数据接口  service-item 微服务中  ItemApiController 中的方法

    //获取sku详情信息
    @GetMapping("api/item/{skuId}")
    Result getItem(@PathVariable Long skuId);
}
