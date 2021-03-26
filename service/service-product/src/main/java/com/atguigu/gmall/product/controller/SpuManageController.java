package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@Api(tags = "商品管理")
@RequestMapping("admin/product")
@RestController
public class SpuManageController {

    @Autowired
    private ManageService manageService;

    @ApiOperation("分页获取商品信息")
    @GetMapping("{page}/{limit}")
    public Result getSpuInfoPage(
            @PathVariable Long page,
            @PathVariable Long limit,
            SpuInfo spuInfo) {
        Page<SpuInfo> spuInfoPage = new Page<>(page, limit);
        IPage<SpuInfo> spuInfoPageList = manageService.getSpuInfoPage(spuInfoPage, spuInfo);
        return Result.ok(spuInfoPageList);
    }

    @ApiOperation("加载销售属性")
    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList(){
        List<BaseSaleAttr> baseSaleAttrList = manageService.getSaleAttrList();
        return Result.ok(baseSaleAttrList);
    }

    @ApiOperation("保存商品属性SPU")
    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        manageService.saveSpuInfo(spuInfo);
        return Result.ok();
    }

    @ApiOperation("根据spuId获取图片列表")
    @GetMapping("spuImageList/{spuId}")
    public Result spuImageList(@PathVariable Long spuId){
        List<SpuImage> spuImageList = manageService.getSpuImageListById(spuId);
        return Result.ok(spuImageList);
    }

    @ApiOperation("根据spuId获取销售属性")
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result<List<SpuSaleAttr>> spuSaleAttrList(@PathVariable Long spuId){
        List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrListById(spuId);
        return Result.ok(spuSaleAttrList);
    }

    @ApiOperation("获取sku分页列表")
    @GetMapping("list/{page}/{limit}")
    public Result index(@PathVariable Long page,@PathVariable Long limit){
        Page<SkuInfo> skuInfoPage = new Page<>(page,limit);
        IPage<SkuInfo> spuInfoIPage = manageService.getPage(skuInfoPage);
        return Result.ok(spuInfoIPage);
    }

    @ApiOperation("商品上架")
    @GetMapping("onSale/{skuId}")
    public Result onSale(@PathVariable Long skuId){
        manageService.onSale(skuId);
        return Result.ok();
    }

    @ApiOperation("商品下架")
    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale(@PathVariable Long skuId){
        manageService.cancelSale(skuId);
        return Result.ok();
    }

    @ApiOperation("根据关键字获取spu列表，活动使用")
    @GetMapping("findSpuInfoByKeyword/{keyword}")
    public Result findSpuInfoByKeyword(@PathVariable String keyword){
        return Result.ok(manageService.findSpuInfoByKeyword(keyword));
    }
}
