package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class OrderReceiver {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Autowired
    private RabbitService rabbitService;

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel){
        try {
            if (null != orderId) {
                //防止重复消费
                OrderInfo orderInfo = orderService.getById(orderId);
                //订单信息不为空 并且 订单未支付
                if(null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                    //将订单状态修改为已关闭
                    //orderService.execExpiredOrder(orderId);
                    //订单创建时，就是未付款，判断是否有交易记录产生
                    PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                    if(null != paymentInfo && paymentInfo.getPaymentStatus().equals(PaymentStatus.UNPAID.name())){
                        //先查看是否有交易记录（用户是否扫了二维码）
                        Boolean aBoolean = paymentFeignClient.checkPayment(orderId);
                        if(aBoolean){
                            //有交易记录，关闭支付宝 防止用户在过期时间到的那一个瞬间，付款
                            Boolean flag = paymentFeignClient.closePay(orderId);
                            if (flag) {
                                //用户未付款，开始关闭订单，关闭交易记录 "2":表示要关闭交易记录paymentInfo中有数据
                                orderService.execExpiredOrder(orderId,"2");
                            }else {
                                //用户已付款
                                rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                            }
                        }else {
                            //在支付宝中没有交易记录，但是在电商中有交易记录
                            orderService.execExpiredOrder(orderId,"2");
                        }
                    }else {
                        //在paymentInfo中根本没有记录
                        orderService.execExpiredOrder(orderId,"1");
                    }
                }
            }
        } catch (Exception e) {
            //出现异常，返回Nack
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true);
            //发短信的方式通知管理员，查看并解决问题
            e.printStackTrace();
        }
        //手动确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }



    //订单支付成功，更改订单状态为PAID
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,durable = "true",autoDelete = "false"),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void updateOrderStatus(Long orderId,Message message,Channel channel) throws IOException {
        try {
            if (orderId != null) {
                //开始的时候状态应该是UNPAID,将UNPAID改为PAID
                OrderInfo orderInfo = orderService.getById(orderId);
                if (orderInfo != null && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name()) && orderInfo.getProcessStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())) {
                    //支付状态和进度状态都是未支付的情况下，更新订单状态为PAID
                    orderService.updateOrderStatus(orderId,ProcessStatus.PAID);

                    //发消息通知库存系统 减库存 减库存的消息队列消费端接口要什么参数，就发送什么
                    orderService.sendOrderStatus(orderId);

                }
            }
        } catch (Exception e) {
            //消息重入队列
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,true);
            e.printStackTrace();
            //消息只重回一次
            return;
        }
        //消息手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    //监听减库存消息队列
    @RabbitListener(queues = MqConst.QUEUE_WARE_ORDER)
    public void updateOrder(String jsonStr, Message message, Channel channel) throws IOException {
        //获取到这个字符串  {"orderId":"138","status":"DEDUCTED"}
        if(!StringUtils.isEmpty(jsonStr)){
            Map map = JSON.parseObject(jsonStr, Map.class);
            String status = (String) map.get("status");
            String orderId = (String) map.get("orderId");

            if("DEDUCTED".equals(status)){
                //已减库存，更新订单状态为 待发货
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.WAITING_DELEVER);
            }else {
                // 超卖
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
                // 解决方案，通知人工客服补货，再次手动更新消息
            }
        }
        //确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }


}
