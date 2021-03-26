package com.atguigu.gmall.list.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.list.service.SearchService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ListReceiver {

    @Autowired
    private SearchService searchService;

    /**
     * 商品上架
     * @param skuId
     * @param message
     * @param channel
     */
    @RabbitListener(bindings = @QueueBinding(value =
            @Queue(value = MqConst.QUEUE_GOODS_UPPER,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS),
            key = {MqConst.ROUTING_GOODS_UPPER}))
    public void upperGoods(Long skuId, Message message, Channel channel) throws IOException {
        if (null != skuId) {
            //上架商品，把数据添加到es中
            searchService.upperGoods(skuId);
        }
        // false 确认一个消息，true 批量确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    /**
     * 商品下架
     * @param skuId
     * @param message
     * @param channel
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_LOWER,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS,type = ExchangeTypes.DIRECT,durable = "true"),
            key = {MqConst.ROUTING_GOODS_LOWER}))
    public void lowerGood(Long skuId, Message message,Channel channel) throws IOException {
        if(null != skuId){
            //下架商品，把数据从es中删除
            searchService.lowerGoods(skuId);
        }
        // false 确认一个消息，true 批量确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
