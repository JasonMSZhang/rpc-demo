package com.swlfzms.service.impl;

import com.swlfzms.bean.Order;
import com.swlfzms.listener.OrderListener;
import com.swlfzms.service.OrderService;

/**
 * 订单的实际处理类
 */
public class OrderServiceImpl implements OrderService {
    @Override
    public Order getOrderById(String orderId) {
        return new Order();
    }

    @Override
    public void getOrderById(String orderId, OrderListener listener) {
        Order order = new Order();
        listener.update(order);
    }
}
