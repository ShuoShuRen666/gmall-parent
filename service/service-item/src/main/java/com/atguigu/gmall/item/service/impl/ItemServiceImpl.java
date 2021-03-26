package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Supplier;


@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ListFeignClient listFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        Map<String,Object> result = new HashMap<>();

        //远程调用方法

         //  使用异步编排优化商品详细渲染！
        //1.创建一个异步编排对象，（有返回值）
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //skuInfo 和  skuImage集合   数据
            SkuInfo skuInfo = productFeignClient.getAttrValueList(skuId);
            //将数据封装到 map 中
            result.put("skuInfo",skuInfo);
            return skuInfo;
        },threadPoolExecutor);

        //2.拿到异步编排对象获取到的skuInfo，获取三级分类id
        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //通过三级分类id查询分类信息
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            result.put("categoryView", categoryView);
        },threadPoolExecutor);

        //3.创建一个异步编排对象，（无返回值）
        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
            //获取sku最新价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            result.put("price", skuPrice);
        },threadPoolExecutor);

        //4.拿到异步编排对象获取到的skuInfo，查询销售属性，销售属性值
        CompletableFuture<Void> spuSaleAttrListCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //查询销售属性，销售属性值 并锁定
            List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
            result.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
        },threadPoolExecutor);

        //5.拿到异步编排对象获取到的skuInfo,实现用户点击销售属性值切换sku的功能
        CompletableFuture<Void> skuValueIdsCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //用户点击销售属性值切换sku的功能
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            //因为前端需要的是json数据，所以要把Map转换为join数据
            String mapJson = JSON.toJSONString(skuValueIdsMap);
            System.out.println("mapJson = " + mapJson);
            result.put("valuesSkuJson",mapJson);
        },threadPoolExecutor);

        //6.更新商品incrHotScore
        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        },threadPoolExecutor);

        //多任务组合
        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                categoryViewCompletableFuture,
                skuPriceCompletableFuture,
                spuSaleAttrListCompletableFuture,
                skuValueIdsCompletableFuture,
                incrHotScoreCompletableFuture
        ).join();
        //返回 map 集合
        return result;
    }
}
