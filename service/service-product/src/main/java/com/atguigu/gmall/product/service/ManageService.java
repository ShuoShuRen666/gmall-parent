package com.atguigu.gmall.product.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.model.product.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ManageService {

    /**
     * 查询所有一级分类
     * @return
     */
    List<BaseCategory1> getCategory1();

    /**
     * 根据一级分类id查询二级分类
     * @param category1Id
     * @return
     */
    List<BaseCategory2> getCategory2(Long category1Id);

    /**
     * 根据二级分类id查询三级分类
     * @param category2Id
     * @return
     */
    List<BaseCategory3> getCategory3(Long category2Id);

    /**
     * 根据分类id获取平台属性 + 平台属性值
     * 接口说明：
     *      1.平台属性可以挂载一级分类、二级分类、三级分类
     *      2.查询一级分类下的平台属性： 传categoryId1,0,0    取出该分类的平台属性
     *      3.查询二级分类下的平台属性： 传categoryId2,0,0
     *          取出对应一级分类下的平台属性，和二级分类的平台属性
     *      4.查询三级分类下的平台属性： 传categoryId3,0,0
     *          取出对应一级、二级、三级分类的平台属性
     * @param categoryId1   一级分类id
     * @param categoryId2   二级分类id
     * @param categoryId3   三级分类id
     * @return
     */
    List<BaseAttrInfo> getAttrInfoList(Long categoryId1,Long categoryId2,Long categoryId3);

    /**
     * 保存平台属性的方法
     * @param baseAttrInfo
     */
    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    /**
     * 根据平台属性ID获取平台属性对象数据
     * @param attrId
     * @return
     */
    BaseAttrInfo getAttrInfo(Long attrId);

    /**
     * 分页查询商品表
     * @param pageParam
     * @param spuInfo
     * @return
     */
    IPage<SpuInfo> getSpuInfoPage(Page<SpuInfo> pageParam, SpuInfo spuInfo);

    /**
     * 加载销售属性
     * @return
     */
    List<BaseSaleAttr> getSaleAttrList();

    void saveSpuInfo(SpuInfo spuInfo);

    List<SpuImage> getSpuImageListById(Long spuId);

    List<SpuSaleAttr> getSpuSaleAttrListById(Long spuId);

    void saveSkuInfo(SkuInfo skuInfo);

    IPage<SkuInfo> getPage(Page<SkuInfo> skuInfoPage);

    void onSale(Long skuId);

    void cancelSale(Long skuId);

    SkuInfo getSkuInfo(Long skuId);

    BaseCategoryView getCategoryViewByCategory3Id(Long category3Id);

    BigDecimal getSkuPrice(Long skuId);

    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId,Long spuId);

    Map getSkuValueIdsMap(Long spuId);

    /**
     * 获取全部分类信息(获取首页数据)
     * JSONObject   public class JSONObject extends JSON implements Map<String, Object>
     * @return
     */
    List<JSONObject> getBaseCategoryList();

    /**
     * 通过品牌id查询品牌logo信息
     * @param tmId
     * @return
     */
    BaseTrademark getTrademarkByTmId(Long tmId);

    /**
     * 通过skuId查询对应的平台属性信息
     * @param skuId
     * @return
     */
    List<BaseAttrInfo> getAttrList(Long skuId);

    //根据skuid列表获取sku列表  活动使用
    List<SkuInfo> findSkuInfoBySkuIdList(List<Long> skuIdList);

    //根据关键字获取spu列表，活动使用
    List<SpuInfo> findSpuInfoByKeyword(String keyword);


    //根据spuid列表获取spu列表，活动使用
    List<SpuInfo> findSpuInfoBySpuIdList(List<Long> spuIdList);

    //根据category3Id列表获取category3列表，活动使用
    List<BaseCategory3> findBaseCategory3ByCategory3IdList(List<Long> category3IdList);
}
