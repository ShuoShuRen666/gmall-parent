package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.RedisConst;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate redisTemplate;

    //编写一个环绕通知
    @SneakyThrows   //小辣椒的处理异常注解
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAround(ProceedingJoinPoint joinPoint){
        /*
            1.  获取方法上的注解
            2.  获取到注解的前缀 组成缓存的key
            3.  根据这个key 获取缓存数据
                true:
                    则直接返回
                false:
                    则需要查询数据库，并防止缓存击穿+防止缓存穿透
         */

        Object object = new Object();
        //将Signature转换为方法级别
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        //1.获取方法上的注解
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);

        //2.获取到注解的前缀
        String prefix = gmallCache.prefix();
        //组成缓存的key  key = skuPrice:skuId  value = price
        //获取到方法传递的参数
        Object[] args = joinPoint.getArgs();
        //拼接缓存key
        String key = prefix + Arrays.asList(args).toString();

        try {
            //3.根据缓存的 key 获取缓存的数据
            //key ： 缓存的key   signature ：能够获取到方法上具体的返回值
            object = this.cacheHit(key,signature);
            if(object == null){
                //说明缓存中没有数据，需要从数据库中获取
                String lockKey = prefix + ":lock";
                RLock lock = redissonClient.getLock(lockKey);
                //尝试加锁
                boolean flag = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                if (flag) {
                    try {
                        //查询数据库
                        object = joinPoint.proceed(joinPoint.getArgs()); //表示执行带有@GmallCache 注解的方法

                        //判断object 是否为空，防止缓存穿透
                        if(object == null){
                            Object object1 = new Object();
                            redisTemplate.opsForValue().set(key, JSON.toJSONString(object1),RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            return object1;
                        }
                        //如果object 不为空
                        redisTemplate.opsForValue().set(key,JSON.toJSONString(object),RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);
                        return object;
                    } finally {
                        lock.unlock();
                    }
                }else {
                    //没有获取到锁的线程
                    Thread.sleep(1000);
                    return cacheAround(joinPoint);
                }
            }else {
                return object;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        //数据库兜底
        return joinPoint.proceed(joinPoint.getArgs());
    }

    //获取缓存的数据
    private Object cacheHit(String key, MethodSignature signature) {
        String strJson = (String) redisTemplate.opsForValue().get(key);
        if(!StringUtils.isEmpty(strJson)){
            //获取到了缓存数据
            //返回的数据类型
            Class returnType = signature.getReturnType();
            //将字符串转换为对应的数据类型
            return JSON.parseObject(strJson,returnType);
        }
        return null;
    }
}
