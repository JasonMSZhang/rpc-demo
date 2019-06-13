package com.swlfzms.proxy;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.Socket;

/**
 * 动态代理工具
 */
public class ProxyBean {

    public static <T> T getBean(Class classes, String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        RPCInvocationHandler invocationHandler = new RPCInvocationHandler(objectOutputStream, objectInputStream, classes);
        return (T) Proxy.newProxyInstance(classes.getClassLoader(), classes.getInterfaces(), invocationHandler);
    }
    /**
     * 生成动态代理
     * @param classLoader
     * @param classes
     * @param invocationHandler
     * @param <T>
     * @return
     */
    public static <T> T getBean(ClassLoader classLoader, Class<T> [] classes, InvocationHandler invocationHandler){
        return (T) Proxy.newProxyInstance(classLoader, classes, invocationHandler);
    }

    /**
     * 生成动态代理
     * @param classLoader
     * @param classes
     * @param invocationHandler
     * @param index 回调参数的index
     * @param <T>
     * @return
     */
    public static <T> T getBean(ClassLoader classLoader, Class<T> [] classes, InvocationHandler invocationHandler, int index){
        return (T) Proxy.newProxyInstance(classLoader, classes, invocationHandler);
    }

}
