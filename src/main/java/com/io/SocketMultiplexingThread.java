package com.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * 多路复用器， 多线程版本
 *
 * @Author 张三金
 * @Date 2022/1/3 0003 14:37
 * @Company jzb
 * @Version 1.0.0
 */
public class SocketMultiplexingThread {

    private ServerSocketChannel server = null;

    private Selector selector = null;

    int port = 9090;

    public void initServer () {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start () {
        initServer();
        System.out.println("服务器启动了！！！");
        try {
            while (true) {
                Set<SelectionKey> keys = selector.keys();
                while (selector.select(500) > 0) {
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.isAcceptable()) {
                            acceptHandler(key);
                        } else if (key.isReadable()) {
                            key.cancel();
                            readHandler(key); // 即便抛出线程去读取，但是在时差里，这个key的read事件可能会重复触发，所以需要 key.cancel()
                        } else if (key.isWritable()) {
                            // 写事件 什么时候会发生？ send-queue 只要是有空间的，就一定会给你返回可以写事件，就会回调写方法
                            // 什么时候写，不是依赖send-queue 是不是有空间
                            // 1. 准备好要写的数据
                            // 2. send-queue 是否有空间
                            // 3. 所以，读 read 一开始就要注册，但是 write 依赖以上关系，什么时候用就什么时候注册write事件
                            // 4. 如果一开始就注册write事件，就会进入死循环，一直调起
                            key.cancel();
                            writeHandle(key);
                        }
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptHandler (SelectionKey key) {

        try {
            ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
            SocketChannel client = ssc.accept();
            client.configureBlocking(false);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            client.register(selector, SelectionKey.OP_READ, buffer);
            System.out.println("接收了一个新的客户端连接： " + client.getRemoteAddress());
        }catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void readHandler (SelectionKey key) {
        new Thread(() -> {
            System.out.println("read handle");
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer)key.attachment();
            buffer.clear();
            int read = 0;
            try {
                while (true) {
                    read = client.read(buffer);
                    if (read > 0) {
                        // 不直接写，而是先注册一个write事件
                        client.register(key.selector(), SelectionKey.OP_WRITE, buffer);
                        // 关心 OP_WRITE 其实就是关心send-queue 是否有空间
                    } else if (read == 0) {
                        break;
                    } else {
                        // 客户端断开连接了
                        client.close();
                        break;
                    }
                }
            }catch (IOException e) {
                e.printStackTrace();;
            }
        }).start();
    }

    private void writeHandle (SelectionKey key) {
        new Thread(() -> {
            System.out.println("write handle");
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            buffer.flip();
            while (buffer.hasRemaining()) {
                try {
                    client.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            buffer.clear();
        }).start();

    }

    public static void main(String[] args) {
        SocketMultiplexingThread server = new SocketMultiplexingThread();
        server.start();
    }
}
