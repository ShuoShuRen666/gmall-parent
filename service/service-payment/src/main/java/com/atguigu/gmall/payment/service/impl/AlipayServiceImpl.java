package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.*;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePageRefundResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AlipayServiceImpl implements AlipayService {

    @Autowired
    private AlipayClient alipayClient;  //在AlipayConfig配置类中 将AlipayClient注入到spring容器中了

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Override
    public String createAlipay(Long orderId) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //保存交易记录到数据库
        paymentService.savePaymentInfo(orderInfo, PaymentType.ALIPAY.name());
        //如果订单到时间未支付，订单状态为CLOSED，则不生成二维码
        if("CLOSED".equals(orderInfo.getOrderStatus())){
            return "订单已经关闭，不能生成二维码";
        }
        //生成二维码
        // AlipayClient 操作支付的类
        // AlipayClient alipayClient =  new DefaultAlipayClient( "https://openapi.alipay.com/gateway.do" , APP_ID, APP_PRIVATE_KEY, FORMAT, CHARSET, ALIPAY_PUBLIC_KEY, SIGN_TYPE);  //获得初始化的AlipayClient
        //创建支付请求
        AlipayTradePagePayRequest alipayRequest =  new  AlipayTradePagePayRequest(); //创建API对应的request
        //设置同步回调参数地址
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        //设置异步回调参数地址
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url); //在公共参数中设置回跳和通知地址
        //设置的生成二维码需要的参数
        //        alipayRequest.setBizContent( "{"  +
        //                "    \"out_trade_no\":\"20150320010101001\","  +
        //                "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\","  +
        //                "    \"total_amount\":88.88,"  +
        //                "    \"subject\":\"Iphone6 16G\","  +
        //                "    \"body\":\"Iphone6 16G\","  +
        //                "    \"passback_params\":\"merchantBizType%3d3C%26merchantBizNo%3d2016010101111\","  +
        //                "    \"extend_params\":{"  +
        //                "    \"sys_service_provider_id\":\"2088511833207846\""  +
        //                "    }" +
        //                "  }" ); //填充业务参数
        Map<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
//        map.put("total_amount",orderInfo.getTotalAmount());
        map.put("total_amount","0.01");
        map.put("subject",orderInfo.getTradeBody());
        map.put("timeout_express","5m");//订单超时时间，5分钟
        //设置的生成二维码需要的参数
        alipayRequest.setBizContent(JSON.toJSONString(map));

        String form= "" ;
        try  {
            form = alipayClient.pageExecute(alipayRequest).getBody();  //调用SDK生成表单
        }  catch  (AlipayApiException e) {
            e.printStackTrace();
        }
        return form;
    }

    /**
     * 退款
     * @param orderId
     * @return
     */
    @Override
    public boolean refund(Long orderId) {
        //根据订单id查询orderInfo
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        // AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest (); //创建退款请求
        Map<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
//        map.put("out_request_no","HZ01RF001"); //表示部分退款
        map.put("refund_amount","0.01");
        map.put("refund_reason","号小了");
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeRefundResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        if(response.isSuccess()){
            System.err.println("退款成功");
            //交易状态改成CLOSED
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
            paymentService.updatePaymentInfo(orderInfo.getOutTradeNo(),PaymentType.ALIPAY.name(),paymentInfo);
            return true;
        }else {
            System.err.println("退款失败");
            return false;
        }
    }

    /**
     * 根据订单id，关闭订单
     * @param orderId
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean closePay(Long orderId) {
        //AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        Map<String, Object> map = new HashMap<>();
        //        request.setBizContent("{" +
        //                "\"trade_no\":\"2013112611001004680073956707\"," +
        //                "\"out_trade_no\":\"HZ0120131127001\"," +
        //                "\"operator_id\":\"YX01\"" +
        //                "  }");
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("operator_id", "YX01");
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeCloseResponse response = alipayClient.execute(request);
        if (response.isSuccess()) {
            System.out.println("关闭订单成功");
            return true;
        } else {
            System.out.println("关闭订单失败");
            return false;
        }
    }

    /**
     * 根据订单查询是否支付成功！
     * @param orderId
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean checkPayment(Long orderId) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        Map<String, Object> map = new HashMap<>();
        //        request.setBizContent("{" +
        //                "\"out_trade_no\":\"20150320010101001\"," +
        //                "\"trade_no\":\"2014112611001004680 073956707\"," +
        //                "\"org_pid\":\"2088101117952222\"," +
        //                "      \"query_options\":[" +
        //                "        \"trade_settle_info\"" +
        //                "      ]" +
        //                "  }");
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        request.setBizContent(JSON.toJSONString(map));
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        if(response.isSuccess()){
            System.out.println("支付成功");
            return true;
        } else {
            System.out.println("支付失败");
            return false;
        }
    }


}
