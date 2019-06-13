package com.swlfzms.listener;

import com.swlfzms.bean.Order;

/**
 * 订单监听器
 */
public interface OrderListener extends CallBackListener {

    /**
     * 更新订单操作
     */
    public void update(Order order);
}
