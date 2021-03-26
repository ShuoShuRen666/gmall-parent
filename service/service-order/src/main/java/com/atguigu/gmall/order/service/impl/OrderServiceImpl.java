package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.atguigu.gmall.model.enums.CouponType;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.*;
import com.atguigu.gmall.order.mapper.*;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Value("${ware.url}")
    private String WARE_URL; //http://localhost:9001

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;

    @Autowired
    private OrderDetailActivityMapper orderDetailActivityMapper;

    @Autowired
    private OrderDetailCouponMapper orderDetailCouponMapper;

    @Autowired
    private ActivityFeignClient activityFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrderInfo(OrderInfo orderInfo) {
        orderInfo.sumTotalAmount();
        //设置订单状态为未支付
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        //设置订单交易编号
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfo.setCreateTime(new Date());
        //过期时间设置为一天
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DATE,1);
        orderInfo.setExpireTime(instance.getTime());
        //设置进度状态为未支付
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        //获取订单明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuffer tradeBody = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            tradeBody.append(orderDetail.getSkuName()).append(" ");
        }
        if (tradeBody.toString().length() > 100) {
            orderInfo.setTradeBody(tradeBody.toString().substring(0,100));
        }else {
            //当前订单描述
            orderInfo.setTradeBody(tradeBody.toString());
        }

        orderInfo.setFeightFee(new BigDecimal(0));
        orderInfo.setOperateTime(orderInfo.getCreateTime());
        //促销优惠总金额
        BigDecimal activityReduceAmount = this.getActivityReduceAmount(orderInfo);
        orderInfo.setActivityReduceAmount(activityReduceAmount);

        orderInfoMapper.insert(orderInfo);

        //计算购物项分摊的优惠减少金额，按比例分摊，退款时按实际支付金额退款
        Map<String, BigDecimal> skuIdToReduceAmountMap = this.computeOrderDetailPayAmount(orderInfo);
        //sku对应的订单明细
        Map<Long, Long> skuIdToOrderDetailIdMap = new HashMap<>();

        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetail.setCreateTime(new Date());
            //促销活动分摊金额
            BigDecimal splitActivityAmount = skuIdToReduceAmountMap.get("activity:" + orderDetail.getSkuId());
            if(null == splitActivityAmount){
                splitActivityAmount = new BigDecimal(0);
            }
            //设置购物项促销分摊金额
            orderDetail.setSplitActivityAmount(splitActivityAmount);
            //优惠券分摊金额
            BigDecimal splitCouponAmount = skuIdToReduceAmountMap.get("coupon:"+orderDetail.getSkuId());
            if(null == splitCouponAmount) {
                splitCouponAmount = new BigDecimal(0);
            }
            //设置优惠券分摊金额
            orderDetail.setSplitCouponAmount(splitCouponAmount);
            //订单项的总金额
            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
            //优惠后的总金额      订单项的总金额 - 促销活动分摊金额 - 优惠券分摊金额
            BigDecimal payAmount = skuTotalAmount.subtract(splitActivityAmount).subtract(splitCouponAmount);
            //实际支付金额
            orderDetail.setSplitTotalAmount(payAmount);

            orderDetailMapper.insert(orderDetail);

            //sku对应的订单明细id
            skuIdToOrderDetailIdMap.put(orderDetail.getSkuId(),orderDetail.getId());

            //记录订单与 促销活动、优惠券的关联信息
            this.saveActivityAndCouponRecord(orderInfo, skuIdToOrderDetailIdMap);

            //记录订单状态日志
            this.saveOrderStatusLog(orderInfo.getId(), orderInfo.getOrderStatus());

        }
        //发送延迟队列，如果订单24小时未支付，取消订单
        rabbitService.sendDelayMessage(
                MqConst.EXCHANGE_DIRECT_ORDER_CANCEL
                , MqConst.ROUTING_ORDER_CANCEL
                ,orderInfo.getId()
                ,MqConst.DELAY_TIME);

        //后续需要根据订单id进行支付
        return orderInfo.getId();
    }

    //记录订单与 促销活动、优惠券的关联信息
    private void saveActivityAndCouponRecord(OrderInfo orderInfo, Map<Long, Long> skuIdToOrderDetailIdMap) {
        //记录促销活动
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)){
            for (OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                if(null != activityRule){
                    //有活动的 skuId集合
                    List<Long> skuIdList = activityRule.getSkuIdList();
                    if(skuIdList != null && skuIdList.size() > 0){
                        for (Long skuId : skuIdList) {
                            OrderDetailActivity orderDetailActivity = new OrderDetailActivity();
                            orderDetailActivity.setOrderId(orderInfo.getId());
                            orderDetailActivity.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                            orderDetailActivity.setActivityId(activityRule.getActivityId());
                            orderDetailActivity.setActivityRule(activityRule.getId());
                            orderDetailActivity.setSkuId(skuId);
                            orderDetailActivity.setCreateTime(new Date());
                            //保存数据库
                            orderDetailActivityMapper.insert(orderDetailActivity);
                        }
                    }
                }
            }
        }
        //记录优惠券
        // 是否更新优惠券状态
        Boolean isUpdateCouponStatus = false;
        CouponInfo couponInfo = orderInfo.getCouponInfo();
        if (couponInfo != null) {
            //优惠券对应活动列表
            List<Long> skuIdList = couponInfo.getSkuIdList();
            if(!CollectionUtils.isEmpty(skuIdList)){
                for (Long skuId : skuIdList) {
                    OrderDetailCoupon orderDetailCoupon = new OrderDetailCoupon();
                    orderDetailCoupon.setCouponId(couponInfo.getId());
                    orderDetailCoupon.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                    orderDetailCoupon.setCouponId(couponInfo.getId());
                    orderDetailCoupon.setSkuId(skuId);
                    orderDetailCoupon.setCreateTime(new Date());
                    //更新数据库
                    orderDetailCouponMapper.insert(orderDetailCoupon);
                    //更新优惠券使用状态
                    if(!isUpdateCouponStatus){
                        activityFeignClient.updateCouponInfoUseStatus(couponInfo.getId(),orderInfo.getUserId(),orderInfo.getId());
                    }
                    isUpdateCouponStatus = true;
                }
            }
        }
    }

    //计算购物项分摊的优惠减少金额，按比例分摊，退款时按实际支付金额退款
    private Map<String, BigDecimal> computeOrderDetailPayAmount(OrderInfo orderInfo) {
        Map<String, BigDecimal> skuIdToReduceAmountMap = new HashMap<>();
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)) {
            for(OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                List<OrderDetail> orderDetailList = orderDetailVo.getOrderDetailList();
                if(null != activityRule) {
                    //优惠金额， 按比例分摊
                    BigDecimal reduceAmount = activityRule.getReduceAmount();
                    if(orderDetailList.size() == 1) {
                        skuIdToReduceAmountMap.put("activity:"+orderDetailList.get(0).getSkuId(), reduceAmount);
                    } else {
                        //总金额
                        BigDecimal originalTotalAmount = new BigDecimal(0);
                        for(OrderDetail orderDetail : orderDetailList) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                        }
                        //记录除最后一项是所有分摊金额， 最后一项=总的 - skuPartReduceAmount
                        BigDecimal skuPartReduceAmount = new BigDecimal(0);
                        if (activityRule.getActivityType().equals(ActivityType.FULL_REDUCTION.name())) {
                            for(int i=0, len=orderDetailList.size(); i<len; i++) {
                                OrderDetail orderDetail = orderDetailList.get(i);
                                if(i < len -1) {
                                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                                    //sku分摊金额
                                    BigDecimal skuReduceAmount = skuTotalAmount.divide(originalTotalAmount, 2, RoundingMode.HALF_UP).multiply(reduceAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);

                                    skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                                } else {
                                    BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);
                                }
                            }
                        } else {
                            for(int i=0, len=orderDetailList.size(); i<len; i++) {
                                OrderDetail orderDetail = orderDetailList.get(i);
                                if(i < len -1) {
                                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));

                                    //sku分摊金额
                                    BigDecimal skuDiscountTotalAmount = skuTotalAmount.multiply(activityRule.getBenefitDiscount().divide(new BigDecimal("10")));
                                    BigDecimal skuReduceAmount = skuTotalAmount.subtract(skuDiscountTotalAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);

                                    skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                                } else {
                                    BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);
                                }
                            }
                        }
                    }
                }
            }
        }

        CouponInfo couponInfo = orderInfo.getCouponInfo();
        if(null != couponInfo) {
            //sku对应的订单明细
            Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
            for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
                skuIdToOrderDetailMap.put(orderDetail.getSkuId(), orderDetail);
            }
            //优惠券对应的skuId列表
            List<Long> skuIdList = couponInfo.getSkuIdList();
            //优惠券优化总金额
            BigDecimal reduceAmount = couponInfo.getReduceAmount();
            if(skuIdList.size() == 1) {
                //sku总的优化金额 = 促销 + 优惠券
                BigDecimal skuActicityReduceAmount = skuIdToReduceAmountMap.get("activity:"+skuIdList.get(0));
                if(null == skuActicityReduceAmount) {
                    skuActicityReduceAmount = new BigDecimal(0);
                }
                skuIdToReduceAmountMap.put("coupon:"+skuIdToOrderDetailMap.get(skuIdList.get(0)).getSkuId(), reduceAmount);
            } else {
                //总金额
                BigDecimal originalTotalAmount = new BigDecimal(0);
                for (Long skuId : skuIdList) {
                    OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                    originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                }
                //记录除最后一项是所有分摊金额， 最后一项=总的 - skuPartReduceAmount
                BigDecimal skuPartReduceAmount = new BigDecimal(0);
                if (couponInfo.getCouponType().equals(CouponType.CASH.name()) || couponInfo.getCouponType().equals(CouponType.FULL_REDUCTION.name())) {
                    for(int i=0, len=skuIdList.size(); i<len; i++) {
                        OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuIdList.get(i));
                        if(i < len -1) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            //sku分摊金额
                            BigDecimal skuReduceAmount = skuTotalAmount.divide(originalTotalAmount, 2, RoundingMode.HALF_UP).multiply(reduceAmount);
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);

                            skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                        } else {
                            BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);
                        }
                    }
                } else {
                    for(int i=0, len=skuIdList.size(); i<len; i++) {
                        OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuIdList.get(i));
                        if(i < len -1) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            BigDecimal skuDiscountTotalAmount = skuTotalAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal("10")));
                            BigDecimal skuReduceAmount = skuTotalAmount.subtract(skuDiscountTotalAmount);
                            //sku分摊金额
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);

                            skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                        } else {
                            BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);
                        }
                    }
                }
            }
        }
        return skuIdToReduceAmountMap;
    }

    //促销优惠总金额
    private BigDecimal getActivityReduceAmount(OrderInfo orderInfo) {
       //促销优惠金额
        BigDecimal activityReduceAmount = new BigDecimal("0");
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)) {
            for(OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                if(null != activityRule) {
                    activityReduceAmount = activityReduceAmount.add(activityRule.getReduceAmount());
                }
            }
        }
        return activityReduceAmount;
    }

    /**
     * 生产流水号
     * @param userId
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        //定义一个流水号
        String tradeNo = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set(tradeNoKey,tradeNo);
        return tradeNo;
    }

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeCodeNo.equals(redisTradeNo);
    }

    /**
     * 删除流水号
     * @param userId
     */
    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // 删除数据
        redisTemplate.delete(tradeNoKey);
    }

    /**
     * 检查库存是否充足
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        // 远程调用http://localhost:9001/hasStock?skuId=10221&num=2
        String result = HttpClientUtil
                .doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        // 0：没有库存，1：有库存
        return "1".equals(result);
    }

    /**
     * 处理过期订单
     * @param orderId
     */
    @Override
    public void execExpiredOrder(Long orderId) {
        //将订单状态修改为已关闭
        updateOrderStatus(orderId,ProcessStatus.CLOSED);

        //发消息关闭交易   PaymentReceiver消费消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
    }

    /**
     * 更新过期订单
     * @param orderId
     * @param flag
     */
    @Override
    public void execExpiredOrder(Long orderId, String flag) {
        this.updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if("2".equals(flag)){
            //发送消息队列，关闭支付宝的交易记录
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }

    /**
     * 根据订单Id 修改订单的状态
     * @param orderId
     * @param processStatus
     */
    @Override
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    /**
     * 根据订单id，查询订单信息
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo != null) {
            //获取订单明细数据
            List<OrderDetail> detailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderId));
            orderInfo.setOrderDetailList(detailList);
        }
        return orderInfo;
    }

    /**
     * 发送消息给库存系统
     * @param orderId
     */
    @Override
    public void sendOrderStatus(Long orderId) {
        //更改订单状态为  已通知仓储
        this.updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);
        // 发消息
        String wareJson = initWareOrder(orderId);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK,MqConst.ROUTING_WARE_STOCK,wareJson);
    }

    /**
     * 获取发送的json字符串
     * @param orderId
     * @return
     */
    private String initWareOrder(Long orderId) {
        OrderInfo orderInfo = this.getOrderInfo(orderId);

        //将orderInfo中对应的字段，转换为map集合,这个map中只有需要的参数值
        Map map = this.initWareOrder(orderInfo);
        //将map集合转为字符串
        return JSON.toJSONString(map);
    }

    /**
     * 将orderInfo中的部分字段转换为Map集合
     * @param orderInfo
     * @return
     */
    @Override
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！

        /*
        details:[{skuId:101,skuNum:1,skuName:’小米手64G’}
                ,{skuId:201,skuNum:1,skuName:’索尼耳机’}]
         */
        ArrayList<Map> maps = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId",orderDetail.getSkuId());
            orderDetailMap.put("skuNum",orderDetail.getSkuNum());
            orderDetailMap.put("skuName",orderDetail.getSkuName());
            maps.add(orderDetailMap);
        }
        map.put("details",maps);
        return map;
    }

    /**
     * 拆单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderInfo> orderSplit(Long orderId, String wareSkuMap) {
        ArrayList<OrderInfo> orderInfoArrayList = new ArrayList<>();
    /*
    1.  先获取到原始订单 107
    2.  将w bareSkuMap 转换为我们能操作的对象 [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        方案一：class Param{
                    private String wareId;
                    private List<String> skuIds;
                }
        方案二：看做一个Map mpa.put("wareId",value); map.put("skuIds",value)

    3.  创建一个新的子订单 108 109 。。。
    4.  给子订单赋值
    5.  保存子订单到数据库
    6.  修改原始订单的状态
    7.  测试
     */
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        if (maps != null) {
            for (Map map : maps) {
                String wareId = (String) map.get("wareId");

                List<String> skuIds = (List<String>) map.get("skuIds");

                OrderInfo subOrderInfo = new OrderInfo();
                // 属性拷贝
                BeanUtils.copyProperties(orderInfoOrigin, subOrderInfo);
                // 防止主键冲突
                subOrderInfo.setId(null);
                subOrderInfo.setParentOrderId(orderId);
                // 赋值仓库Id
                subOrderInfo.setWareId(wareId);
                // 子订单号
                String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
                subOrderInfo.setOutTradeNo(outTradeNo);

                // 计算子订单的金额: 必须有订单明细
                // 获取到子订单明细
                // 声明一个集合来存储子订单明细
                ArrayList<OrderDetail> subOrderDetailList = new ArrayList<>();

                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                // 表示主主订单明细中获取到子订单的明细
                if (orderDetailList != null && orderDetailList.size() > 0) {
                    for (OrderDetail orderDetail : orderDetailList) {
                        // 获取子订单明细的商品Id
                        for (String skuId : skuIds) {
                            if (Long.parseLong(skuId) == orderDetail.getSkuId().longValue()) {
                                OrderDetail subOrderDetail = new OrderDetail();
                                BeanUtils.copyProperties(orderDetail, subOrderDetail);
                                subOrderDetail.setId(null);
                                // 将订单明细添加到集合
                                subOrderDetailList.add(subOrderDetail);
                            }
                        }
                    }
                }
                subOrderInfo.setOrderDetailList(subOrderDetailList);

                // 重新计算子订单和订单明细金额
                BigDecimal totalAmount = new BigDecimal("0");
                BigDecimal originalTotalAmount = new BigDecimal("0");
                BigDecimal couponAmount = new BigDecimal("0");
                BigDecimal activityReduceAmount = new BigDecimal("0");
                for (OrderDetail subOrderDetail : subOrderDetailList) {
                    BigDecimal skuTotalAmount = subOrderDetail.getOrderPrice().multiply(new BigDecimal(subOrderDetail.getSkuNum()));
                    originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                    totalAmount = totalAmount.add(skuTotalAmount).subtract(subOrderDetail.getSplitCouponAmount()).subtract(subOrderDetail.getSplitActivityAmount());
                    couponAmount = couponAmount.add(subOrderDetail.getSplitCouponAmount());
                    activityReduceAmount = activityReduceAmount.add(subOrderDetail.getSplitActivityAmount());
                }
                subOrderInfo.setTotalAmount(totalAmount);
                subOrderInfo.setOriginalTotalAmount(originalTotalAmount);
                subOrderInfo.setCouponAmount(couponAmount);
                subOrderInfo.setActivityReduceAmount(activityReduceAmount);
                subOrderInfo.setFeightFee(new BigDecimal(0));
                orderInfoMapper.insert(subOrderInfo);

                //保存子订单明细
                for (OrderDetail subOrderDetail : subOrderDetailList) {
                    subOrderDetail.setOrderId(subOrderInfo.getId());
                    orderDetailMapper.insert(subOrderDetail);
                }

                // 保存订单状态记录
                List<OrderStatusLog> orderStatusLogList = orderStatusLogMapper.selectList(new QueryWrapper<OrderStatusLog>().eq("order_id", orderId));
                for(OrderStatusLog orderStatusLog : orderStatusLogList) {
                    OrderStatusLog subOrderStatusLog = new OrderStatusLog();
                    BeanUtils.copyProperties(orderStatusLog, subOrderStatusLog);
                    subOrderStatusLog.setId(null);
                    subOrderStatusLog.setOrderId(subOrderInfo.getId());
                    orderStatusLogMapper.insert(subOrderStatusLog);
                }
                // 将子订单添加到集合中！
                orderInfoArrayList.add(subOrderInfo);
            }
        }
        // 修改原始订单的状态
        updateOrderStatus(orderId, ProcessStatus.SPLIT);
        return orderInfoArrayList;
    }

    //记录订单状态日志
    @Override
    public void saveOrderStatusLog(Long orderId, String orderStatus) {
        // 记录订单状态
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(orderStatus);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}
