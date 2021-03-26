package com.atguigu.gmall.task.scheduled;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@EnableScheduling
@Component
@Slf4j
public class ScheduledTask {

    @Autowired
    private RabbitService rabbitService;

    //{秒数} {分钟} {小时} {日期} {月份} {星期} {年份(可为空)}
    @Scheduled(cron = "0/10 * * * * ?")
    public void task1(){
//        System.err.println("来了老弟");
        //  发送一个消息： 任意一个字符串都可以！
        //  在消费的时候，跟这个发送的内容没有关系！ 消费的时候，只查询当天的秒杀商品即可
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,MqConst.ROUTING_TASK_1,"来吧");
    }

    //每晚18点，清空秒杀商品的redis缓存
    @Scheduled(cron = "0 0 18 * * ?")
    public void clearSeckillData(){
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,MqConst.ROUTING_TASK_18,"走吧");
    }
}
