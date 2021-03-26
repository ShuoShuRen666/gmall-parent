package com.atguigu.gmall.mq.receiver;

import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@Configuration
public class DeadLetterReceiver {

    //不需要繁琐的绑定规则了，因为在 DeadLetterMqConfig 配置类中 已经配置好了
    @SneakyThrows
    @RabbitListener(queues = DeadLetterMqConfig.queue_dead_2)  //因为队列一会过期，消息会发送到死信交换机，死信交换机绑定了队列2，所以我们监听队列2就可以得到消息
    public void get(String msg, Message message, Channel channel){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.err.println("接收到的消息：\t 时间：" + simpleDateFormat.format(new Date()) + "内容是：\t" + msg);
        //手动确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
