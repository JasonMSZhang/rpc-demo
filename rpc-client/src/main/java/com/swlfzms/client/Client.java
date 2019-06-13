package com.swlfzms.client;

import com.swlfzms.bean.Order;
import com.swlfzms.config.ZKConfig;
import com.swlfzms.listener.OrderListener;
import com.swlfzms.proxy.ProxyBean;
import com.swlfzms.service.OrderService;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * RPC请求客户端
 */
public class Client {


    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {

        CountDownLatch countDownLatch = new CountDownLatch(1);
        ZooKeeper zooKeeper = new ZooKeeper(ZKConfig.ZK_SERVER, ZKConfig.ZK_PORT, watchedEvent -> {
            if(watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected){
                countDownLatch.countDown();
            }
        });
        countDownLatch.await();
        String servicePath = ZKConfig.ROOR + ZKConfig.ORDER_SERVICE;
        Stat stat = new Stat();
        byte[] bytes = zooKeeper.getData(servicePath, false, stat);
        if(bytes != null && bytes.length>0){
            String serviceValue = new String(bytes);

            //服务可能注册了多个地址，任意选择一个发起调用，或者实现各种调用策略，按策略自行调用
            String[] addresses = serviceValue.split(";");
            //使用第一个地址发起请求
            String[] address = addresses[0].split(":");
            OrderService orderService = (OrderService) ProxyBean.getBean(OrderService.class, address[0], Integer.valueOf(address[1]));
            orderService.getOrderById("abcd", new OrderListener() {
                @Override
                public void update(Order order) {
                    order.setOrderStatus(1);
                    System.out.println(order.toString());
                }
            });
        }else{
            //服务并未启动
            System.out.println(servicePath + "服务异常");
            System.exit(1);
        }
    }
}
