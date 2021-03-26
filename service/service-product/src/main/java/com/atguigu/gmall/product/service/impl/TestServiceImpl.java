package com.atguigu.gmall.product.service.impl;

import com.alibaba.nacos.client.utils.StringUtils;
import com.atguigu.gmall.product.service.TestService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {


    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void testLock() throws InterruptedException {
        //获取对象
        RLock lock = redissonClient.getLock("lock");
        //加锁
//        lock.lock();
        lock.lock(10,TimeUnit.SECONDS);  //十秒后自动解锁  无需手动解锁

        //业务逻辑
        String value = redisTemplate.opsForValue().get("num");
        //判断是否为空
        if(StringUtils.isEmpty(value)){
            return;
        }
        //数据转换
        int num = Integer.parseInt(value);
        //放入缓存
        redisTemplate.opsForValue().set("num", String.valueOf(++num));
        //解锁
//        lock.unlock();
    }

//    @Override
//    public void testLock() throws InterruptedException {
//        //setnx lock ok
////        Boolean flag = redisTemplate.opsForValue().setIfAbsent("lock", "ok");
//
//        //set lock ok px 10000 nx   这种方式即使下面业务出现异常  也会自动释放锁
////        Boolean flag = redisTemplate.opsForValue().setIfAbsent("lock", "ok",3, TimeUnit.SECONDS);
//
//        //使用UUID防止误删锁    set lock uuid px 10000 nx
//        String uuid = UUID.randomUUID().toString();
//        Boolean flag = redisTemplate.opsForValue().setIfAbsent("lock", uuid,1, TimeUnit.SECONDS);
//
//        //判断flag
//        if(flag){
//            //上锁成功，执行业务逻辑
//
//            // 查询redis中的num值
//            String value = redisTemplate.opsForValue().get("num");
//            // 没有该值return
//            if (StringUtils.isEmpty(value)){
//                return ;
//            }
//            // 有值就转成成int
//            int num = Integer.parseInt(value);
//            // 把redis中的num值+1
//            redisTemplate.opsForValue().set("num", String.valueOf(++num));
//
//            //判断缓存中的uuid与代码块生成的uuid是否一致
////            if(uuid.equals(redisTemplate.opsForValue().get("lock"))){
////                //释放锁
////                redisTemplate.delete("lock");
////            }
//            //声明一个lua 脚本
//            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
//
//            //执行lua 脚本
//            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
//            //将lua 脚本放入到DefaultRedisScript 对象中
//            redisScript.setScriptText(script);
//            //设置DefaultRedisScript对象的泛型
//            redisScript.setResultType(Long.class);
//            //执行删除
//            redisTemplate.execute(redisScript, Arrays.asList("lock"),uuid);
//
//        }else {
//            //其他线程等待
//            Thread.sleep(1000);
//            //睡醒之后重试
//            testLock();
//        }
//
//    }
}
