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
     * ????????????
     * @param orderInfo
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrderInfo(OrderInfo orderInfo) {
        orderInfo.sumTotalAmount();
        //??????????????????????????????
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        //????????????????????????
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfo.setCreateTime(new Date());
        //???????????????????????????
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DATE,1);
        orderInfo.setExpireTime(instance.getTime());
        //??????????????????????????????
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        //??????????????????
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuffer tradeBody = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            tradeBody.append(orderDetail.getSkuName()).append(" ");
        }
        if (tradeBody.toString().length() > 100) {
            orderInfo.setTradeBody(tradeBody.toString().substring(0,100));
        }else {
            //??????????????????
            orderInfo.setTradeBody(tradeBody.toString());
        }

        orderInfo.setFeightFee(new BigDecimal(0));
        orderInfo.setOperateTime(orderInfo.getCreateTime());
        //?????????????????????
        BigDecimal activityReduceAmount = this.getActivityReduceAmount(orderInfo);
        orderInfo.setActivityReduceAmount(activityReduceAmount);

        orderInfoMapper.insert(orderInfo);

        //???????????????????????????????????????????????????????????????????????????????????????????????????
        Map<String, BigDecimal> skuIdToReduceAmountMap = this.computeOrderDetailPayAmount(orderInfo);
        //sku?????????????????????
        Map<Long, Long> skuIdToOrderDetailIdMap = new HashMap<>();

        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetail.setCreateTime(new Date());
            //????????????????????????
            BigDecimal splitActivityAmount = skuIdToReduceAmountMap.get("activity:" + orderDetail.getSkuId());
            if(null == splitActivityAmount){
                splitActivityAmount = new BigDecimal(0);
            }
            //?????????????????????????????????
            orderDetail.setSplitActivityAmount(splitActivityAmount);
            //?????????????????????
            BigDecimal splitCouponAmount = skuIdToReduceAmountMap.get("coupon:"+orderDetail.getSkuId());
            if(null == splitCouponAmount) {
                splitCouponAmount = new BigDecimal(0);
            }
            //???????????????????????????
            orderDetail.setSplitCouponAmount(splitCouponAmount);
            //?????????????????????
            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
            //?????????????????????      ????????????????????? - ???????????????????????? - ?????????????????????
            BigDecimal payAmount = skuTotalAmount.subtract(splitActivityAmount).subtract(splitCouponAmount);
            //??????????????????
            orderDetail.setSplitTotalAmount(payAmount);

            orderDetailMapper.insert(orderDetail);

            //sku?????????????????????id
            skuIdToOrderDetailIdMap.put(orderDetail.getSkuId(),orderDetail.getId());

            //??????????????? ???????????????????????????????????????
            this.saveActivityAndCouponRecord(orderInfo, skuIdToOrderDetailIdMap);

            //????????????????????????
            this.saveOrderStatusLog(orderInfo.getId(), orderInfo.getOrderStatus());

        }
        //?????????????????????????????????24??????????????????????????????
        rabbitService.sendDelayMessage(
                MqConst.EXCHANGE_DIRECT_ORDER_CANCEL
                , MqConst.ROUTING_ORDER_CANCEL
                ,orderInfo.getId()
                ,MqConst.DELAY_TIME);

        //????????????????????????id????????????
        return orderInfo.getId();
    }

    //??????????????? ???????????????????????????????????????
    private void saveActivityAndCouponRecord(OrderInfo orderInfo, Map<Long, Long> skuIdToOrderDetailIdMap) {
        //??????????????????
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)){
            for (OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                if(null != activityRule){
                    //???????????? skuId??????
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
                            //???????????????
                            orderDetailActivityMapper.insert(orderDetailActivity);
                        }
                    }
                }
            }
        }
        //???????????????
        // ???????????????????????????
        Boolean isUpdateCouponStatus = false;
        CouponInfo couponInfo = orderInfo.getCouponInfo();
        if (couponInfo != null) {
            //???????????????????????????
            List<Long> skuIdList = couponInfo.getSkuIdList();
            if(!CollectionUtils.isEmpty(skuIdList)){
                for (Long skuId : skuIdList) {
                    OrderDetailCoupon orderDetailCoupon = new OrderDetailCoupon();
                    orderDetailCoupon.setCouponId(couponInfo.getId());
                    orderDetailCoupon.setOrderDetailId(skuIdToOrderDetailIdMap.get(skuId));
                    orderDetailCoupon.setCouponId(couponInfo.getId());
                    orderDetailCoupon.setSkuId(skuId);
                    orderDetailCoupon.setCreateTime(new Date());
                    //???????????????
                    orderDetailCouponMapper.insert(orderDetailCoupon);
                    //???????????????????????????
                    if(!isUpdateCouponStatus){
                        activityFeignClient.updateCouponInfoUseStatus(couponInfo.getId(),orderInfo.getUserId(),orderInfo.getId());
                    }
                    isUpdateCouponStatus = true;
                }
            }
        }
    }

    //???????????????????????????????????????????????????????????????????????????????????????????????????
    private Map<String, BigDecimal> computeOrderDetailPayAmount(OrderInfo orderInfo) {
        Map<String, BigDecimal> skuIdToReduceAmountMap = new HashMap<>();
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        if(!CollectionUtils.isEmpty(orderDetailVoList)) {
            for(OrderDetailVo orderDetailVo : orderDetailVoList) {
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                List<OrderDetail> orderDetailList = orderDetailVo.getOrderDetailList();
                if(null != activityRule) {
                    //??????????????? ???????????????
                    BigDecimal reduceAmount = activityRule.getReduceAmount();
                    if(orderDetailList.size() == 1) {
                        skuIdToReduceAmountMap.put("activity:"+orderDetailList.get(0).getSkuId(), reduceAmount);
                    } else {
                        //?????????
                        BigDecimal originalTotalAmount = new BigDecimal(0);
                        for(OrderDetail orderDetail : orderDetailList) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                        }
                        //????????????????????????????????????????????? ????????????=?????? - skuPartReduceAmount
                        BigDecimal skuPartReduceAmount = new BigDecimal(0);
                        if (activityRule.getActivityType().equals(ActivityType.FULL_REDUCTION.name())) {
                            for(int i=0, len=orderDetailList.size(); i<len; i++) {
                                OrderDetail orderDetail = orderDetailList.get(i);
                                if(i < len -1) {
                                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                                    //sku????????????
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

                                    //sku????????????
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
            //sku?????????????????????
            Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
            for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
                skuIdToOrderDetailMap.put(orderDetail.getSkuId(), orderDetail);
            }
            //??????????????????skuId??????
            List<Long> skuIdList = couponInfo.getSkuIdList();
            //????????????????????????
            BigDecimal reduceAmount = couponInfo.getReduceAmount();
            if(skuIdList.size() == 1) {
                //sku?????????????????? = ?????? + ?????????
                BigDecimal skuActicityReduceAmount = skuIdToReduceAmountMap.get("activity:"+skuIdList.get(0));
                if(null == skuActicityReduceAmount) {
                    skuActicityReduceAmount = new BigDecimal(0);
                }
                skuIdToReduceAmountMap.put("coupon:"+skuIdToOrderDetailMap.get(skuIdList.get(0)).getSkuId(), reduceAmount);
            } else {
                //?????????
                BigDecimal originalTotalAmount = new BigDecimal(0);
                for (Long skuId : skuIdList) {
                    OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                    originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                }
                //????????????????????????????????????????????? ????????????=?????? - skuPartReduceAmount
                BigDecimal skuPartReduceAmount = new BigDecimal(0);
                if (couponInfo.getCouponType().equals(CouponType.CASH.name()) || couponInfo.getCouponType().equals(CouponType.FULL_REDUCTION.name())) {
                    for(int i=0, len=skuIdList.size(); i<len; i++) {
                        OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuIdList.get(i));
                        if(i < len -1) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            //sku????????????
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
                            //sku????????????
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

    //?????????????????????
    private BigDecimal getActivityReduceAmount(OrderInfo orderInfo) {
       //??????????????????
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
     * ???????????????
     * @param userId
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        // ??????key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        //?????????????????????
        String tradeNo = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set(tradeNoKey,tradeNo);
        return tradeNo;
    }

    /**
     * ???????????????
     * @param userId ???????????????????????????
     * @param tradeCodeNo   ??????????????????????????????
     * @return
     */
    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        // ??????key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeCodeNo.equals(redisTradeNo);
    }

    /**
     * ???????????????
     * @param userId
     */
    @Override
    public void deleteTradeNo(String userId) {
        // ??????key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // ????????????
        redisTemplate.delete(tradeNoKey);
    }

    /**
     * ????????????????????????
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        // ????????????http://localhost:9001/hasStock?skuId=10221&num=2
        String result = HttpClientUtil
                .doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        // 0??????????????????1????????????
        return "1".equals(result);
    }

    /**
     * ??????????????????
     * @param orderId
     */
    @Override
    public void execExpiredOrder(Long orderId) {
        //?????????????????????????????????
        updateOrderStatus(orderId,ProcessStatus.CLOSED);

        //?????????????????????   PaymentReceiver????????????
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
    }

    /**
     * ??????????????????
     * @param orderId
     * @param flag
     */
    @Override
    public void execExpiredOrder(Long orderId, String flag) {
        this.updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if("2".equals(flag)){
            //???????????????????????????????????????????????????
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }

    /**
     * ????????????Id ?????????????????????
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
     * ????????????id?????????????????????
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo != null) {
            //????????????????????????
            List<OrderDetail> detailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderId));
            orderInfo.setOrderDetailList(detailList);
        }
        return orderInfo;
    }

    /**
     * ???????????????????????????
     * @param orderId
     */
    @Override
    public void sendOrderStatus(Long orderId) {
        //?????????????????????  ???????????????
        this.updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);
        // ?????????
        String wareJson = initWareOrder(orderId);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK,MqConst.ROUTING_WARE_STOCK,wareJson);
    }

    /**
     * ???????????????json?????????
     * @param orderId
     * @return
     */
    private String initWareOrder(Long orderId) {
        OrderInfo orderInfo = this.getOrderInfo(orderId);

        //???orderInfo??????????????????????????????map??????,??????map???????????????????????????
        Map map = this.initWareOrder(orderInfo);
        //???map?????????????????????
        return JSON.toJSONString(map);
    }

    /**
     * ???orderInfo???????????????????????????Map??????
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
        map.put("wareId", orderInfo.getWareId());// ??????Id ????????????????????????????????????

        /*
        details:[{skuId:101,skuNum:1,skuName:????????????64G???}
                ,{skuId:201,skuNum:1,skuName:??????????????????}]
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
     * ??????
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderInfo> orderSplit(Long orderId, String wareSkuMap) {
        ArrayList<OrderInfo> orderInfoArrayList = new ArrayList<>();
    /*
    1.  ???????????????????????? 107
    2.  ???w bareSkuMap ????????????????????????????????? [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        ????????????class Param{
                    private String wareId;
                    private List<String> skuIds;
                }
        ????????????????????????Map mpa.put("wareId",value); map.put("skuIds",value)

    3.  ??????????????????????????? 108 109 ?????????
    4.  ??????????????????
    5.  ???????????????????????????
    6.  ???????????????????????????
    7.  ??????
     */
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        if (maps != null) {
            for (Map map : maps) {
                String wareId = (String) map.get("wareId");

                List<String> skuIds = (List<String>) map.get("skuIds");

                OrderInfo subOrderInfo = new OrderInfo();
                // ????????????
                BeanUtils.copyProperties(orderInfoOrigin, subOrderInfo);
                // ??????????????????
                subOrderInfo.setId(null);
                subOrderInfo.setParentOrderId(orderId);
                // ????????????Id
                subOrderInfo.setWareId(wareId);
                // ????????????
                String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
                subOrderInfo.setOutTradeNo(outTradeNo);

                // ????????????????????????: ?????????????????????
                // ????????????????????????
                // ??????????????????????????????????????????
                ArrayList<OrderDetail> subOrderDetailList = new ArrayList<>();

                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                // ??????????????????????????????????????????????????????
                if (orderDetailList != null && orderDetailList.size() > 0) {
                    for (OrderDetail orderDetail : orderDetailList) {
                        // ??????????????????????????????Id
                        for (String skuId : skuIds) {
                            if (Long.parseLong(skuId) == orderDetail.getSkuId().longValue()) {
                                OrderDetail subOrderDetail = new OrderDetail();
                                BeanUtils.copyProperties(orderDetail, subOrderDetail);
                                subOrderDetail.setId(null);
                                // ??????????????????????????????
                                subOrderDetailList.add(subOrderDetail);
                            }
                        }
                    }
                }
                subOrderInfo.setOrderDetailList(subOrderDetailList);

                // ??????????????????????????????????????????
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

                //?????????????????????
                for (OrderDetail subOrderDetail : subOrderDetailList) {
                    subOrderDetail.setOrderId(subOrderInfo.getId());
                    orderDetailMapper.insert(subOrderDetail);
                }

                // ????????????????????????
                List<OrderStatusLog> orderStatusLogList = orderStatusLogMapper.selectList(new QueryWrapper<OrderStatusLog>().eq("order_id", orderId));
                for(OrderStatusLog orderStatusLog : orderStatusLogList) {
                    OrderStatusLog subOrderStatusLog = new OrderStatusLog();
                    BeanUtils.copyProperties(orderStatusLog, subOrderStatusLog);
                    subOrderStatusLog.setId(null);
                    subOrderStatusLog.setOrderId(subOrderInfo.getId());
                    orderStatusLogMapper.insert(subOrderStatusLog);
                }
                // ?????????????????????????????????
                orderInfoArrayList.add(subOrderInfo);
            }
        }
        // ???????????????????????????
        updateOrderStatus(orderId, ProcessStatus.SPLIT);
        return orderInfoArrayList;
    }

    //????????????????????????
    @Override
    public void saveOrderStatusLog(Long orderId, String orderStatus) {
        // ??????????????????
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(orderStatus);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}
