package com.atguigu.gmall.product.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.impl.ProductDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

//value：表示要远程调用的微服务   fallback：表示如果远程调用发生异常，则调用该类中的兜底方法
@FeignClient(value = "service-product",fallback = ProductDegradeFeignClient.class)
public interface ProductFeignClient {

    //发布数据接口  service-product微服务中的 ProductApiController 中的方法

    //根据skuId获取sku基本信息和图片列表
    @GetMapping("api/product/inner/getSkuInfo/{skuId}")
    SkuInfo getAttrValueList(@PathVariable Long skuId);

    //通过三级分类id查询分类信息
    @GetMapping("api/product/inner/getCategoryView/{category3Id}")
    BaseCategoryView getCategoryView(@PathVariable Long category3Id);

    //获取sku最新价格
    @GetMapping("api/product/inner/getSkuPrice/{skuId}")
    BigDecimal getSkuPrice(@PathVariable Long skuId);

    //查询销售属性，销售属性值 并锁定
    @GetMapping("api/product/inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(
            @PathVariable("skuId") Long skuId,
            @PathVariable("spuId") Long spuId);

    //根据spuId 来获取到销售属性值Id与skuId组成的map集合
    @GetMapping("api/product/inner/getSkuValueIdsMap/{spuId}")
    Map getSkuValueIdsMap(@PathVariable Long spuId);

    /**
     * 获取全部分类信息（获取首页数据）
     * @return
     */
    @GetMapping("/api/product/getBaseCategoryList")
    Result getBaseCategoryList();

    /**
     * 通过品牌id查询品牌logo信息
     * @param tmId
     * @return
     */
    @GetMapping("api/product/inner/getTrademark/{tmId}")
    BaseTrademark getTrademark(@PathVariable Long tmId);

    /**
     * 通过skuId查询对应的平台属性信息
     * @param skuId
     * @return
     */
    @GetMapping("api/product/inner/getAttrList/{skuId}")
    List<BaseAttrInfo> getAttrList(@PathVariable Long skuId);

    // 根据关键字获取sku列表，活动使用
    @GetMapping("admin/product/inner/findSkuInfoByKeyword/{keyword}")
    List<SkuInfo> findSkuInfoByKeyword(@PathVariable String keyword);

    //根据skuid列表获取sku列表 活动使用
    @PostMapping("admin/product/inner/findSkuInfoBySkuIdList")
    List<SkuInfo> findSkuInfoBySkuIdList(@RequestBody List<Long> skuIdList);

    /**
     * 根据spuid列表获取spu列表，活动使用
     * @param spuIdList
     * @return
     */
    @PostMapping("/api/product/inner/findSpuInfoBySpuIdList")
    List<SpuInfo> findSpuInfoBySpuIdList(@RequestBody List<Long> spuIdList);

    /**
     * 根据category3Id列表获取category3列表，活动使用
     * @param category3IdList
     * @return
     */
    @PostMapping("/api/product/inner/findBaseCategory3ByCategory3IdList")
    List<BaseCategory3> findBaseCategory3ByCategory3IdList(@RequestBody List<Long> category3IdList);

    /**
     * 根据trademarkId列表获取trademark列表，活动使用
     * @param trademarkIdList
     * @return
     */
    @PostMapping("/admin/product/baseTrademark/inner/findBaseTrademarkByTrademarkIdList")
    List<BaseTrademark> findBaseTrademarkByTrademarkIdList(@RequestBody List<Long> trademarkIdList);
}
