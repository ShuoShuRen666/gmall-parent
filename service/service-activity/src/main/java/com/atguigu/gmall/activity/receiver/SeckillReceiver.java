package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import sun.misc.Contended;

import java.util.Date;
import java.util.List;

@Component
public class SeckillReceiver {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    //接收service-task发的消息
    //将商品信息导入缓存，同时更新状态位
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importItemToRedis(String msg, Message message, Channel channel){
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        //查询审核状态为 1 并且库存数量大于0，当天的商品      (当前秒杀的商品)
        queryWrapper.eq("status",1).gt("stock_count",0);
        queryWrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(queryWrapper);
        //将集合数据放入缓存中
        if(!CollectionUtils.isEmpty(seckillGoodsList)){
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                //使用 hash 数据类型保存商品
                // key = seckill:goods  field = skuId
                // 判断缓存中是否有当前key
                Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());
                if (flag) {
                    //当前商品在缓存中已存在，不需要再放入缓存
//                    System.out.println("跳过");
                    continue;
                }
                // 商品id为field ，对象为value 放入缓存  key = seckill:goods field = skuId value=商品字符串
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(),seckillGoods);
                //根据每一个商品的数量把商品按队列的形式放进redis中
                for (Integer i = 0; i < seckillGoods.getStockCount(); i++) {
                    // key = seckill:stock:skuId  value = skuId的值
                    // lpush key value
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId().toString())
                            .leftPush(seckillGoods.getSkuId().toString());
                }
                //通知添加 与 更新状态位，更新为开启
                redisTemplate.convertAndSend("seckillpush",seckillGoods.getSkuId() + ":1");
            }
            //手动确认消息接收成功
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }


    //秒杀用户加入队列   监听队列中的用户UserRecode;
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER,durable = "true",autoDelete = "false"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode,Message message,Channel channel){
        if (userRecode != null) {
            //预下单
            seckillGoodsService.seckillOrder(userRecode.getSkuId(),userRecode.getUserId());
            //  手动确认：
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }

    //秒杀结束清空缓存
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void clearSeckillData(String msg, Message message, Channel channel){
        QueryWrapper<SeckillGoods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status",1);
        queryWrapper.le("end_time",new Date());
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(queryWrapper);
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            //清空缓存
            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId());
        }
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        //  当秒杀结束之后：删除所有的预订单记录！
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);

        //  数据库： 相关的数据：更新掉！
        //  设置更新的数据： status=1 表示审核通过 status = 2 表示秒杀结束
        SeckillGoods seckillGoods = new SeckillGoods();
        seckillGoods.setStatus("2");
        seckillGoodsMapper.update(seckillGoods,queryWrapper);
        //  手动确认！
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

}
