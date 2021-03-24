package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.ItemFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
public class ItemController {

    @Autowired
    private ItemFeignClient itemFeignClient;

    @RequestMapping("{skuId}.html")
    public String getItem(@PathVariable Long skuId, Model model){
        Result<Map> result = itemFeignClient.getItem(skuId);
        //获取到result中的data，他才是我们想要的map封装好的数据
        //用来存储所有的map集合 Map的key 就是页面要渲染的key
        model.addAllAttributes(result.getData());
        return "item/index";
    }
}
