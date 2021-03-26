package com.atguigu.gmall.mq.receiver;
import com.rabbitmq.client.Channel;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class DelayReceiver {

    //基于插件的延迟消息监听
    @SneakyThrows
    @RabbitListener(queues = DelayedMqConfig.queue_delay_1)
    public void get(String msg, Message message,Channel channel){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.err.println("接收到的消息：\t时间:"+simpleDateFormat.format(new Date()) + "\t 内容是：\t"+msg);
        //手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
