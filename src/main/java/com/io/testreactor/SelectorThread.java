package com.io.testreactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *  每个线程对应一个Selector
 *  多线程情况下，多个客户端被分配到多个selector上
 *  注意：每个客户端，只绑定到其中一个 selector 上
 */
public class SelectorThread implements Runnable{

    // 多路复用器
    Selector selector = null;

    // 线程所在的组
    SelectorThreadGroup group;

    // 堆里的对象
    // 线程的栈是独立的，堆是共享的
    // 其它的方法逻辑、本地对象是线程隔离的
    LinkedBlockingQueue<Channel> queue = new LinkedBlockingQueue<>();

    public SelectorThread (SelectorThreadGroup group){
        try {
            this.group = group;
            this.selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        // loop
        while (true) {
            try {
//                System.out.println(Thread.currentThread().getName() + ": before select()....." + selector.keys().size());
                // 1. select()
                int nums = selector.select(); // select() 不传时间参数的时候是会阻塞的  wakeup()
//                System.out.println(Thread.currentThread().getName() + ": after select()....." + selector.keys().size());
                // 2. 处理 selectKeys
                if (nums > 0) {
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    while (iterator.hasNext()) { // 线程内部是线性处理
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (key.isAcceptable()) {
                            // 接收客户端的过程较复杂（接收之后，要注册，多线程下，新的客户端注册到哪里去？）
                            acceptHandler(key);
                        } else if (key.isReadable()) {
                            readHandler(key);
                        } else if (key.isWritable()) {
//                            writeHandler(key);
                        }
                    }

                }


                // 3. 处理一些 tasks
                if (!queue.isEmpty()) {
                    try {
                        Channel channel = queue.take();
                        if (channel instanceof ServerSocketChannel) {
                            ServerSocketChannel server = (ServerSocketChannel) channel;
                            server.register(selector, SelectionKey.OP_ACCEPT);
                            System.out.println(Thread.currentThread().getName() + "-register listen.....");
                        } else if (channel instanceof SocketChannel) {
                            SocketChannel client = (SocketChannel) channel;
                            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                            client.register(selector, SelectionKey.OP_READ, buffer);
                            System.out.println(Thread.currentThread().getName() + "-register client.....");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readHandler(SelectionKey key) {
        System.out.println(Thread.currentThread().getName() + "-read handler.....");

        ByteBuffer byteBuffer = (ByteBuffer) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        byteBuffer.clear();
        while (true) {
            try {
                int num = client.read(byteBuffer);
                if (num > 0 ) {
                    // 将读到的内容翻转，直接写出
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining()) {
                        client.write(byteBuffer);
                    }
                    byteBuffer.clear();
                } else if (num == 0) {
                    break;
                } else {
                    // 客户端断开了
                    System.out.println("client: " + client.getRemoteAddress() + "closed....");
                    key.cancel();
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void acceptHandler(SelectionKey key) {
        System.out.println("accept handler");
        ServerSocketChannel server = (ServerSocketChannel)key.channel();
        try {
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            //  需要选择一个多路复用器并且去注册
            group.nextSelectorV3(client);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void setWorker(SelectorThreadGroup worker) {
        this.group = worker;
    }
}
