package com.swlfzms.service;

import com.swlfzms.bean.Order;
import com.swlfzms.listener.OrderListener;

/**
 * 订单接口
 */
public interface OrderService {

    /**
     * 根据订单id获取订单
     * @param orderId
     * @return
     */
    public Order getOrderById(String orderId);

    /**
     * 根据订单id获取订单
     * @param orderId
     * @param listener
     * @return
     */
    public void getOrderById(String orderId, OrderListener listener);

}
