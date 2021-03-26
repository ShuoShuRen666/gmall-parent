package com.atguigu.gmall.product.api.ProductApiController;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/product")
public class ProductApiController {

    @Autowired
    private ManageService manageService;

    @ApiOperation("根据skuId获取sku基本信息和图片列表")
    @GetMapping("inner/getSkuInfo/{skuId}")
    public SkuInfo getAttrValueList(@PathVariable Long skuId){
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        return skuInfo;
    }

    @ApiOperation("通过三级分类id查询分类信息")
    @GetMapping("inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryView(@PathVariable Long category3Id){
        return manageService.getCategoryViewByCategory3Id(category3Id);
    }

    @ApiOperation("获取sku最新价格")
    @GetMapping("inner/getSkuPrice/{skuId}")
    public BigDecimal getSkuPrice(@PathVariable Long skuId){
        return manageService.getSkuPrice(skuId);
    }

    @ApiOperation("查询销售属性，销售属性值 并锁定")
    @GetMapping("inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(
            @PathVariable("skuId") Long skuId,
            @PathVariable("spuId") Long spuId){
        return manageService.getSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    @ApiOperation("根据spuId 来获取到销售属性值Id与skuId组成的map集合")
    @GetMapping("inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable Long spuId){
        return manageService.getSkuValueIdsMap(spuId);
    }

    @ApiOperation("获取全部分类信息（获取首页数据）")
    @GetMapping("getBaseCategoryList")
    public Result getBaseCategoryList(){
        List<JSONObject> list = manageService.getBaseCategoryList();
        return Result.ok(list);
    }

    @ApiOperation("通过品牌id查询品牌logo信息")
    @GetMapping("inner/getTrademark/{tmId}")
    public BaseTrademark getTrademark(@PathVariable Long tmId){
        return manageService.getTrademarkByTmId(tmId);
    }

    @ApiOperation("通过skuId查询对应的平台属性信息")
    @GetMapping("inner/getAttrList/{skuId}")
    public List<BaseAttrInfo> getAttrList(@PathVariable Long skuId){
        return manageService.getAttrList(skuId);
    }

    /**
     * 根据spuid列表获取spu列表，活动使用
     * @param spuIdList
     * @return
     */
    @PostMapping("inner/findSpuInfoBySpuIdList")
    public List<SpuInfo> findSpuInfoBySpuIdList(@RequestBody List<Long> spuIdList) {
        return manageService.findSpuInfoBySpuIdList(spuIdList);
    }

    /**
     * 根据category3Id列表获取category3列表，活动使用
     * @param category3IdList
     * @return
     */
    @PostMapping("inner/findBaseCategory3ByCategory3IdList")
    public List<BaseCategory3> findBaseCategory3ByCategory3IdList(@RequestBody List<Long> category3IdList) {
        return manageService.findBaseCategory3ByCategory3IdList(category3IdList);
    }

}
