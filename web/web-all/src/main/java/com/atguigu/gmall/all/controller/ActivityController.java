package com.atguigu.gmall.all.controller;

import io.swagger.models.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ActivityController {
    /**
     * 返回优惠券列表
     * @return
     */
    @GetMapping("couponInfo.html")
    public String index() {
        return "couponInfo/index";
    }
}
