package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("api/item")
public class ItemApiController {

    @Autowired
    private ItemService itemService;

    //发布一个远程调用地址给web-all使用
    @ApiOperation("获取sku详情信息")
    @GetMapping("{skuId}")
    public Result getItem(@PathVariable Long skuId){
        //获取封装之后的数据集
        Map<String, Object> result = itemService.getBySkuId(skuId);
        // 将map赋值给Result对象中的data属性
        return Result.ok(result);
    }
}
