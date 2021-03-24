package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class SeckillController {

    @Autowired
    private ActivityFeignClient activityFeignClient;

    @ApiOperation("返回秒杀商品列表")
    @GetMapping("seckill.html")
    public String index(Model model){
        Result result = activityFeignClient.findAll();
        model.addAttribute("list",result.getData());
        return "seckill/index";
    }

    @ApiOperation("根据id获取秒杀商品实体（秒杀详情页）")
    @GetMapping("seckill/{skuId}.html")
    public String getItem(@PathVariable Long skuId,Model model){
        //通过skuId查询skuInfo
        Result result = activityFeignClient.getSeckillGoods(skuId);
        model.addAttribute("item",result.getData());
        return "seckill/item";
    }

    //window.location.href = '/seckill/queue.html?skuId='+this.skuId+'&skuIdStr='+skuIdStr
    //排队控制器
    @GetMapping("seckill/queue.html")
    public String queue(@RequestParam(name = "skuId") Long skuId,
                        @RequestParam(name = "skuIdStr") String skuIdStr,
                        HttpServletRequest request){
        //页面需要skuId，skuIdStr
        request.setAttribute("skuId",skuId);
        request.setAttribute("skuIdStr",skuIdStr);
        return "seckill/queue";
    }

    //确认订单
    @GetMapping("/seckill/trade.html")
    public String trade(Model model){
        Result<Map> result = activityFeignClient.trade();
        if(result.isOk()){
            model.addAllAttributes(result.getData());
            return "seckill/trade";
        }else {
            model.addAttribute("message",result.getMessage());
            return "seckill/fail";
        }
    }


}
