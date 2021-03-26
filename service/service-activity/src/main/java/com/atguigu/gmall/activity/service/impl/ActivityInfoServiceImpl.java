package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.ActivityInfoMapper;
import com.atguigu.gmall.activity.mapper.ActivityRuleMapper;
import com.atguigu.gmall.activity.mapper.ActivitySkuMapper;
import com.atguigu.gmall.activity.mapper.CouponInfoMapper;
import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.model.activity.*;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.cart.CartInfoVo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActivityInfoServiceImpl extends ServiceImpl<ActivityInfoMapper, ActivityInfo> implements ActivityInfoService {

    @Autowired
    private ActivityInfoMapper activityInfoMapper;

    @Autowired
    private ActivityRuleMapper activityRuleMapper;

    @Autowired
    private ActivitySkuMapper activitySkuMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CouponInfoMapper couponInfoMapper;

    //分页查询活动列表
    @Override
    public IPage<ActivityInfo> getPage(Page<ActivityInfo> pageParam) {
        QueryWrapper<ActivityInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        IPage<ActivityInfo> activityInfoIPage = activityInfoMapper.selectPage(pageParam, queryWrapper);
        //细节 活动的数据类型 activityTypeString  该字段在表中不存在
        //getRecords() 返回当前集合
        activityInfoIPage.getRecords().stream().forEach(activityInfo -> {
            activityInfo.setActivityTypeString(ActivityType.getNameByType(activityInfo.getActivityType()));
        });
        return activityInfoIPage;
    }

    //保存活动规则,先删除，再添加
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveActivityRule(ActivityRuleVo activityRuleVo) {
        activityRuleMapper.delete(new QueryWrapper<ActivityRule>().eq("activity_id",activityRuleVo.getActivityId()));
        activitySkuMapper.delete(new QueryWrapper<ActivitySku>().eq("activity_id",activityRuleVo.getActivityId()));

        List<ActivityRule> activityRuleList = activityRuleVo.getActivityRuleList();
        List<ActivitySku> activitySkuList = activityRuleVo.getActivitySkuList();
        //后续处理优惠券id列表  删除优惠券与活动的绑定关系
        CouponInfo couponInfo = new CouponInfo();
        couponInfo.setActivityId(0L);
        couponInfoMapper.update(couponInfo,new QueryWrapper<CouponInfo>().eq("activity_id",activityRuleVo.getActivityId()));

        List<Long> couponIdList = activityRuleVo.getCouponIdList();
        if (!CollectionUtils.isEmpty(couponIdList)) {
            for (Long couponId : couponIdList) {
                CouponInfo couponInfoUpd = couponInfoMapper.selectById(couponId);
                couponInfoUpd.setActivityId(activityRuleVo.getActivityId());
                couponInfoMapper.updateById(couponInfoUpd);
            }
        }

        for (ActivityRule activityRule : activityRuleList) {
            activityRule.setActivityId(activityRuleVo.getActivityId());
            activityRuleMapper.insert(activityRule);
        }
        for (ActivitySku activitySku : activitySkuList) {
            activitySku.setActivityId(activityRuleVo.getActivityId());
            activitySkuMapper.insert(activitySku);
        }
    }

    //根据关键字获取sku列表，活动使用
    @Override
    public List<SkuInfo> findSkuInfoByKeyword(String keyword) {
        //  根据关键词查询所有的skuInfo列表
        List<SkuInfo> skuInfoList = productFeignClient.findSkuInfoByKeyword(keyword);
        //获取当前列表的skuId 组成一个新的集合
        List<Long> skuIdList = skuInfoList.stream().map(SkuInfo::getId).collect(Collectors.toList());
        //获取当前活动列表的skuId
        List<Long> existSkuIdList = activityInfoMapper.selectExistSkuIdList(skuIdList);
        //通过skuId 获取到当前对象的集合
        List<SkuInfo> skuInfos = existSkuIdList.stream().map(skuId ->
                productFeignClient.getAttrValueList(skuId)).collect(Collectors.toList());
        //一个sku只能参加一个活动，搜索时要过滤掉已经参加过还在活动期间活动的sku。
        //removeAll() 底层使用的equals方法  所以再skuInfo中要重写equals方法
        skuInfoList.removeAll(skuInfos);
        return skuInfoList;
    }

    //获取活动规则 点击活动列表 回显数据
    @Override
    public Map<String, Object> findActivityRuleList(Long activityId) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<ActivityRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("activity_id",activityId);
        List<ActivityRule> activityRuleList = activityRuleMapper.selectList(queryWrapper);
        result.put("activityRuleList",activityRuleList);

        QueryWrapper<ActivitySku> activitySkuQueryWrapper = new QueryWrapper<>();
        activitySkuQueryWrapper.eq("activity_id",activityId);
        List<ActivitySku> activitySkuList = activitySkuMapper.selectList(activitySkuQueryWrapper);
        List<Long> skuIdList = activitySkuList.stream().map(ActivitySku::getSkuId).collect(Collectors.toList());

        List<SkuInfo> skuInfoList = productFeignClient.findSkuInfoBySkuIdList(skuIdList);
        result.put("skuInfoList", skuInfoList);

        //回显优惠券列表
        List<CouponInfo> couponInfoList = couponInfoMapper.selectList(new QueryWrapper<CouponInfo>().eq("activity_id", activityId));
        result.put("couponInfoList",couponInfoList);

        return result;
    }

    //根据skuId找到活动规则列表
    @Override
    public List<ActivityRule> findActivityRule(Long skuId) {
        return activityInfoMapper.selectActivityRuleList(skuId);
    }


    //获取购物项对应的活动规则列表
    @Override
    public List<CartInfoVo> findCartActivityRuleMap(List<CartInfo> cartInfoList, Map<Long, Long> skuIdToActivityIdMap) {
        List<CartInfoVo> cartInfoVoList = new ArrayList<>();

        //获取skuId对应的购物项
        Map<Long,CartInfo> skuIdToCartInfoMap = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            skuIdToCartInfoMap.put(cartInfo.getSkuId(),cartInfo);
        }
        //获取到购物车中所有的skuId
        List<Long> skuIdList = cartInfoList.stream().map(CartInfo::getSkuId).collect(Collectors.toList());
        //获取skuIdList对应的活动规则列表
        List<ActivityRule> activityRuleList = activityInfoMapper.selectCartActivityRuleList(skuIdList);
        //根据skuId进行分组，获取skuId对应的活动规则列表  Map<skuId,List<ActivityRule>>
        Map<Long, List<ActivityRule>> skuIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(ActivityRule::getSkuId));
        //                HashMap<Long, List<ActivityRule>> map = new HashMap<>();
        //                for (ActivityRule activityRule : activityRuleList) {
        //                    Long skuId = activityRule.getSkuId();
        //                    if(map.containsKey(skuId)){
        //                        List<ActivityRule> activityRuleList1 = map.get(skuId);
        //                        activityRuleList1.add(activityRule);
        //                    }else {
        //                        List<ActivityRule> activityRuleList1 = new ArrayList<>();
        //                        activityRuleList1.add(activityRule);
        //                        map.put(skuId,activityRuleList1);
        //                    }
        //                }
        //根据活动id分组，获取活动id对应的商品列表  Map<activityId,List<ActivityRule>>
        Map<Long, List<ActivityRule>> activityIdToActivityRuleListAllMap = activityRuleList.stream().collect(Collectors.groupingBy(ActivityRule::getActivityId));
        Iterator<Map.Entry<Long, List<ActivityRule>>> iterator = activityIdToActivityRuleListAllMap.entrySet().iterator();
        //如果仍有元素可以迭代，则返回 true。
        while (iterator.hasNext()){
            //返回迭代的下一个元素。
            Map.Entry<Long, List<ActivityRule>> entry = iterator.next();

            Long activityId = entry.getKey();
            List<ActivityRule> currentActivityRuleList = entry.getValue();
            //获取到活动对应的购物车skuId列表，即activitySkuIdSet列表可以组合使用活动规则
            Set<Long> activitySkuIdSet = currentActivityRuleList.stream().map(ActivityRule::getSkuId).collect(Collectors.toSet());

            CartInfoVo cartInfoVo = new CartInfoVo();
            List<CartInfo> currentCartInfoList = new ArrayList<>();
            for (Long skuId : activitySkuIdSet) {
                skuIdToActivityIdMap.put(skuId,activityId);
                currentCartInfoList.add(skuIdToCartInfoMap.get(skuId));
            }
            cartInfoVo.setCartInfoList(currentCartInfoList);
            //获取该活动的规则
            List<ActivityRule> skuActivityRuleList = skuIdToActivityRuleListMap.get(activitySkuIdSet.iterator().next());
            cartInfoVo.setActivityRuleList(skuActivityRuleList);
            cartInfoVoList.add(cartInfoVo);
        }
        return cartInfoVoList;
    }

    //获取购物项中 活动id 对应的最优活动规则 map<activityId,ActivityRule>
    //计算金额 客户端传递有可能出现造假，所以后台必须重新计算一次
    @Override
    public Map<Long, ActivityRule> findTradeActivityRuleMap(List<OrderDetail> orderDetailList) {
        //购物项activityId 对应的最优活动规则
        Map<Long, ActivityRule> activityIdToActivityRuleMap = new HashMap<>();

        //获取skuId对应的购物项  map<skuId,orderDetail>
        Map<Long,OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
        for (OrderDetail orderDetail : orderDetailList) {
            skuIdToOrderDetailMap.put(orderDetail.getSkuId(),orderDetail);
        }

        //获取当前订单 所有 的skuId
        List<Long> skuIdList = orderDetailList.stream().map(OrderDetail::getSkuId).collect(Collectors.toList());
        //查询skuIdList对应的活动规则列表(当前订单 所有的 活动规则)
        List<ActivityRule> activityRuleList = activityInfoMapper.selectCartActivityRuleList(skuIdList);
        //根据skuId进行分组，获取每一个skuId对应的活动规则 map<skuId,List<ActivityRule>>
        Map<Long, List<ActivityRule>> skuIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(ActivityRule::getSkuId));

        //根据活动id分组，获取每一个activityId 对应的活动规则 map<activityId,List<ActivityRule>>
        Map<Long, List<ActivityRule>> activityIdToActivityRuleListMap = activityRuleList.stream().collect(Collectors.groupingBy(ActivityRule::getActivityId));
        //迭代 map<activityId,List<ActivityRule>>
        Iterator<Map.Entry<Long, List<ActivityRule>>> iterator = activityIdToActivityRuleListMap.entrySet().iterator();
        // 一个循环 一个活动
        while (iterator.hasNext()){
            Map.Entry<Long, List<ActivityRule>> entry = iterator.next();
            Long activityId = entry.getKey();
            List<ActivityRule> currentActivityRuleList = entry.getValue();

            //活动id对应的购物车skuId列表 (当前活动对应的skuId列表)
            Set<Long> activitySkuIdSet = currentActivityRuleList.stream().map(ActivityRule::getSkuId).collect(Collectors.toSet());

            // 该活动的总金额 {如果是满减打折则使用activityTotalAmount}
            BigDecimal activityTotalAmount = new BigDecimal("0");

            //该活动订单明细的件数 {如果是满件打折的时候，需要判断activityTotalNum}
            Integer activityTotalNum = 0;
            //迭代 当前活动对应的skuId列表
            for (Long skuId : activitySkuIdSet) {
                //获取当前sku对应的唯一订单明细
                OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                //当前sku对应的唯一订单明细的总金额(可能购买多件商品)
                BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                //计算这个活动的总金额
                activityTotalAmount = activityTotalAmount.add(skuTotalAmount);
                //计算这个活动的总件数
                activityTotalNum += orderDetail.getSkuNum();
            }

            //获取skuId 对应的该活动规则(这个集合中的skuId都是同一个规则)
            Long skuId = activitySkuIdSet.iterator().next();
            //map<skuId,List<ActivityRule>> 获取skuId对应的规则列表
            List<ActivityRule> skuActivityRuleList = skuIdToActivityRuleListMap.get(skuId);
            for (ActivityRule activityRule : skuActivityRuleList) {
                //  数据库存储的 FULL_REDUCTION(满减)
                if(activityRule.getActivityType().equals(ActivityType.FULL_REDUCTION.name())){
                    //活动总金额大于满减金额额度
                    if(activityTotalAmount.compareTo(activityRule.getConditionAmount()) > -1){
                        //设置优惠后减少的金额
                        activityRule.setReduceAmount(activityRule.getBenefitAmount());
                        //设置好skuId列表
                        activityRule.setSkuIdList(new ArrayList<>(activitySkuIdSet));
                        //活动id对应的最优规则
                        activityIdToActivityRuleMap.put(activityRule.getActivityId(),activityRule);
                        break;
                    }
                }else {
                    //  如果订单项购买个数大于等于满减件数，则优化打折 FULL_DISCOUNT  满件打折
                    if(activityTotalNum >= activityRule.getConditionNum()){
                        //  9折  9/10 = 0.9  totalAmount * 0.9
                        BigDecimal skuDiscountTotalAmount = activityTotalAmount.multiply(activityRule.getBenefitDiscount().divide(new BigDecimal("10")));
                        //总金额 - 打折后金额 = 优惠金额
                        BigDecimal reduceAmount = activityTotalAmount.subtract(skuDiscountTotalAmount);
                        //  设置优惠后的金额reduceAmount
                        activityRule.setReduceAmount(reduceAmount);
                        activityRule.setSkuIdList(new ArrayList<>(activitySkuIdSet));
                        activityIdToActivityRuleMap.put(activityRule.getActivityId(),activityRule);
                        break;
                    }
                }
            }
        }

        return activityIdToActivityRuleMap;
    }

}
