package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

public interface PaymentService {

    /**
     * 保存交易记录信息
     * @param orderInfo
     * @param paymentType
     */
    void savePaymentInfo(OrderInfo orderInfo,String paymentType);

    /**
     * 获取交易记录信息
     * @param outTradeNo
     * @param name
     * @return
     */
    PaymentInfo getPaymentInfo(String outTradeNo, String name);

    /**
     *  支付成功
     * @param outTradeNo
     * @param name
     * @param paramMap
     */
    void paySuccess(String outTradeNo, String name, Map<String, String> paramMap);

    /**
     * 根据第三方交易编号，修改支付交易记录
     * @param outTradeNo
     * @param name
     * @param paymentInfo
     */
    void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo);

    /**
     * 关闭交易
     * @param orderId
     */
    void closePayment(Long orderId);
}
