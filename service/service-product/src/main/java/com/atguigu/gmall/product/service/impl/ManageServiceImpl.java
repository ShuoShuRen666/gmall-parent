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
        // 什么情况下 是添加，什么情况下是更新，修改 根据baseAttrInfo 的Id
        if(baseAttrInfo.getId() != null){
            //修改
            baseAttrInfoMapper.updateById(baseAttrInfo);
        }else {
            //添加
            baseAttrInfoMapper.insert(baseAttrInfo);
        }

        //平台属性值
        //修改：通过先删除{baseAttrValue},再新增的方式
        baseAttrValueMapper.delete(new QueryWrapper<BaseAttrValue>().eq("attr_id",baseAttrInfo.getId()));
        // 获取页面传递过来的所有平台属性值数据
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
        // 查询到最新的平台属性值集合数据放入平台属性中！
        baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        return baseAttrInfo;
    }


    /**
     * 根据属性id获取属性值
     * @param attrId
     * @return
     */
    private List<BaseAttrValue> getAttrValueList(Long attrId) {
        List<BaseAttrValue> baseAttrValueList  = baseAttrValueMapper.selectList(new QueryWrapper<BaseAttrValue>().eq("attr_id", attrId));
        return baseAttrValueList;
    }


    /**
     * 分页查询商品表
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
     * 保存商品数据
     * 将传递的数据保存到什么地方{mysql 的什么表！}
     * 								spuInfo
     * 								spuImage
     * 								spuSaleAttr
     * 								spuSaleAttrValue
     * @param spuInfo
     */
    @Override
    public void saveSpuInfo(SpuInfo spuInfo) {
        //spuInfo 表
        spuInfoMapper.insert(spuInfo);
        //spuImage表
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if(!StringUtils.isEmpty(spuImageList)){
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
        //spuSaleAttr 表
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if(!StringUtils.isEmpty(spuSaleAttrList)){
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);
                //spuSaleAttrValue 表
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
     * 根据spuId获取图片列表
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
        //sku_info 表
        skuInfoMapper.insert(skuInfo);
        //sku_image 表
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if(!StringUtils.isEmpty(skuImageList)){
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        //sku_attr_value 表
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if(!StringUtils.isEmpty(skuAttrValueList)){
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        //sku_sale_attr_value 表
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
     * 获取sku分页列表
     * @param skuInfoPage
     * @return
     */
    @Override
    public IPage<SkuInfo> getPage(Page<SkuInfo> skuInfoPage) {
        QueryWrapper<SkuInfo> queryWrapper = new QueryWrapper<SkuInfo>().orderByDesc("id");
        return skuInfoMapper.selectPage(skuInfoPage, queryWrapper);
    }

    /**
     * 商品上架
     * @param skuId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);
        //发消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_UPPER,skuId);
    }

    /**
     * 商品下架
     * @param skuId
     */
    @Override
    public void cancelSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(0);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);
        //发消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_LOWER,skuId);
    }

    /**
     * 根据skuId获取skuInfo和skuImage的集合信息
     * 该方法需要  做分布式锁，防止缓存击穿
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

    //方式二 ： 使用redisson 实现分布式锁
    private SkuInfo getSkuInfoByRedisson(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            //查看缓存中是否有数据，必须要知道缓存的key是谁
            // 定义key sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            skuInfo = (SkuInfo)redisTemplate.opsForValue().get(skuKey);
            if(skuInfo == null){
                //使用redisson来解决
                // 定义锁的key sku:skuId:lock  set k1 v1 px 10000 nx
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                RLock lock = redissonClient.getLock(lockKey);
                // 尝试加锁，最多等待1秒，上锁以后1秒自动解锁
                boolean flag = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                if(flag){
                    try {
                        //上锁成功,从数据库获取数据
                        skuInfo = getSkuInfoDB(skuId);
                        if(skuInfo == null){
                            //数据库没有此数据，为了防止缓存穿透，放一个空对象到缓存中
                            SkuInfo skuInfo1 = new SkuInfo();
                            redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            return skuInfo1;
                        }
                        //数据库中有此数据，放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo;
                    } finally {
                        //释放锁
                        lock.unlock();
                    }
                }else {
                    //其他线程没有获取到锁，自旋等待
                    Thread.sleep(1000);
                    return getSkuInfo(skuId);
                }

            }else {
                return skuInfo;
            }
        } catch (InterruptedException e) {
            // 获取到redis宕机的原因，通知管理员：接入短信接口，直接发信息给管理员
            e.printStackTrace();
        }
        //redisson出现异常兜底（查询数据库）
        return getSkuInfoDB(skuId);
    }

    //方式一 ： 根据 redis 中的 set 命令 + lua 脚本实现分布式锁
    private SkuInfo getSkuInfoByRedis(Long skuId) {

        SkuInfo skuInfo = null;
        try {
        //查看缓存中是否有数据，必须要知道缓存的key是谁
        // 定义key sku:skuId:info
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            skuInfo = (SkuInfo)redisTemplate.opsForValue().get(skuKey);
            //如果缓存中获取到的数据是null
            if(skuInfo == null){
                //说明缓存中没有数据要从数据库中获取数据,  要使用锁 防止缓存击穿
                // 定义锁的key sku:skuId:lock  set k1 v1 px 10000 nx
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                //定义一个uuid作为锁的value
                String uuid = UUID.randomUUID().toString();
                Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);

                if(flag){
                    //上锁成功,查询数据库
                    skuInfo = getSkuInfoDB(skuId);
                   //防止缓存穿透
                    if(skuInfo == null){
                        //数据库里没这个数据，要在缓存中放入一个空对象
                        SkuInfo skuInfo1 = new SkuInfo();
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);

                        return skuInfo1;
                    }
                    //当skuInfo不为空,直接将获取到的数据放入缓存
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                    //将数据放入到了缓存，任务执行完成，此时需要将锁删除，使用lua脚本
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    //声明一个对象 DefaultRedisScript
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                    redisScript.setScriptText(script);
                    redisScript.setResultType(Long.class);
                    //执行lua脚本,删除锁
                    redisTemplate.execute(redisScript, Arrays.asList(lockKey),uuid);

                    return skuInfo;
                }else {
                    //没有获取到锁的线程
                        Thread.sleep(1000);
                        getSkuInfo(skuId);
                }
            }else {
                //如果缓存中获取到的数据不是空，则直接返回
                return skuInfo;
            }
        } catch (InterruptedException e) {
            // 获取到redis宕机的原因，通知管理员：接入短信接口，直接发信息给管理员
            e.printStackTrace();
        }
        //如果redis宕机了，使用数据库兜底
        return getSkuInfoDB(skuId);
    }


    //根据skuId从  数据库  获取skuInfo和skuImage的集合信息
    private SkuInfo getSkuInfoDB(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        //根据skuId查询图片列表集合
        List<SkuImage> skuImageList = skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId));
        if(skuInfo != null){
            skuInfo.setSkuImageList(skuImageList);
        }

        return skuInfo;
    }

    /**
     * 通过三级分类id查询分类信息
     * @param category3Id
     * @return
     */
    @Override
    @GmallCache(prefix = "categoryView:")  //该方法需要  做分布式锁，防止缓存击穿
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    /**
     * 获取sku最新价格
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
     * 查询销售属性，销售属性值 并锁定
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
     * 根据spuId 来获取到销售属性值Id与skuId组成的map集合
     * @param spuId
     * @return
     */
    @Override
    @GmallCache(prefix = "skuValueIds:")
    public Map getSkuValueIdsMap(Long spuId) {
        Map<Object,Object> map = new HashMap();
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);
        if(!StringUtils.isEmpty(mapList)){
            for (Map skuMap : mapList) {
                map.put(skuMap.get("value_ids"),skuMap.get("sku_id"));
            }
        }
        return map;
    }

    /**
     * 获取全部分类信息(获取首页数据)
     * JSONObject   public class JSONObject extends JSON implements Map<String, Object>
     * @return
     */
    @Override
    @GmallCache(prefix = "index:")
    public List<JSONObject> getBaseCategoryList() {
        //声明一个集合
        List<JSONObject> jsonObjectList = new ArrayList<>();

        //查找视图下的所有分类数据
        List<BaseCategoryView> baseCategoryViewList = baseCategoryViewMapper.selectList(null);

        //以一级分类Id进行分组，（为了分组，所以需要使用    stream流  ）
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViewList.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));

        //声明一个index
        int index = 1;

        //获取一级分类下的所有数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry : category1Map.entrySet()) {
            //获取一级分类下的id
            Long category1Id = entry.getKey();
            //获取一级分类下的所有集合{包含分类名称，分类id}
            List<BaseCategoryView> category1List = entry.getValue();
            //声明一个集合，用来存储一级分类下的数据  最终放入 jsonObjectList 集合中
            JSONObject category1 = new JSONObject();
            category1.put("index",index);
            category1.put("categoryId",category1Id);
            //当以分类id进行分组之后，后面所有的分类名称都一样，所以只取第一个就可 get(0)
            category1.put("categoryName",category1List.get(0).getCategory1Name());
