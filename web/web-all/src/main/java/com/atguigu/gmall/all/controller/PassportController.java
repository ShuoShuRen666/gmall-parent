package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class PassportController {

    //用户在访问什么的时候会跳转到登陆页面
    //http://passport.gmall.com/login.html?originUrl=http://www.gmall.com/
    //originUrl  用户记录用户在哪个页面点击的登录
    @GetMapping("login.html")
    public String login(HttpServletRequest request){
        String originUrl = request.getParameter("originUrl");
        request.setAttribute("originUrl",originUrl);
        return "login";
    }
}
