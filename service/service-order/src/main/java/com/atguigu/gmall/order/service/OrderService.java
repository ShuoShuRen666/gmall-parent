package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<OrderInfo> {

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    /**
     * 生产流水号
     * @param userId
     * @return
     */
    String getTradeNo(String userId);

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    boolean checkTradeCode(String userId, String tradeCodeNo);


    /**
     * 删除流水号
     * @param userId
     */
    void deleteTradeNo(String userId);

    /**
     * 检查库存是否充足
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean checkStock(Long skuId,Integer skuNum);

    /**
     * 处理过期订单
     * @param orderId
     */
    void execExpiredOrder(Long orderId);

    /**
     * 更新过期订单
     * @param orderId
     * @param flag
     */
    void execExpiredOrder(Long orderId,String flag);

    /**
     * 根据订单Id 修改订单的状态
     * @param orderId
     * @param processStatus
     */
    void updateOrderStatus(Long orderId, ProcessStatus processStatus);

    /**
     * 根据订单id 查询订单信息
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);

    /**
     * 发送消息给库存系统
     * @param orderId
     */
    void sendOrderStatus(Long orderId);

    Map initWareOrder(OrderInfo orderInfo);

    /**
     * 拆单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    List<OrderInfo> orderSplit(Long orderId, String wareSkuMap);

    //记录订单状态日志
    void saveOrderStatusLog(Long orderId, String orderStatus);

}
