package com.atguigu.gmall.payment.service;

public interface AlipayService {

    //支付生成二维码 参数 orderId ，返回值string{接收完整的表单，将表单输出到页面的时候，我们在控制器完成}
    String createAlipay(Long orderId);

    //退款
    boolean refund(Long orderId);

    //根据订单id，关闭订单
    Boolean closePay(Long orderId);

    Boolean checkPayment(Long orderId);
}
