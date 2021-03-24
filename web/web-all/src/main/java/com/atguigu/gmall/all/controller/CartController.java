package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class CartController {

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    /**
     * 查看购物车
     * @return
     */
    @RequestMapping("cart.html")
    public String index(){
        //返回cart/index 这个页面后，异步直接加载api/cart/cartList
        return "cart/index";
    }

    /**
     * 添加购物车
     * addCart.html?skuId=13&skuNum=1&sourceType=QUERY
     * @return
     */
    @RequestMapping("addCart.html")
    public String addCart(HttpServletRequest request){
        String skuId = request.getParameter("skuId");
        String skuNum = request.getParameter("skuNum");

        cartFeignClient.addToCart(Long.parseLong(skuId),Integer.parseInt(skuNum));
        //前端页面需要一个skuInfo对象和skuNum
        SkuInfo skuInfo = productFeignClient.getAttrValueList(Long.parseLong(skuId));
        request.setAttribute("skuInfo",skuInfo);
        request.setAttribute("skuNum",skuNum);
        return "cart/addCart";
    }
}
