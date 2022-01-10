package com.io.netty;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 1. 需求：写一个RPC
 * 2. 来回通信，连接数量，拆包？
 * 3. 动态代理，序列化反序列化，协议封装
 * 4. 连接池
 *
 * RPC（remote procedure call）：远程调用协议
 * 就像调用本地方法一样去调用远程的方法，面向java中就是所谓的 面向接口开发
 */
public class MyRPCTest {

    // 模拟 consumer 端
    @Test
    public void get() {
        // 动态代理获得一个对象
        Car car = proxyGet(Car.class);
        car.start("张三启动了");

        Fly fly = proxyGet(Fly.class);
        fly.start("张三起飞了");
    }

    public static <T>T proxyGet (Class<T> interfaceInfo) {
        // 实现各个版本的动态代理的方式。。。

        ClassLoader loader = interfaceInfo.getClassLoader();
        Class<?>[] methodInfo = {interfaceInfo};


        return (T)Proxy.newProxyInstance(loader, methodInfo, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                // 如何设计consumer对于provider的调用过程
                // 1. 调用服务、方法、参数  ==》 封装成 message

                // 2. requestID + message, 本地要缓存

                // 3. 连接池::取得连接

                // 4. 发送  --> 走 IO  out

                // 5. 如果从IO，未来回来了，怎么将代码执行到这里

                // (睡眠/回调,如何然线程停下来？你还能够继续。。。)

                return null;
            }
        });
    }
}

interface Car {
    void start(String msg);
}

interface Fly {
    void start(String msg);
}

