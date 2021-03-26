package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.mapper.CouponRangeMapper;
import com.atguigu.gmall.activity.mapper.CouponUseMapper;
import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.model.activity.*;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.CouponRangeType;
import com.atguigu.gmall.model.enums.CouponStatus;
import com.atguigu.gmall.model.enums.CouponType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.BaseCategory3;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Autowired
    private CouponInfoMapper couponInfoMapper;

    @Autowired
    private CouponRangeMapper couponRangeMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CouponUseMapper couponUseMapper;

    //分页查询优惠券列表
    @Override
    public IPage<CouponInfo> selectPage(Page<CouponInfo> pageParam) {
        IPage<CouponInfo> couponInfoPage = couponInfoMapper.selectPage(pageParam, new QueryWrapper<CouponInfo>().orderByDesc("id"));
        //设置优惠券类型
        couponInfoPage.getRecords().stream().forEach(couponInfo ->{
            //  获取优惠劵类型
            couponInfo.setCouponTypeString(CouponType.getNameByType(couponInfo.getCouponType()));
            //  如果优惠劵范围不为空，则要赋值范围
            if(null != couponInfo.getRangeType()){
                couponInfo.setRangeTypeString(CouponRangeType.getNameByType(couponInfo.getCouponType()));
            }
        });
        return couponInfoPage;
    }

    //新增优惠券规则（大保存）
    @Override
    public void saveCouponRule(CouponRuleVo couponRuleVo) {
        /*
        1.先删除，再更新 coupon_range  这样点击添加规则按钮的时候，就可以修改、添加两个功能共用了
        2.更新 coupon_info
         */

        //删除coupon_range
        couponRangeMapper.delete(new QueryWrapper<CouponRange>().eq("coupon_id",couponRuleVo.getCouponId()));
        //更新coupon_range
        List<CouponRange> couponSkuList = couponRuleVo.getCouponRangeList();
        for (CouponRange couponRange : couponSkuList) {
            //页面没有传couponId  所以需要赋值
            couponRange.setCouponId(couponRuleVo.getCouponId());
            couponRangeMapper.insert(couponRange);
        }

        // 更新 coupon_info
        CouponInfo couponInfo = this.getById(couponRuleVo.getCouponId());
        couponInfo.setBenefitAmount(couponRuleVo.getBenefitAmount());
        couponInfo.setBenefitDiscount(couponRuleVo.getBenefitDiscount());
        couponInfo.setConditionAmount(couponRuleVo.getConditionAmount());
        couponInfo.setConditionNum(couponRuleVo.getConditionNum());
        couponInfo.setRangeDesc(couponRuleVo.getRangeDesc());
        couponInfo.setRangeType(couponRuleVo.getRangeType().name());

        couponInfoMapper.updateById(couponInfo);
    }

    //获取优惠券规则信息
    @Override
    public Map<String, Object> findCouponRuleList(Long couponId) {
        Map<String, Object> result = new HashMap<>();
        CouponInfo couponInfo = this.getById(couponId);

        //根据优惠券id  获取到优惠券范围列表
        List<CouponRange> couponRangeList = couponRangeMapper.selectList(new QueryWrapper<CouponRange>().eq("coupon_id", couponId));
        //获取到优惠券范围表里得RangeId字段（SPU、三级分类、品牌 对应的id）集合
        List<Long> rangeIdList = couponRangeList.stream().map(CouponRange::getRangeId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(rangeIdList)) {
            if("TRADEMARK".equals(couponInfo.getRangeType())){
                List<BaseTrademark> trademarkList = productFeignClient.findBaseTrademarkByTrademarkIdList(rangeIdList);
                result.put("trademarkList",trademarkList);
            }else if ("SPU".equals(couponInfo.getRangeType())){
                List<SpuInfo> spuInfoList = productFeignClient.findSpuInfoBySpuIdList(rangeIdList);
                result.put("spuInfoList",spuInfoList);
            }else {
                List<BaseCategory3> category3List = productFeignClient.findBaseCategory3ByCategory3IdList(rangeIdList);
                result.put("category3List",category3List);
            }
        }
        return result;
    }

    //根据关键字获取优惠券列表，活动使用
    @Override
    public List<CouponInfo> findCouponByKeyword(String keyword) {
        return couponInfoMapper.selectList(new QueryWrapper<CouponInfo>().like("coupon_name",keyword));
    }

    //获取优惠券信息
    @Override
    public List<CouponInfo> findCouponInfo(Long skuId, Long activityId, Long userId) {
        SkuInfo skuInfo = productFeignClient.getAttrValueList(skuId);
        if(null == skuInfo) return new ArrayList<>();
        //获取普通优惠券
        List<CouponInfo> couponInfoList = couponInfoMapper.selectCouponInfoList(skuInfo.getSpuId(),skuInfo.getCategory3Id(),skuInfo.getTmId(),userId);
        // 获取活动优惠券，活动优惠券为活动范围与优惠券范围的交集
        if (null != activityId) {
            List<CouponInfo> activityCouponInfoList = couponInfoMapper.selectActivityCouponInfoList(skuInfo.getSpuId(), skuInfo.getCategory3Id(), skuInfo.getTmId(), activityId, userId);
            couponInfoList.addAll(activityCouponInfoList);
        }
        return couponInfoList;
    }

    //领取优惠券
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void getCouponInfo(Long couponId, Long userId) {
        //判断优惠券是否领完
        CouponInfo couponInfo = this.getById(couponId);
        //如果优惠券已领用次数大于等于最大领用次数 那么就抛异常
        if(couponInfo.getTakenCount() >= couponInfo.getLimitNum()){
            throw new GmallException(ResultCodeEnum.COUPON_LIMIT_GET);
        }
        //判断该用户是否已经领取了优惠券，一个用户只能领取一次
        QueryWrapper<CouponUse> couponUseQueryWrapper = new QueryWrapper<>();
        couponUseQueryWrapper.eq("coupon_id",couponId);
        couponUseQueryWrapper.eq("user_id",userId);
        Integer count = couponUseMapper.selectCount(couponUseQueryWrapper);
        if (count != 0) {
            throw new GmallException(ResultCodeEnum.COUPON_GET);
        }
        //更新领取个数
        couponInfo.setTakenCount(couponInfo.getTakenCount() + 1);
        this.updateById(couponInfo);
        //更新Use表
        CouponUse couponUse = new CouponUse();
        couponUse.setCouponId(couponId);
        couponUse.setUserId(userId);
        couponUse.setCouponStatus(CouponStatus.NOT_USED.name());
        couponUse.setGetTime(new Date());
        couponUse.setExpireTime(couponInfo.getExpireTime());
        couponUseMapper.insert(couponUse);
    }

    //我的优惠券
    @Override
    public IPage<CouponInfo> selectPageByUserId(Page<CouponInfo> pageParam, Long userId) {
        IPage<CouponInfo> page = couponInfoMapper.selectPageByUserId(pageParam,userId);
        return page;
    }

    /**
     * 获取购物项对应的优惠券列表
     * @param cartInfoList
     * @param skuIdToActivityIdMap  这个skuId是否存在对应的活动
     * @param userId  标识当前用户是否领取优惠劵
     * @return
     */
    @Override
    public Map<Long, List<CouponInfo>> findCartCouponInfo(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap, Long userId) {
        //优惠券范围规则数据
        Map<String, List<Long>> rangeToSkuIdMap = new HashMap<>();
        //存储了购物车中所有的skuId对应的skuInfo  Map<skuId,skuInfo>
        Map<Long, SkuInfo> skuIdToSkuInfoMap = new HashMap<>();
        //所有购物项的sku列表  一个购物项对应一个skuInfo
        List<SkuInfo> skuInfoList = new ArrayList<>();

        //循环当前购物车数据
        for (CartInfo cartInfo : cartInfoList) {
            //  当前skuInfo 中 当前优惠劵使用范围 tmId,category3Id,spuId
            SkuInfo skuInfo = productFeignClient.getAttrValueList(cartInfo.getSkuId());
            skuInfoList.add(skuInfo);
            //   skuIdToSkuInfoMap 存储了多个 map<skuId,skuInfo>
            skuIdToSkuInfoMap.put(cartInfo.getSkuId(),skuInfo);
            //  设置sku规则对应优惠劵使用范围规则
            this.setRuleData(skuInfo, rangeToSkuIdMap);
        }
        /**
         * rangeType(范围类型)  1:商品(spuId) 2:品类(三级分类Id) 3:品牌tmId
         * rangeId(范围id) spuId, categoryId , tmId,
         * 同一张优惠券不能包含多个范围类型，同一张优惠券可以对应同一范围类型的多个范围id（即：同一张优惠券可以包含多个spuId）
         * 示例数据：
         * couponId   rangeType   rangeId
         * 1             1             20
         * 1             1             30
         * 2             2             20
         */
        //  查询所有优惠劵，但是没有与skuId 做对应关系
        List<CouponInfo> allCouponInfoList = couponInfoMapper.selectCartCouponInfoList(skuInfoList,userId);
        //  根据规则关联优惠券对应的skuId列表   从 allCouponInfoList 优惠券中取出一个skuId对应的优惠券列表
        for (CouponInfo couponInfo : allCouponInfoList) {
            //  获取到对应的范围类型
            String rangeType = couponInfo.getRangeType();
            //  获取到rangeId
            Long rangeId = couponInfo.getRangeId();
            //  优惠劵：可以对应多个skuId, 那么哪些skuId 是参与了活动{活动优惠劵} ，哪些skuId 没有参与活动。
            //优惠券分为活动优惠券 + 非活动优惠券  ActivityId不为空说明是活动优惠券
            if (null != couponInfo.getActivityId()) {
                //skuIdToActivityIdMap 中存储的是参加活动的skuId
                //  声明一个集合来存储活动优惠券的 skuId
                List<Long> skuIdList = new ArrayList<>();
                //  所有skuId对应的活动id  Map<skuId, activityId>  一个活动id可能有多个相同的skuId与他对应
                Iterator<Map.Entry<Long, Long>> iterator = skuIdToActivityIdMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Long, Long> entry = iterator.next();
                    Long skuId = entry.getKey();
                    Long activityId = entry.getValue();
                    //找到活动对应的skuId ，活动优惠券取交集  判断你的活动id下 有哪些skuId 对应的优惠券   if = true  说明当前优惠券与当前商品是同一个活动
                    if (activityId.longValue() == couponInfo.getActivityId().longValue()) {
                        //根据skuId 对应找到skuInfo 然后找到优惠券
                        SkuInfo skuInfo = skuIdToSkuInfoMap.get(skuId);
                        //  判断活动范围是否是spu的名称，获取交集。
                        if(rangeType.equals(CouponRangeType.SPU.name())){
                            if (skuInfo.getSpuId().longValue() == rangeId.longValue()) {
                                skuIdList.add(skuId);
                            }
                            //  判断活动范围是否是三级分类的名称
                        }else if (rangeType.equals(CouponRangeType.CATAGORY.name())){
                            if (skuInfo.getCategory3Id().longValue() == rangeId.longValue()) {
                                skuIdList.add(skuId);
                            }
                            //  判断活动范围是否是品牌
                        }else if(rangeType.equals(CouponRangeType.TRADEMARK.name())){
                            if(skuInfo.getTmId().longValue()==rangeId.longValue()){
                                skuIdList.add(skuId);
                            }
                        }
                    }
                }
                //获取到了优惠券对应的skuId列表
                couponInfo.setSkuIdList(skuIdList);
                //  当前优惠劵中没有对应的skuId，继续循环下一个优惠劵。
            }else {
                //activityId为空   非活动优惠券  普通优惠券
                if(rangeType.equals(CouponRangeType.SPU.name())){
                    //  map中应该存储商品的skuId
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:1:" + rangeId));
                }else if (rangeType.equals(CouponRangeType.CATAGORY.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:2:" + rangeId));
                }else {
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:3:" + rangeId));
                }
            }
        }
        //处理skuId对应的优惠券列表
        Map<Long, List<CouponInfo>> skuIdToCouponInfoListMap = new HashMap<>();
        //  因为一个优惠劵可以对应多个skuId 。
        for (CouponInfo couponInfo : allCouponInfoList) {
            //获取活动，非活动的skuId列表
            List<Long> skuIdList = couponInfo.getSkuIdList();
            if (!CollectionUtils.isEmpty(skuIdList)) {
                for (Long skuId : skuIdList) {
                    if(skuIdToCouponInfoListMap.containsKey(skuId)){
                        List<CouponInfo> couponInfoList = skuIdToCouponInfoListMap.get(skuId);
                        couponInfoList.add(couponInfo);
                    }else {
                        List<CouponInfo> couponInfoList = new ArrayList<>();
                        couponInfoList.add(couponInfo);
                        skuIdToCouponInfoListMap.put(skuId, couponInfoList);
                    }
                }
            }
        }
        return skuIdToCouponInfoListMap;
    }

    //根据购物项获取对应的优惠券列表
    @Override
    public List<CouponInfo> findTradeCouponInfo(List<OrderDetail> orderDetailList, Map<Long, ActivityRule> activityIdToActivityRuleMap, Long userId) {
        //优惠券范围规则数据
        Map<String, List<Long>> rangeToSkuIdMap = new HashMap<>();
        // 初始化数据，后续使用 map<skuId,orderDetail>
        Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
        // 初始化数据，后续使用 map<skuId,skuInfo>
        Map<Long, SkuInfo> skuIdToSkuInfoMap = new HashMap<>();

        //获取所有购物项的sku列表
        List<SkuInfo> skuInfoList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            SkuInfo skuInfo = productFeignClient.getAttrValueList(orderDetail.getSkuId());
            skuInfoList.add(skuInfo);
            //map<skuId,orderDetail>
            skuIdToOrderDetailMap.put(orderDetail.getSkuId(),orderDetail);
            //map<skuId,skuInfo>
            skuIdToSkuInfoMap.put(orderDetail.getSkuId(),skuInfo);
            //设置规则数据
            //  this.setRuleData(skuInfo, orderDetail.getSkuId(), rangeToSkuIdMap);
            this.setRuleData(skuInfo,rangeToSkuIdMap);
        }

        /**
         * rangeType(范围类型)  1:商品(spuid) 2:品类(三级分类id) 3:品牌
         * rangeId(范围id)
         * 同一张优惠券不能包含多个范围类型，同一张优惠券可以对应同一范围类型的多个范围id（即：同一张优惠券可以包含多个spuId）
         * 示例数据：  allCouponInfoList
         * couponId   rangeType   rangeId
         * 1             1             20  小米 skuId  41，42，43
         * 1             1             30   华为  skuId  44，45
         * 2             1             20
         */
        if(CollectionUtils.isEmpty(skuInfoList)) return new ArrayList<>();
        //查询用户的优惠券列表  只获取当前用户已领取的所有优惠券
        List<CouponInfo> allCouponInfoList = couponInfoMapper.selectTradeCouponInfoList(skuInfoList,userId);
        //  优惠券列表： 普通优惠券 + 活动优惠券！ 记录到优惠券使用范围规则中：
        for (CouponInfo couponInfo : allCouponInfoList) {
            // 获取使用范围
            String rangeType = couponInfo.getRangeType();
            // tmId,spuId,category3Id
            Long rangeId = couponInfo.getRangeId();

            //如果是促销活动优惠券，那么该优惠券只能作用于该促销活动的sku
            if(null != couponInfo.getActivityId()){
                Iterator<Map.Entry<Long, ActivityRule>> iterator = activityIdToActivityRuleMap.entrySet().iterator();
                while (iterator.hasNext()){
                    Map.Entry<Long, ActivityRule> entry = iterator.next();
                    Long activityId = entry.getKey();
                    ActivityRule activityRule = entry.getValue();
                    //说明是当前促销活动的优惠券 该优惠券只能作用于当前促销活动的sku
                    if(activityId.longValue() == couponInfo.getActivityId()){
                        //活动对应的skuId  判断这个活动下 有哪些商品
                        List<Long> activitySkuIdList = activityRule.getSkuIdList();
                        List<Long> skuIdList = new ArrayList<>();
                        //判断skuId是否在优惠券范围,如果在，则加入skuIdList
                        for (Long skuId : activitySkuIdList) {
                            SkuInfo skuInfo = skuIdToSkuInfoMap.get(skuId);
                            if(rangeType.equals(CouponRangeType.SPU.name())) {
                                if(skuInfo.getSpuId().longValue() == rangeId.longValue()) {
                                    skuIdList.add(skuId);
                                }
                            } else if (rangeType.equals(CouponRangeType.CATAGORY.name())) {
                                if(skuInfo.getCategory3Id().longValue() == rangeId.longValue()) {
                                    skuIdList.add(skuId);
                                }
                            } else {
                                if(skuInfo.getTmId().longValue() == rangeId.longValue()) {
                                    skuIdList.add(skuId);
                                }
                            }
                        }
                        couponInfo.setSkuIdList(skuIdList);
                    }
                }
            }else {
                //普通优惠券
                if(rangeType.equals(CouponRangeType.SPU.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:1:" + rangeId));
                }else if(rangeType.equals(CouponRangeType.CATAGORY.name())){
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:2:" + rangeId));
                }else {
                    couponInfo.setSkuIdList(rangeToSkuIdMap.get("range:3:" + rangeId));
                }
            }
        }

        //找到优惠券对应的skuId集合
        //外层声明一个集合来存储最新的优惠券列表
        List<CouponInfo> resultCouponInfoList = new ArrayList<>();

        //以优惠券id进行分组  map<couponId,List<CouponInfo>>
        Map<Long, List<CouponInfo>> couponIdToListMap = allCouponInfoList.stream().collect(Collectors.groupingBy(CouponInfo::getId));
        // 循环遍历这个集合
        Iterator<Map.Entry<Long, List<CouponInfo>>> iterator = couponIdToListMap.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<Long, List<CouponInfo>> entry = iterator.next();
            //当前优惠券id 对应的优惠券列表
            List<CouponInfo> couponInfoList = entry.getValue();
            //声明一个skuIdList集合  存放 小米 skuId  41，42，43  华为  skuId  44，45
            List<Long> skuIdList = new ArrayList<>();
            for (CouponInfo couponInfo : couponInfoList) {
                skuIdList.addAll(couponInfo.getSkuIdList());
            }
            //获取到当前的优惠券，给skuIdList属性赋值
            CouponInfo couponInfo = couponInfoList.get(0);
            couponInfo.setSkuIdList(skuIdList); //存放 小米 skuId  41，42，43  华为  skuId  44，45
            //  这个优惠券 做了变更！  合并之后
            resultCouponInfoList.add(couponInfo);
        }

        //计算优惠券的最优规则
        //  购物券类型 1 现金券 2 折扣券 3 满减券 4 满件打折券

        // 记录最优选项金额
        BigDecimal checkedAmount = new BigDecimal("0");
        //记录最优优惠券
        CouponInfo checkedCouponInfo = null;
        //循环遍历所有优惠券列表 1.记录总金额  2.记录有多少个
        for (CouponInfo couponInfo : resultCouponInfoList) {
            //获取该优惠券对应的skuId列表
            List<Long> skuIdList = couponInfo.getSkuIdList();
            //记录该优惠券的总金额
            BigDecimal totalAmount = new BigDecimal(0);
            //该优惠券对应的购物项总个数
            int totalNum = 0;
            for (Long skuId : skuIdList) {
                //通过skuId获取订单明细
                OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                //计算每个orderDetail的金额
                BigDecimal skuAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                //将每次遍历的金额加在一起
                totalAmount = totalAmount.add(skuAmount);
                //总数量
                totalNum += orderDetail.getSkuNum();
            }

            /*
             * reduceAmount: 优惠后减少金额
             * isChecked:    是否最优选项（1：最优）
             * isSelect:     是否可选（1：表示满足该优惠券使用条件，用户可以选择该优惠券）
             */
            // 优惠后减少金额
            BigDecimal reduceAmount = new BigDecimal("0");
            // 购物券类型 1 现金券 2 折扣券 3 满减券 4 满件打折券
            if(couponInfo.getCouponType().equals(CouponType.CASH.name())){
                //1 现金券
                reduceAmount = couponInfo.getBenefitAmount();
                //标记可选
                couponInfo.setIsSelect(1);
            }else if(couponInfo.getCouponType().equals(CouponType.DISCOUNT.name())){
                //2 折扣券
                reduceAmount = totalAmount.subtract(totalAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal("10"))));
                //标记可选
                couponInfo.setIsSelect(1);
            }else if (couponInfo.getCouponType().equals(CouponType.FULL_REDUCTION.name())){
                //3 满减券
                if(totalAmount.compareTo(couponInfo.getConditionAmount()) > -1){
                    reduceAmount = couponInfo.getBenefitAmount();
                    //标记可选
                    couponInfo.setIsSelect(1);
                }
            }else {
                //4 满件打折券
                if(totalNum >= couponInfo.getConditionNum()){
                    BigDecimal skuDiscountTotalAmount1 = totalAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal("10")));
                    reduceAmount = totalAmount.subtract(skuDiscountTotalAmount1);
                    //标记可选
                    couponInfo.setIsSelect(1);
                }
            }

            //  reduceAmount 计算最优的价格，checkedAmount 选中的最优金额
            if(reduceAmount.compareTo(checkedAmount) > 0){
                //赋值最优金额为当前金额
                checkedAmount = reduceAmount;
                //设置最优优惠券为当前优惠券
                checkedCouponInfo = couponInfo;
            }
            //优惠后减少金额
            couponInfo.setReduceAmount(reduceAmount);
        }


        //如果最优优惠券存在，则默认设置为选中
        if(checkedCouponInfo != null){
            for (CouponInfo couponInfo : resultCouponInfoList) {
                //  根据优惠券Id 进行比较，找出我们计算最优的那个优惠券！
                if(couponInfo.getId().longValue() == checkedCouponInfo.getId().longValue()){
                    //  要将最优的那个优惠券选中！
                    couponInfo.setIsChecked(1);
                }
            }
        }

        return resultCouponInfoList;
    }

    //更新优惠券使用状态
    @Override
    public void updateCouponInfoUseStatus(Long couponId, Long userId, Long orderId) {
        CouponUse couponUse = new CouponUse();
        couponUse.setOrderId(orderId);
        couponUse.setCouponStatus(CouponStatus.USE_RUN.name());
        couponUse.setUsingTime(new Date()); //使用时间

        QueryWrapper<CouponUse> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("coupon_id",couponId);
        queryWrapper.eq("user_id",userId);
        couponUseMapper.update(couponUse,queryWrapper);
    }

    //  设置规则 既有活动的skuId,也有非活动的skuId.
    private void setRuleData(SkuInfo skuInfo, Map<String, List<Long>> rangeToSkuIdMap) {
        //  为了方便快速查询将规则放入map集合中
        String key1 = "range:1:" + skuInfo.getSpuId();
        // 判断当前map中是否有这个key
        if(rangeToSkuIdMap.containsKey(key1)){
            //获取数据
            List<Long> skuIdList = rangeToSkuIdMap.get(key1);
            skuIdList.add(skuInfo.getId());
        }else {
            List<Long> skuIdList = new ArrayList<>();
            skuIdList.add(skuInfo.getId());
            rangeToSkuIdMap.put(key1,skuIdList);
        }

        String key2 = "range:2:" + skuInfo.getCategory3Id();
        if(rangeToSkuIdMap.containsKey(key2)){
            List<Long> skuIdList = rangeToSkuIdMap.get(key2);
            skuIdList.add(skuInfo.getId());
        }else {
            List<Long> skuIdList = new ArrayList<>();
            skuIdList.add(skuInfo.getId());
            rangeToSkuIdMap.put(key2,skuIdList);
        }

        String key3 = "range:3:" + skuInfo.getTmId();
        if(rangeToSkuIdMap.containsKey(key3)){
            List<Long> skuIdList = rangeToSkuIdMap.get(key3);
            skuIdList.add(skuInfo.getId());
        }else {
            List<Long> skuIdList = new ArrayList<>();
            skuIdList.add(skuInfo.getId());
            rangeToSkuIdMap.put(key3,skuIdList);
        }
    }
}
