package com.io.testreactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SelectorThreadGroup {


    // 当前组包含的线程
    SelectorThread[] threads;

    // 服务端的socket连接
    ServerSocketChannel server = null;

    AtomicInteger ai = new AtomicInteger(0);

    // worker group, 默认是自己，当前对象就是boss group
    SelectorThreadGroup worker = this;

    public void setWorker (SelectorThreadGroup worker) {
        this.worker = worker;
    }

    /**
     * @param nThreads 线程数
     */
    public SelectorThreadGroup(int nThreads) {
        threads = new SelectorThread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            threads[i] = new SelectorThread(this);
            new Thread(threads[i]).start();
        }
    }

    public void bind(int port) {

        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));

            // server 注册到哪个selector 上？
            nextSelectorV3(server);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // 无论serverSocket还是Socket都复用这个方法
    public void nextSelector (Channel channel) {
        SelectorThread thread = next(); // 在主线程中，取到 堆里的 selectorThread对象

        // 1. 通过队列传递数据消息
        thread.queue.add(channel);
        // 2. 通过打断阻塞，让对应的线程去自己在打断后完成注册selector，也就是处理 task
        thread.selector.wakeup();


        // 重点： channel有可能是 server，也有可能是 client
//        try {
//                // 呼应上， int nums = selector.select(); // 阻塞  wakeup()
//                ServerSocketChannel server = (ServerSocketChannel) channel;
//                // 目的是让selector的select()方法立刻返回，不阻塞
//                server.register(thread.selector, SelectionKey.OP_ACCEPT);
//                thread.selector.wakeup();
//        } catch (ClosedChannelException e) {
//            e.printStackTrace();
//        }
    }

    // 服务端注册到tread-0 上，客户端注册在thread-0和thread-1 上
    public void nextSelectorV2 (Channel channel) {
        if (channel instanceof ServerSocketChannel) {
            threads[0].queue.add(channel);
            threads[0].selector.wakeup();
        } else {
            SelectorThread thread = nextV2();
            thread.queue.add(channel);
            thread.selector.wakeup();
        }

    }

    public void nextSelectorV3 (Channel channel) {
        if (channel instanceof ServerSocketChannel) {
            // server 就是在自己的这个组选择一个就可以,
            SelectorThread thread = next();
            thread.queue.add(channel);
            // listen 选择了boss组中的一个线程后，未来要更新这个线程的worker组
            thread.setWorker(worker);
            thread.selector.wakeup();
        } else {
            // client 就需要在当前组的worker组中选一个
            SelectorThread thread = nextV3();
            thread.queue.add(channel);
            thread.selector.wakeup();
        }
    }

    private SelectorThread next () {
        // 轮询就可能会导致分配不均
        int index = ai.incrementAndGet() % threads.length;
        return threads[index];
    }

    // 服务端注册到tread-0 上，客户端注册在thread-0和thread-1 上
    private SelectorThread nextV2 () {
        int index = ai.incrementAndGet() % (threads.length - 1);
        return threads[index + 1];
    }

    private SelectorThread nextV3 () {
        // 在worker里面挑一个
        int index = ai.incrementAndGet() % worker.threads.length;
        return worker.threads[index];
    }
}
