package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.awt.image.Kernel;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class ManageServiceImpl implements ManageService {

    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private SpuInfoMapper spuInfoMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;

    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        return baseCategory2Mapper.selectList(new QueryWrapper<BaseCategory2>().eq("category1_id",category1Id));
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        return baseCategory3Mapper.selectList(new QueryWrapper<BaseCategory3>().eq("category2_id",category2Id));
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id,category2Id,category3Id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        // ??????????????? ????????????????????????????????????????????? ??????baseAttrInfo ???Id
        if(baseAttrInfo.getId() != null){
            //??????
            baseAttrInfoMapper.updateById(baseAttrInfo);
        }else {
            //??????
            baseAttrInfoMapper.insert(baseAttrInfo);
        }

        //???????????????
        //????????????????????????{baseAttrValue},??????????????????
        baseAttrValueMapper.delete(new QueryWrapper<BaseAttrValue>().eq("attr_id",baseAttrInfo.getId()));
        // ??????????????????????????????????????????????????????
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if(attrValueList != null && attrValueList.size() > 0){
            for (BaseAttrValue baseAttrValue : attrValueList) {
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }

    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        // ?????????????????????????????????????????????????????????????????????
        baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        return baseAttrInfo;
    }


    /**
     * ????????????id???????????????
     * @param attrId
     * @return
     */
    private List<BaseAttrValue> getAttrValueList(Long attrId) {
        List<BaseAttrValue> baseAttrValueList  = baseAttrValueMapper.selectList(new QueryWrapper<BaseAttrValue>().eq("attr_id", attrId));
        return baseAttrValueList;
    }


    /**
     * ?????????????????????
     * @param pageParam
     * @param spuInfo
     * @return
     */
    @Override
    public IPage<SpuInfo> getSpuInfoPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> spuInfoQueryWrapper = new QueryWrapper<>();
        spuInfoQueryWrapper.eq("category3_id",spuInfo.getCategory3Id()).orderByDesc("id");
        return spuInfoMapper.selectPage(pageParam, spuInfoQueryWrapper);
    }

    @Override
    public List<BaseSaleAttr> getSaleAttrList() {
        return baseSaleAttrMapper.selectList(null);
    }

    /**
     * ??????????????????
     * ???????????????????????????????????????{mysql ???????????????}
     * 								spuInfo
     * 								spuImage
     * 								spuSaleAttr
     * 								spuSaleAttrValue
     * @param spuInfo
     */
    @Override
    public void saveSpuInfo(SpuInfo spuInfo) {
        //spuInfo ???
        spuInfoMapper.insert(spuInfo);
        //spuImage???
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if(!StringUtils.isEmpty(spuImageList)){
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
        //spuSaleAttr ???
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if(!StringUtils.isEmpty(spuSaleAttrList)){
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);
                //spuSaleAttrValue ???
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if(!StringUtils.isEmpty(spuSaleAttrValueList)){
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }
    }

    /**
     * ??????spuId??????????????????
     * @param spuId
     * @return
     */
    @Override
    public List<SpuImage> getSpuImageListById(Long spuId) {
        QueryWrapper<SpuImage> queryWrapper = new QueryWrapper<SpuImage>().eq("spu_id", spuId);
        return spuImageMapper.selectList(queryWrapper);
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrListById(Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveSkuInfo(SkuInfo skuInfo) {
        //sku_info ???
        skuInfoMapper.insert(skuInfo);
        //sku_image ???
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if(!StringUtils.isEmpty(skuImageList)){
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        //sku_attr_value ???
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if(!StringUtils.isEmpty(skuAttrValueList)){
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        //sku_sale_attr_value ???
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if(!StringUtils.isEmpty(skuSaleAttrValueList)){
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }


    }

    /**
     * ??????sku????????????
     * @param skuInfoPage
     * @return
     */
    @Override
    public IPage<SkuInfo> getPage(Page<SkuInfo> skuInfoPage) {
        QueryWrapper<SkuInfo> queryWrapper = new QueryWrapper<SkuInfo>().orderByDesc("id");
        return skuInfoMapper.selectPage(skuInfoPage, queryWrapper);
    }

    /**
     * ????????????
     * @param skuId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);
        //?????????
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_UPPER,skuId);
    }

    /**
     * ????????????
     * @param skuId
     */
    @Override
    public void cancelSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(0);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);
        //?????????
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_LOWER,skuId);
    }

    /**
     * ??????skuId??????skuInfo???skuImage???????????????
     * ???????????????  ????????????????????????????????????
     * @param skuId
     * @return
     */
    @Override
    @GmallCache(prefix = "skuInfo:")
    public SkuInfo getSkuInfo(Long skuId) {

        //return getSkuInfoByRedisson(skuId);
        //return getSkuInfoByRedis(skuId);
        return getSkuInfoDB(skuId);
    }

    //????????? ??? ??????redisson ??????????????????
    private SkuInfo getSkuInfoByRedisson(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            //?????????????????????????????????????????????????????????key??????
            // ??????key sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            skuInfo = (SkuInfo)redisTemplate.opsForValue().get(skuKey);
            if(skuInfo == null){
                //??????redisson?????????
                // ????????????key sku:skuId:lock  set k1 v1 px 10000 nx
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                RLock lock = redissonClient.getLock(lockKey);
                // ???????????????????????????1??????????????????1???????????????
                boolean flag = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                if(flag){
                    try {
                        //????????????,????????????????????????
                        skuInfo = getSkuInfoDB(skuId);
                        if(skuInfo == null){
                            //????????????????????????????????????????????????????????????????????????????????????
                            SkuInfo skuInfo1 = new SkuInfo();
                            redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            return skuInfo1;
                        }
                        //???????????????????????????????????????
                        redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo;
                    } finally {
                        //?????????
                        lock.unlock();
                    }
                }else {
                    //?????????????????????????????????????????????
                    Thread.sleep(1000);
                    return getSkuInfo(skuId);
                }

            }else {
                return skuInfo;
            }
        } catch (InterruptedException e) {
            // ?????????redis????????????????????????????????????????????????????????????????????????????????????
            e.printStackTrace();
        }
        //redisson???????????????????????????????????????
        return getSkuInfoDB(skuId);
    }

    //????????? ??? ?????? redis ?????? set ?????? + lua ????????????????????????
    private SkuInfo getSkuInfoByRedis(Long skuId) {

        SkuInfo skuInfo = null;
        try {
        //?????????????????????????????????????????????????????????key??????
        // ??????key sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            skuInfo = (SkuInfo)redisTemplate.opsForValue().get(skuKey);
            //????????????????????????????????????null
            if(skuInfo == null){
                //?????????????????????????????????????????????????????????,  ???????????? ??????????????????
                // ????????????key sku:skuId:lock  set k1 v1 px 10000 nx
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                //????????????uuid????????????value
                String uuid = UUID.randomUUID().toString();
                Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);

                if(flag){
                    //????????????,???????????????
                    skuInfo = getSkuInfoDB(skuId);
                   //??????????????????
                    if(skuInfo == null){
                        //??????????????????????????????????????????????????????????????????
                        SkuInfo skuInfo1 = new SkuInfo();
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);

                        return skuInfo1;
                    }
                    //???skuInfo?????????,???????????????????????????????????????
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                    //????????????????????????????????????????????????????????????????????????????????????lua??????
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    //?????????????????? DefaultRedisScript
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                    redisScript.setScriptText(script);
                    redisScript.setResultType(Long.class);
                    //??????lua??????,?????????
                    redisTemplate.execute(redisScript, Arrays.asList(lockKey),uuid);

                    return skuInfo;
                }else {
                    //???????????????????????????
                        Thread.sleep(1000);
                        getSkuInfo(skuId);
                }
            }else {
                //????????????????????????????????????????????????????????????
                return skuInfo;
            }
        } catch (InterruptedException e) {
            // ?????????redis????????????????????????????????????????????????????????????????????????????????????
            e.printStackTrace();
        }
        //??????redis?????????????????????????????????
        return getSkuInfoDB(skuId);
    }


    //??????skuId???  ?????????  ??????skuInfo???skuImage???????????????
    private SkuInfo getSkuInfoDB(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        //??????skuId????????????????????????
        List<SkuImage> skuImageList = skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId));
        if(skuInfo != null){
            skuInfo.setSkuImageList(skuImageList);
        }

        return skuInfo;
    }

    /**
     * ??????????????????id??????????????????
     * @param category3Id
     * @return
     */
    @Override
    @GmallCache(prefix = "categoryView:")  //???????????????  ????????????????????????????????????
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    /**
     * ??????sku????????????
     * @param skuId
     * @return
     */
    @Override
    @GmallCache(prefix = "skuPrice:")
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if(null != skuInfo){
            return skuInfo.getPrice();
        }
        return new BigDecimal(0);
    }

    /**
     * ???????????????????????????????????? ?????????
     * @param skuId
     * @param spuId
     * @return
     */
    @Override
    @GmallCache(prefix = "spuSaleAttrListCheck:")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId,spuId);
    }

    /**
     * ??????spuId ???????????????????????????Id???skuId?????????map??????
     * @param spuId
     * @return
     */
    @Override
    @GmallCache(prefix = "skuValueIds:")
    public Map getSkuValueIdsMap(Long spuId) {
        Map<Object,Object> map = new HashMap();
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);
        //map<sku_id,"1">
        //map<value_ids,"1|4|7">
        if(!StringUtils.isEmpty(mapList)){
            for (Map skuMap : mapList) {
                map.put(skuMap.get("value_ids"),skuMap.get("sku_id"));
            }
        }
        return map;
    }

    /**
     * ????????????????????????(??????????????????)
     * JSONObject   public class JSONObject extends JSON implements Map<String, Object>
     * @return
     */
    @Override
    @GmallCache(prefix = "index:")
    public List<JSONObject> getBaseCategoryList() {
        //??????????????????
        List<JSONObject> jsonObjectList = new ArrayList<>();

        //????????????????????????????????????
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);

        //???????????????Id???????????????????????????????????????????????????    stream???  ???
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));

        //????????????index
        int index = 1;

        //????????????????????????????????????
        for (Map.Entry<Long, List<BaseCategoryView>> entry : category1Map.entrySet()) {
            //????????????????????????id
            Long category1Id = entry.getKey();
            //????????????????????????????????????{???????????????????????????id}
            List<BaseCategoryView> category1List = entry.getValue();
            //?????????????????????????????????????????????????????????  ???????????? jsonObjectList ?????????
            JSONObject category1 = new JSONObject();
            category1.put("index",index);
            category1.put("categoryId",category1Id);
            //????????????id??????????????????????????????????????????????????????????????????????????????????????? get(0)
            category1.put("categoryName",category1List.get(0).getCategory1Name());
//            category1.put("categoryChild","??????");
            //????????????
            index++;


            //????????????????????????????????????,???????????????id??????
            Map<Long, List<BaseCategoryView>> category2Map = category1List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));

            //???????????????????????????????????????????????????????????????????????????????????????????????????????????????
            List<JSONObject> category2Child = new ArrayList<>();

            //????????????
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                //????????????????????????id
                Long category2Id = entry2.getKey();
                //????????????????????????????????????
                List<BaseCategoryView> category2List = entry2.getValue();
                //?????????????????????????????????????????????????????????  ???????????? jsonObjectList ?????????
                JSONObject category2 = new JSONObject();
                category2.put("categoryName",category2List.get(0).getCategory2Name());
                category2.put("categoryId",category2Id);
                //??????????????????????????????????????????
                category2Child.add(category2);


                //???????????????????????????????????????????????????????????????????????????????????????????????????????????????
                List<JSONObject> category3Child = new ArrayList<>();

                //????????????????????????????????????
                category2List.forEach(entry3->{
                    //?????????????????????????????????????????????????????????  ???????????? jsonObjectList ?????????
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",entry3.getCategory3Id());
                    category3.put("categoryName",entry3.getCategory3Name());
                    //??????????????????????????????????????????
                    category3Child.add(category3);
                });

                //???????????????????????????????????? ??????JSON??????
                category2.put("categoryChild",category3Child);
            }

            //???????????????????????????????????? ??????JSON??????
            category1.put("categoryChild",category2Child);

            //???????????????????????????????????? ????????? List<JSONObject> ??????????????????JSON??????
            jsonObjectList.add(category1);
        }
        //????????????
        return jsonObjectList;
    }

    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }

    //??????skuId?????????????????????????????????
    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        //base_attr_info  base_attr_value  sku_attr_value
        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }

    //??????skuid????????????sku?????? ????????????
    @Override
    public List<SkuInfo> findSkuInfoBySkuIdList(List<Long> skuIdList) {
        return skuInfoMapper.selectBatchIds(skuIdList);
    }

    //?????????????????????spu?????????????????????
    @Override
    public List<SpuInfo> findSpuInfoByKeyword(String keyword) {
        return spuInfoMapper.selectList(new QueryWrapper<SpuInfo>().like("spu_name",keyword));
    }

    //??????spuid????????????spu?????????????????????
    @Override
    public List<SpuInfo> findSpuInfoBySpuIdList(List<Long> spuIdList) {
        return spuInfoMapper.selectBatchIds(spuIdList);
    }

    //??????category3Id????????????category3?????????????????????
    @Override
    public List<BaseCategory3> findBaseCategory3ByCategory3IdList(List<Long> category3IdList) {
        return baseCategory3Mapper.selectBatchIds(category3IdList);
    }


}
