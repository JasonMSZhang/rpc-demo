package com.swlfzms.bean;

/**
 * 订单
 */
public class Order {

    /**
     * 订单id
     */
    private String orderId;

    /**
     * 订单状态
     */
    private int orderStatus;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public int getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(int orderStatus) {
        this.orderStatus = orderStatus;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", orderStatus=" + orderStatus +
                '}';
    }
}
