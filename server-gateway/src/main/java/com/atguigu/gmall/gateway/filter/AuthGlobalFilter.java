package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {

    //路径匹配的工具类
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${authUrls.url}")
    private String authUrls;

    /**
     * 过滤器 拦截某些特定的用户请求 如：用户未登录，访问购物车，需要提示用户登录
     * @param exchange   服务层 web对象   能够获取到请求和响应
     * @param chain  过滤器链
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        //获取到用户在浏览器访问的url地址
        String path = request.getURI().getPath();
        //path = /api/product/auth/hello
        System.err.println("path!!!!!!!------------" + path);
        //判断当前路径是否需要拦截
        if(antPathMatcher.match("/**/inner/**",path)){
            // 如果是内部接口，则网关拦截不允许外部访问！
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        //获取用户id  判断用户是否登录（登陆成功之后，在缓存中放了一个userId）
        String userId = this.getUserId(request);
        if ("-1".equals(userId)) {
            //token被盗用
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }
        //如果用户未登录的情况，判断用户是否访问了带有/api/**/auth/** 这样的控制器，如果有 则需要登录
        if(antPathMatcher.match("/api/**/auth/**",path)){
            if (StringUtils.isEmpty(userId)){
                //未登录
                ServerHttpResponse response = exchange.getResponse();
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        //判断用户是否访问了黑白名单的控制器,如果访问了，并且为登录，则提示用户需要登录
        String[] split = authUrls.split(",");
        if(split != null && split.length>0){
            for (String authUrl : split) {
                // 当前的url包含登录的控制器域名，但是用户Id 为空！
                if(path.contains(authUrl) && StringUtils.isEmpty(userId)){
                    ServerHttpResponse response = exchange.getResponse();
                    //设置一下状态码  303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
                    response.setStatusCode(HttpStatus.SEE_OTHER);
                    //URI = http://list.gmall.com/list.html
                    response.getHeaders().set(HttpHeaders.LOCATION,"http://passport.gmall.com/login.html?originUrl=" + request.getURI());
                    System.err.println("URI!!!!_----------------" + request.getURI());
                    //重定向到登录
                    return response.setComplete();
                }
            }
        }
        //获取临时用户id
        String userTempId = this.getUserTempId(request);
        //如果上述验证通过，将userId 和 userTempId 传递给后端各个微服务 以便他们使用
        if(!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){
            if(!StringUtils.isEmpty(userId)){
                request.mutate().header("userid",userId).build();
            }
            if(!StringUtils.isEmpty(userTempId)){
                request.mutate().header("userTempId",userTempId).build();
            }
            //将userId 和 userTempId 传递到后端微服务了
            return chain.filter(exchange.mutate().request(request).build());
        }

        return chain.filter(exchange);
    }

    /**
     * 输出数据到页面
     * @param response
     * @param resultCodeEnum
     * @return
     */
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        // 将提示信息输出到页面
        Result<Object> result = Result.build(null, resultCodeEnum);
        //输出result
        byte[] str = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        //响应给页面
        DataBuffer wrap = response.bufferFactory().wrap(str);
        //设置响应头
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");

        // Mono Publisher<void>  jdk1.8 响应式编程出现的对象
        return response.writeWith(Mono.just(wrap));  //输出到页面
    }

    /**
     * 获取当前登录用户id
     * @param request
     * @return
     */
    private String getUserId(ServerHttpRequest request) {
        String token = "";
        List<String> tokenList = request.getHeaders().get("token");
        if(null != tokenList){
            //先从请求头中获取
            token = tokenList.get(0);
        }else {
            //从cookies中获取
            MultiValueMap<String, HttpCookie> cookieMultiValueMap  = request.getCookies();
            HttpCookie cookie = cookieMultiValueMap.getFirst("token");
            if (cookie != null) {
                token = URLDecoder.decode(cookie.getValue());
            }
        }

        if (!StringUtils.isEmpty(token)) {
            //从请求头或者cookie中获取到了token
            //从redis中取出token
            //key = user:login:08e5d0f5-fab9-422d-901b-98463dbae9f4   v = "{\"ip\":\"192.168.200.1\",\"userId\":\"2\"}"
            String userStr = (String) redisTemplate.opsForValue().get("user:login:" + token);
            JSONObject userJson = JSONObject.parseObject(userStr);
            String ip = userJson.getString("ip");
            String curIp = IpUtil.getGatwayIpAddress(request);
            //校验token是否被盗用  缓存中的ip地址和当前系统所在服务器的ip地址一致的话，返回用户id，否则返回-1
            if (ip.equals(curIp)) {
                return userJson.getString("userId");
            }else {
                //ip不一致
                return "-1";
            }

        }
        return null;
    }

    /**
     * 获取当前用户临时id
     * @param request
     * @return
     */
    private String getUserTempId(ServerHttpRequest request){
        String userTempId = "";
        //先从请求头中找
        List<String> tokenList = request.getHeaders().get("userTempId");
        if(tokenList != null){
            userTempId = tokenList.get(0);
        }else {
            //从cookie中找
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if (cookie != null) {
                userTempId = cookie.getValue();
            }
        }
        return userTempId;
    }
}
