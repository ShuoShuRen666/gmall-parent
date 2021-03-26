package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.tomcat.util.modeler.ParameterInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RabbitService rabbitService;

    /**
     * 保存交易记录信息
     * @param orderInfo
     * @param paymentType
     */
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, String paymentType) {
        //防止用户多次点击交易，数据库产生多条重复数据
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id",orderInfo.getId());
        queryWrapper.eq("payment_type",paymentType);
        Integer count = paymentInfoMapper.selectCount(queryWrapper);
        if(count > 0) return;

        //保存交易记录
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject(orderInfo.getTradeBody());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        paymentInfo.setCreateTime(new Date());
        //存入数据库
        paymentInfoMapper.insert(paymentInfo);
    }

    /**
     * 根据商户订单号,获取交易记录信息
     * @param outTradeNo
     * @param name
     * @return
     */
    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo, String name) {
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("out_trade_no",outTradeNo);
        queryWrapper.eq("payment_type",name);
        return paymentInfoMapper.selectOne(queryWrapper);
    }

    /**
     *  支付成功
     * @param outTradeNo
     * @param name
     * @param paramMap
     */
    @Override
    public void paySuccess(String outTradeNo, String name, Map<String, String> paramMap) {
        //根据outTradeNo  name 查询订单id
        PaymentInfo paymentInfoQuery = this.getPaymentInfo(outTradeNo, name);
        //判断查询到的数据的支付状态
        if("CLOSED".equals(paymentInfoQuery.getPaymentStatus()) || "PAID".equals(paymentInfoQuery.getPaymentStatus())){
            //如果为已支付或者已关闭，则直接return
            return;
        }
        //  update payment_info set trade_no=? ,payment_status = ? ,callback_time = ? where out_trade_no=? and payment_type = ?;
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTradeNo(paramMap.get("trade_no"));
        //修改订单状态为已支付
        paymentInfo.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setCallbackContent(paramMap.toString());

        //更新支付信息表
        this.updatePaymentInfo(outTradeNo,name,paymentInfo);

        //发消息修改订单状态
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,paymentInfoQuery.getOrderId());
    }

    /**
     * 方法复用
     * @param outTradeNo
     * @param name
     * @param paymentInfo
     */
    @Override
    public void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo) {
        QueryWrapper<PaymentInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("out_trade_no",outTradeNo);
        queryWrapper.eq("payment_type",name);
        paymentInfoMapper.update(paymentInfo,queryWrapper);
    }

    /**
     * 关闭交易
     * @param orderId
     */
    @Override
    public void closePayment(Long orderId) {
        //设置关闭交易记录的条件
        Integer count = paymentInfoMapper.selectCount(new QueryWrapper<PaymentInfo>().eq("order_id", orderId));
        if(null == count || count == 0) return;
        //再关闭支付宝交易之前，还需要将paymentInfo的状态设置为CLOSED
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentInfoMapper.update(paymentInfo,new QueryWrapper<PaymentInfo>().eq("order_id", orderId));
    }
}
