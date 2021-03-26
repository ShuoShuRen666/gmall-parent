package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;

    @ResponseBody
    @ApiOperation("支付生成二维码")
    @RequestMapping("submit/{orderId}")
    public String aliPaySubmit(@PathVariable Long orderId, HttpServletResponse response){
        String from = "";
        try {
            from = alipayService.createAlipay(orderId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return from;
    }

    @ApiOperation("支付宝同步回调,给用户展示一个支付成功或失败的页面")
    @RequestMapping("callback/return")
    public String callBack(){
        //当用户获取到回调地址之后，调整到支付成功页面。
        return "redirect:" + AlipayConfig.return_order_url;
    }

    @ApiOperation("支付宝异步回调，必须使用内网穿透")
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap){
        //如果支付成功了 会返回“success”给支付宝
        System.err.println("异步回调");
        //调用SDK验证签名
        boolean signVerified = false;
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap,AlipayConfig.alipay_public_key,AlipayConfig.charset,AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        //交易状态
        String tradeStatus = paramMap.get("trade_status");
        //商户订单号
        String outTradeNo = paramMap.get("out_trade_no");

        if(signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，
            //  对支付结果中的业务内容进行二次校验，
            //  校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            if("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)){
                //验签成功
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
                if(paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID.name()) || paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED.name())){
                    //如果状态为已支付或者已关闭
                    return "failure";
                }else {
                    // 正常的支付成功，我们应该更新交易记录状态
                    paymentService.paySuccess(outTradeNo,PaymentType.ALIPAY.name(),paramMap);
                    return "success";
                }
            }else {
                // TODO 验签失败则记录异常日志，并在response中返回failure.
                return "failure";
            }
        }
        return "failure";
    }

    //http://localhost:8205/api/payment/alipay/refund/20
    @ApiOperation("退款")
    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable Long orderId){
        boolean flag = alipayService.refund(orderId);
        return Result.ok(flag);
    }

    //http://localhost:8205/api/payment/alipay/closePay/25
    @ApiOperation("根据订单id，关闭订单")
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        return alipayService.closePay(orderId);
    }

    //http://localhost:8205/api/payment/alipay/checkPayment/30
    @ApiOperation("根据订单查询是否支付成功！")
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        return alipayService.checkPayment(orderId);
    }

    @ApiOperation("整合关闭过期订单(根据商户订单号,获取交易记录信息)")
    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (paymentInfo != null) {
            return paymentInfo;
        }
        return null;
    }
}
