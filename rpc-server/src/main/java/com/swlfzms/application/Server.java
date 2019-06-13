package com.swlfzms.application;

import com.swlfzms.config.ZKConfig;
import com.swlfzms.proxy.ProxyBean;
import com.swlfzms.proxy.RPCInvocationHandler;
import com.swlfzms.service.impl.OrderServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RPC本质就是一个调用协议，只需要定义好这个协议定内容传输规范，按照规范传输和解析即可，实际定通信还是基于socket，默认使用tcp。
 * 服务启动类
 *
 * 启动步骤
 * 1。链接到zookeeper
 * 2。注册自身服务到zk
 * 3。启动自身服务
 */
public class Server {

    /**
     * 服务器域名
     */
    private static final String SERVER_HOST = "localhost";

    /**
     * 服务器运行端口
     */
    private static final int SERVER_PORT = 2182;

    /**
     * 服务器地址
     */
    private static final String SERVER_ADDRESS = SERVER_HOST+":"+SERVER_PORT;;

    /**
     * 所有服务都注册到其中
     */
    private static final Map<String, Object> serviceMap = new HashMap<>();

    /**
     * 固定线程池用于处理请求
     */
    private static final ExecutorService POOLS = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        //添加一个锁，用于保证zk到注册正常
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ZooKeeper zooKeeper = new ZooKeeper(ZKConfig.ZK_SERVER, ZKConfig.ZK_PORT, watchedEvent -> {
            //链接zk正常
            if(watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected){
                countDownLatch.countDown();
            }
        });

        if(zooKeeper.exists(ZKConfig.ROOR, false) != null){
            //创建跟目录
            zooKeeper.create(ZKConfig.ROOR, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }

        //注册服务
        register(zooKeeper, "orderService", OrderServiceImpl.class);

        //等待zk链接通过
        countDownLatch.await();

        //启动服务
        startup();

    }

    /**
     * 启动服务，接收客户端请求
     */
    private static void startup() throws IOException {
        //socket开启通信
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.setReceiveBufferSize(1024 * 10);
        serverSocket.setSoTimeout(3000 * 10);
        serverSocket.bind(new InetSocketAddress(SERVER_HOST, SERVER_PORT));

        //定义socket的处理
        Socket socket = null;
        while((socket = serverSocket.accept()) != null){

            Socket finalSocket = socket;

            POOLS.execute(() -> {

                ObjectOutputStream objectOutputStream = null;
                ObjectInputStream objectInputStream = null;
                try {
                    objectOutputStream = new ObjectOutputStream(finalSocket.getOutputStream());
                    objectInputStream = new ObjectInputStream(finalSocket.getInputStream());
                    //死循环是因为通信没有中断之前都需要一直保持着，并且持续的读取和写出
                    while(true){

                        if(Thread.currentThread().isInterrupted()){
                            //跳出此次通信
                            break;
                        }

                        String className = objectInputStream.readUTF();
                        String methodName = objectInputStream.readUTF();
                        Class[] classes = (Class[]) objectInputStream.readObject();
                        Object[] values = (Object[]) objectInputStream.readObject();
                        Map<String, String> attachements = (Map<String, String>) objectInputStream.readObject();

                        //检查是否有回调参数 回调参数的value固定格式为 className_callback_index
                        for(int i=0;i<values.length;i++){
                            if(values[i].toString().equalsIgnoreCase(classes[i].getName()+"_callback_"+i)){
                                //有回调参数,回调实际也是socket通信，回调需要调参数发送给客户度，客户度识别是哪个回调类，并使用反射获取该类，执行对应调方法
                                RPCInvocationHandler invocationHandler = new RPCInvocationHandler(objectOutputStream, objectInputStream, classes[i]);
                                values[i] = ProxyBean.getBean(classes[i].getClassLoader(), classes[i].getInterfaces(), invocationHandler, i);
                            }
                        }
                        Object service = serviceMap.get(className);
                        Method method = service.getClass().getMethod(methodName, classes);
                        Object result = method.invoke(service, values);
                        objectOutputStream.writeInt(0);
                        objectOutputStream.writeObject(result);
                        objectOutputStream.flush();
                    }
                    objectOutputStream.close();
                    objectInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }finally {
                    try {
                        if(objectOutputStream != null){
                            objectOutputStream.close();
                        }
                        if(objectInputStream != null){
                            objectInputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }


    /**
     * 注册服务zk和内部集合类
     * @param zooKeeper
     * @param serviceName
     * @param clazz
     * @throws InterruptedException
     */
    private static void register(ZooKeeper zooKeeper, String serviceName, Class clazz) throws KeeperException, InterruptedException {

        //先注册到zookeeper
        Stat stat = new Stat();
        //新服务的地址
        String servicePath = ZKConfig.ROOR + "/" + serviceName;

        byte[] bytes = zooKeeper.getData(servicePath, false, stat);

        if (bytes != null && bytes.length > 0) {
            String serivce = new String(bytes);
            if (StringUtils.isNotBlank(serivce) && !serivce.contains(SERVER_ADDRESS)) {
                //追加同个服务到不同发布地址，不同地址分号分割开
                serivce = serivce.concat(";").concat(SERVER_ADDRESS);
                zooKeeper.setData(servicePath, serivce.getBytes(), -1);
            }
        } else {
            zooKeeper.create(servicePath, SERVER_ADDRESS.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        }

        //注册到内部实现类集合
        serviceMap.put(serviceName, clazz);
    }
}