//            category1.put("categoryChild","暂无");
            //变量更新
            index++;


            //获取二级分类下的所有数据,以二级分类id分组
            Map<Long, List<BaseCategoryView>> category2Map = category1List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));

            //因为一个一级分类下，有多个二级分类，所以要声明一个集合来存储二级分类的数据
            List<JSONObject> category2Child = new ArrayList<>();

            //循环遍历
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                //获取二级分类下的id
                Long category2Id = entry2.getKey();
                //获取二级分类下的所有集合
                List<BaseCategoryView> category2List = entry2.getValue();
                //声明一个集合，用来存储二级分类下的数据  最终放入 jsonObjectList 集合中
                JSONObject category2 = new JSONObject();
                category2.put("categoryName",category2List.get(0).getCategory2Name());
                category2.put("categoryId",category2Id);
                //将二级分类对象，添加到集合中
                category2Child.add(category2);


                //因为一个二级分类下，有多个三级分类，所以要声明一个集合来存储三级分类的数据
                List<JSONObject> category3Child = new ArrayList<>();

                //获取三级分类下的所有数据
                category2List.forEach(entry3->{
                    //声明一个集合，用来存储三级分类下的数据  最终放入 jsonObjectList 集合中
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",entry3.getCategory3Id());
                    category3.put("categoryName",entry3.getCategory3Name());
                    //将三级分类对象，添加到集合中
                    category3Child.add(category3);
                });

                //将三级分类放入二级分类下 构成JSON数据
                category2.put("categoryChild",category3Child);
            }

            //将二级分类放入一级分类下 构成JSON数据
            category1.put("categoryChild",category2Child);

            //最后将所有的一级分类数据 放入到 List<JSONObject> 集合中，构成JSON数据
            jsonObjectList.add(category1);
        }
        //返回集合
        return jsonObjectList;
    }

    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }

    //通过skuId查询对应的平台属性信息
    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        //base_attr_info  base_attr_value  sku_attr_value
        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }

    //根据skuid列表获取sku列表 活动使用
    @Override
    public List<SkuInfo> findSkuInfoBySkuIdList(List<Long> skuIdList) {
        return skuInfoMapper.selectBatchIds(skuIdList);
    }

    //根据关键字获取spu列表，活动使用
    @Override
    public List<SpuInfo> findSpuInfoByKeyword(String keyword) {
        return spuInfoMapper.selectList(new QueryWrapper<SpuInfo>().like("spu_name",keyword));
    }

    //根据spuid列表获取spu列表，活动使用
    @Override
    public List<SpuInfo> findSpuInfoBySpuIdList(List<Long> spuIdList) {
        return spuInfoMapper.selectBatchIds(spuIdList);
    }

    //根据category3Id列表获取category3列表，活动使用
    @Override
    public List<BaseCategory3> findBaseCategory3ByCategory3IdList(List<Long> category3IdList) {
        return baseCategory3Mapper.selectBatchIds(category3IdList);
    }


}
