package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsApiController {

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @ApiOperation("返回秒杀商品列表")
    @GetMapping("/findAll")
    public Result findAll(){
        return Result.ok(seckillGoodsService.findAll());
    }

    @ApiOperation("根据skuId获取秒杀商品详情对象")
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable Long skuId){
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    //添加一个下单码，用来限制秒杀按钮，只有获取到下单码，才能进入秒杀方法进行秒杀
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        if (seckillGoods != null) {
            Date currentTime = new Date();
            if(DateUtil.dateCompare(seckillGoods.getStartTime(),currentTime)
                    && DateUtil.dateCompare(currentTime,seckillGoods.getEndTime())){
                //根据userId 生成下单码，放入redis缓存
                String skuIdStr = MD5.encrypt(userId);
                return Result.ok(skuIdStr);
            }
        }
        return Result.fail().message("获取下单码失败");
    }


    //根据用户和商品id实现秒杀下单(在商品详情页点击立即抢购) UserRecode类中的两个字段
    //http://api.gmall.com/api/activity/seckill/auth/seckillOrder/44?skuIdStr=c81e728d9d4c2f636f067f89cc14862c
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable Long skuId,HttpServletRequest request){
        //校验下单码
        String userId = AuthContextHolder.getUserId(request);
        String skuIdStr = request.getParameter("skuIdStr");
        if(!skuIdStr.equals(MD5.encrypt(userId))){
            //下单码不一致，请求不合法
            Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        //产品标识， 1：可以秒杀 0：秒杀结束  验证状态位：通过map 存储的。
        String state = (String) CacheHelper.get(skuId.toString());
        if("1".equals(state)){
            //状态位为1，表示用户可以秒杀
            UserRecode userRecode = new UserRecode();
            userRecode.setUserId(userId);
            userRecode.setSkuId(skuId);
            //将数据发送到mq进行排队
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER,MqConst.ROUTING_SECKILL_USER,userRecode);
        }else {
            //状态位为0 表示商品已秒杀完毕
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }
        return Result.ok();
    }

    //检查订单
    @GetMapping("/auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        return seckillGoodsService.checkOrder(skuId,userId);
    }

    //秒杀确认订单
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request){
        //获取到用户id
        String userId = AuthContextHolder.getUserId(request);
        //先得到用户想要购买的商品
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if(null == orderRecode){
            return Result.fail().message("非法操作");
        }
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();

        //获取用户地址
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        //声明一个集合来存储订单明细
        List<OrderDetail> orderDetailList = new ArrayList<>();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        orderDetail.setSkuNum(orderRecode.getNum());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        orderDetail.setCreateTime(new Date());
        //添加到集合
        orderDetailList.add(orderDetail);
        //计算总金额  如果有多件商品，这么计算总金额
//        OrderInfo orderInfo = new OrderInfo();
//        orderInfo.setOrderDetailList(orderDetailList);
//        orderInfo.sumTotalAmount();

        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", orderDetailList);
        // 保存总金额
        result.put("totalAmount", seckillGoods.getCostPrice());
        return Result.ok(result);
    }

    //提交秒杀订单
    @PostMapping("/auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));

        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (orderId == null) {
            return Result.fail().message("下单失败，请重新操作");
        }
        //删除下单信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);
        //下单记录
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId,orderId.toString());
        return Result.ok(orderId);
    }
}
