package com.atguigu.gmall.user.controller;

import com.alibaba.csp.sentinel.util.PidUtil;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Api("用户认证接口")
@RestController
@RequestMapping("/api/user/passport")
public class PassportApiController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 用户登录接口
     * @param userInfo
     * @param request
     * @return
     */
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo, HttpServletRequest request){
        /*
        1.调用服务层方法
        2.需要产生一个token
        3.需要将用户信息保存到缓存中
         */
        UserInfo info = userService.login(userInfo);
        if (info != null) {
            //用户在数据库中存在，登陆成功
            String token = UUID.randomUUID().toString();
            Map<String,Object> map = new HashMap<>();
            map.put("token",token);
            //需要在页面显示用户昵称
            map.put("nickName",info.getNickName());

            //用户登陆成功后，需要将数据放入缓存,目的是为了判断用户在访问其他业务的时候 是否登录
            JSONObject userJson  = new JSONObject();
            userJson.put("userId", info.getId().toString());
            userJson.put("ip", IpUtil.getIpAddress(request));
            String userLoginKey = RedisConst.USER_LOGIN_KEY_PREFIX + token;
            redisTemplate.opsForValue().set(userLoginKey, userJson.toJSONString(), RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);

            return Result.ok(map);
        }else {
            return Result.fail().message("登陆失败！(PassportApiController)");
        }
    }

    /**
     * 用户退出
     * @return
     */
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        //在登陆的时候，将token放入了cookie中，同时还将token放入了header中
        String token = request.getHeader("token");
        //删除缓存中的数据
        String userLoginKey = RedisConst.USER_LOGIN_KEY_PREFIX + token;
        redisTemplate.delete(userLoginKey);

        return Result.ok();
    }


}
