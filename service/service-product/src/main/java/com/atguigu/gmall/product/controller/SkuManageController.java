package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.service.ManageService;
import com.atguigu.gmall.product.service.SkuInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "sku管理")
@RestController
@RequestMapping("admin/product")
public class SkuManageController {

    @Autowired
    private ManageService manageService;

    @Autowired
    private SkuInfoService skuInfoService;

    @ApiOperation("添加sku")
    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo){
        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

    // 根据关键字获取sku列表，活动使用
    @GetMapping("inner/findSkuInfoByKeyword/{keyword}")
    public List<SkuInfo> findSkuInfoByKeyword(@PathVariable String keyword){
        return skuInfoService.findSkuInfoByKeyword(keyword);
    }

    //根据skuid列表获取sku列表 活动使用
    @PostMapping("inner/findSkuInfoBySkuIdList")
    public List<SkuInfo> findSkuInfoBySkuIdList(@RequestBody List<Long> skuIdList){
        return manageService.findSkuInfoBySkuIdList(skuIdList);
    }
}
