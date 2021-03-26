package com.atguigu.gmall.mq.receiver;

import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Component
@Configuration
public class ConfirmReceiver {

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "queue.confirm",autoDelete = "false"),
                    exchange = @Exchange(value = "exchange.confirm",autoDelete = "false"),
                    key = {"routing.confirm"}))
    public void process(Message message, Channel channel){
        System.out.println("RabbitListener:" + new String(message.getBody()));
        // 采用手动应答模式, 手动确认应答更为安全稳定
        //如果手动确定了，再出异常，mq不会通知；如果没有手动确认，抛异常mq会一直通知
        // false 确认一个消息，true 批量确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
