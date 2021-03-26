package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.order.OrderTradeVo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.config.ThreadPoolConfig;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("api/order")
public class OrderApiController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ActivityFeignClient activityFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @ApiOperation("结算")
    @GetMapping("auth/trade")
    public Result<Map<String,Object>> trade(HttpServletRequest request){
        //获取用户id
        String userId = AuthContextHolder.getUserId(request);
        //获取用户地址
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        //渲染送货清单
        //先得到用户想要购买的商品
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        //声明一个集合来存储订单明细
        List<OrderDetail> detailList = new ArrayList<>();
        for (CartInfo cartInfo : cartCheckedList) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            //添加到集合
            detailList.add(orderDetail);
        }

        //获取购物车中满足条件的促销与优惠券信息
        OrderTradeVo orderTradeVo = activityFeignClient.findTradeActivityAndCoupon(detailList, Long.parseLong(userId));
        List<OrderDetailVo> orderDetailVoList = null;
        //活动优惠金额
        BigDecimal activityReduceAmount = new BigDecimal(0);

        //送货清单明细
        orderDetailVoList = orderTradeVo.getOrderDetailVoList();
        //活动优惠金额
        activityReduceAmount = orderTradeVo.getActivityReduceAmount();

        //计算总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailList);
        orderInfo.setActivityReduceAmount(activityReduceAmount);
        orderInfo.sumTotalAmount();

        // 获取流水号
        String tradeNo = orderService.getTradeNo(userId);

        Map<String,Object> result = new HashMap<>();
        result.put("tradeNo", tradeNo);
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", detailList);
        //保存总金额
        result.put("totalNum",detailList.size());
        result.put("totalAmount",orderInfo.getTotalAmount());

        result.put("activityReduceAmount", orderInfo.getActivityReduceAmount());//促销金额
        result.put("originalTotalAmount", orderInfo.getOriginalTotalAmount());//原价金额
        result.put("orderDetailVoList", orderDetailVoList);

        result.put("couponInfoList", orderTradeVo.getCouponInfoList());//订单优惠券列表

        return Result.ok(result);
    }

    @ApiOperation("提交订单")
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,HttpServletRequest request){
        //获取userId
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        // 获取前台页面的流水号
        String tradeNo = request.getParameter("tradeNo");
        //调用服务层比较方法
        boolean flag = orderService.checkTradeCode(userId, tradeNo);
        if (!flag) {
            //比较失败，用户可能重复提交订单了
            return Result.fail().message("不能重复提交订单！");
        }
        //在缓存中删除流水号
        orderService.deleteTradeNo(userId);

        //创建一个errorList，用来存储打印的异常信息
        List<String> errorList = new ArrayList<>();
        //创建一个futureList，用来存储 CompletableFuture 异步编排
        List<CompletableFuture> futureList = new ArrayList<>();

        //验证库存是否充足
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            //验证库存
            CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                if (!result) {
                    errorList.add(orderDetail.getSkuName() + "库存不足！");
//                    return Result.fail().message(orderDetail.getSkuName() + "库存不足！");
                }
            },threadPoolExecutor);
            futureList.add(checkStockCompletableFuture);

            //验证价格
            CompletableFuture<Void> checkPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                //当前商品的实时价格
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                    //重新查询价格,并将最新价格放入缓存
                    cartFeignClient.loadCartCache(userId);
                    errorList.add(orderDetail.getSkuName() + "价格有变动！");
//                    return Result.fail().message(orderDetail.getSkuName() + "价格有变动！");
                }
            }, threadPoolExecutor);
            futureList.add(checkPriceCompletableFuture);
        }
        //合并线程
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        //打印存贮的异常信息
        if (errorList.size() > 0) {
            return Result.fail().message(StringUtils.join(errorList,","));
        }

        //验证通过保存订单
        Long orderId  = orderService.saveOrderInfo(orderInfo);
        return Result.ok(orderId);
    }


    @ApiOperation("内部调用获取订单数据 + 订单明细")
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable Long orderId){
        return orderService.getOrderInfo(orderId);
    }

    //http://localhost:8204/api/order/orderSplit?orderId=xxx&wareSkuMap=xxx
    @ApiOperation("拆单")
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        // 商品与仓库的关系[{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        String wareSkuMap = request.getParameter("wareSkuMap");
        //返回子订单的json集合，子订单通过拆单得到
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId),wareSkuMap);

        List<Map> maps = new ArrayList<>();
        if(!CollectionUtils.isEmpty(subOrderInfoList)){
            for (OrderInfo orderInfo : subOrderInfoList) {
                //orderInfo 变为map
                Map map = orderService.initWareOrder(orderInfo);
                maps.add(map);
            }
        }
        //maps中的数据是子订单集合想要的数据
        return JSON.toJSONString(maps);
    }

    //秒杀提交订单，秒杀订单不需要做前置判断，直接下单
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo){
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }
}
