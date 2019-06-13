package com.swlfzms.proxy;

import com.swlfzms.listener.CallBackListener;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;


/**
 * 代理实现，主要用于回调处理，A调用B，附带类回调类C，并非将C传给B，否则执行回调的时候发生在B，而不是A，实际是把回调类告知B，
 * B通过socket通信，把回调类需要的参数传回给A，A根据参数，反射获取实际的回调类，发起方法的调用，完成回调
 */
public class RPCInvocationHandler implements InvocationHandler {

    /**
     * 输出流
     */
    private ObjectOutputStream objectOutputStream;
    /**
     * 输入流
     */
    private ObjectInputStream objectInputStream;

    /**
     * 代理对象类
     */
    private Class aClass;

    /**
     * 回调的索引
     */
    private Integer callBackIndex;

    public RPCInvocationHandler() {

    }

    public void setObjectOutputStream(ObjectOutputStream objectOutputStream) {
        this.objectOutputStream = objectOutputStream;
    }

    public void setObjectInputStream(ObjectInputStream objectInputStream) {
        this.objectInputStream = objectInputStream;
    }

    public ObjectOutputStream getObjectOutputStream() {
        return objectOutputStream;
    }

    public ObjectInputStream getObjectInputStream() {
        return objectInputStream;
    }

    public RPCInvocationHandler(ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream, Class aClass) {
        this.objectOutputStream = objectOutputStream;
        this.objectInputStream = objectInputStream;
        this.aClass = aClass;
    }

    public RPCInvocationHandler(ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream, Class aClass, Integer callBackIndex) {
        this.objectOutputStream = objectOutputStream;
        this.objectInputStream = objectInputStream;
        this.aClass = aClass;
        this.callBackIndex = callBackIndex;
    }

    /**
     * 代理实现
     * 回调通知发送格式：
     * 1。回调声明，固定数字：1 （int）
     * 2。当前回调对象是第几个参数值（回调对象在客户端以参数传入类方法的调用）（int）
     * 3。方法名  （String）
     * 4。参数类型数组 (Class[])
     * 5。参数值数组  (Object[])
     * 6。附带参数Map<String,String>(备用)
     *
     * 普通请求发送格式：
     * 1。类名 (String)
     * 2。方法名 (String)
     * 3。参数类型数组 (Class[])
     * 4。参数值数组  (Object[])
     * 5。附带参数Map<String,String>(备用)
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if(callBackIndex != null){

            //说明是回调
            objectOutputStream.writeInt(1);
            //回调的是第几个参数
            objectOutputStream.writeInt(callBackIndex);
        }else{
            objectOutputStream.writeUTF(this.aClass.getName());
        }
        objectOutputStream.writeUTF(method.getName());
        Class[] paramsType = method.getParameterTypes();
        objectOutputStream.writeObject(paramsType);

        //存储需要回调都参数对象，当服务的发起回调时，根据对应当参数获取对应对象，再使用对象的反射调用对应的方法完成回调的实现
        Map<Integer, Object> callBackParamObj = new HashMap<>();


        //发送参数值数组，但是如果某个参数是回调对象，则不能把该对象传过去，而是传约定字符串，让对方处理完了之后调用客户端，只要socket链接未断开，即可一直保持通信
        for(int i=0;i<args.length;i++){
            //任意一个参数对象，如果是实现类CommonListener接口的，都当作回调接口处理
            Class[] interfaces = args[i].getClass().getInterfaces();
            for(Class anyInteterface : interfaces){
                if(anyInteterface.isAssignableFrom(CallBackListener.class)){
                    callBackParamObj.put(i, args[i]);
                    //修改参数值为固定格: classType_callBack_index
                    args[i] = args.getClass().getName()+"_callBack_"+i;

                }
            }
        }
        objectOutputStream.writeObject(args);

        //填充任意附加参数
        Map<String, String> attachments = new HashMap<>();
        attachments.put("sourceName", InetAddress.getLocalHost().getHostName());
        //发送附加参数
        objectOutputStream.writeObject(attachments);

        Object result = null;
        //不为null说明是服务的回调，调用完成类即可，等待结果返回
        if(callBackIndex != null){
            result = objectInputStream.readObject();
            return result;
        }

        //客户端循环读取服务端发过来读信息，直到全部回调完成
        while (true){
            //接收请求结果
            int isCallBack = objectInputStream.readInt();
            if(isCallBack == 0){
                //处理最后的服务端通知，回调已经完成了，或者根本没有回调，只要拿到接口调用结果返回即可
                result = objectInputStream.readObject();
                break;
            }
            int callBackIndex = objectInputStream.readInt();
            //发起回调传送过来的格式：
            String methodName = objectInputStream.readUTF();
            Class[] classes = ((Class[]) objectInputStream.readObject());
            Object[] values = ((Object[]) objectInputStream.readObject());
            Object targetCallBack = callBackParamObj.get(callBackIndex);

            //反射处理，发起实际的回调。
            Class targetClass = targetCallBack.getClass();
            Method targetMethod = targetClass.getMethod(methodName, classes);
            result = targetMethod.invoke(targetCallBack, values);
            objectOutputStream.writeObject(result);
        }

        return result;
    }

    @Override
    protected void finalize(){
        try {
            if(objectInputStream != null){
                objectInputStream.close();
            }
            if(objectOutputStream != null){
                objectOutputStream.close();
            }
        }catch (Exception e){

        }
    }
}
