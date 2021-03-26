package com.atguigu.gmall.common.cache;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})  //注解使用的范围
@Retention(RetentionPolicy.RUNTIME)  //注解的生命周期
public @interface GmallCache {

    //添加一个属性，组成缓存key的前缀
    String prefix() default "cache";
}
