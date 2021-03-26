package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.cart.CartInfoVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Api(tags = "购物车")
@RestController
@RequestMapping("api/cart")
public class CartApiController {

    @Autowired
    private CartService cartService;

    @Autowired
    private ActivityFeignClient activityFeignClient;

    @ApiOperation("添加购物车")
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request){

        //获取从网关传递过来的userId
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            //userId为空，用户未登录
            //获取临时用户id
            userId = AuthContextHolder.getUserTempId(request);
        }

        cartService.addToCart(skuId,userId,skuNum);
        return Result.ok();
    }

    @ApiOperation("查询购物车")
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request){
        //获取用户id
        String userId = AuthContextHolder.getUserId(request);
        //获取临时用户id
        String userTempId = AuthContextHolder.getUserTempId(request);
        List<CartInfo> cartList = cartService.getCartList(userId, userTempId);
        Long currentUserId = StringUtils.isEmpty(userId) ? null : Long.parseLong(userId);
        List<CartInfoVo> cartInfoVoList = activityFeignClient.findCartActivityAndCoupon(cartList, currentUserId);
        return Result.ok(cartInfoVoList);
    }

    @ApiOperation("更新选中状态")
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable Long skuId,
                            @PathVariable Integer isChecked,
                            HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        if(StringUtils.isEmpty(userId)){
            //未登录
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.checkCart(userId,isChecked,skuId);
        return Result.ok();
    }

    @ApiOperation("删除购物车")
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable Long skuId,HttpServletRequest request){
        String userId = AuthContextHolder.getUserId(request);
        if(StringUtils.isEmpty(userId)){
            //未登录
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.deleteCart(skuId,userId);
        return Result.ok();
    }

    @ApiOperation("根据用户id查询已选中购物车列表")
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable String userId){
        return cartService.getCartCheckedList(userId);
    }

    @ApiOperation("通过userId查询购物车，并放入缓存")
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable String userId){
        cartService.loadCartCache(userId);
        return Result.ok();
    }
}
