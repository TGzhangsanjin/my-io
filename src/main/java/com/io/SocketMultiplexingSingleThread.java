package com.io;

import io.netty.buffer.ByteBuf;

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
 * 多路复用器， 单线程版本
 *
 * @Author 张三金
 * @Date 2022/1/3 0003 14:37
 * @Company jzb
 * @Version 1.0.0
 */
public class SocketMultiplexingSingleThread {

    private ServerSocketChannel server = null;

    private Selector selector = null;

    int port = 9090;

    public void initServer () {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));

            // select、poll、epoll 优先选择 epoll， 但是启动时可以通过 -D 参数修改
            // epoll： open -> epoll_create(fd3,
            // select、poll
            selector = Selector.open();

            // server.register 相当于 listen 状态的 fd4
            // select、poll : jvm里开辟一个数组将 fd 放进去
            // epoll: epoll_crl(fd3, ADD, fd4,
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
                // 返回所有的 fd 集合
                Set<SelectionKey> keys = selector.keys();
                System.out.println(keys.size() + " size");

                // selector.select(500) 啥意思？
                // select、poll: 内核的 select(fd4) poll(fd4)
                // epoll: 内核的 epoll_wait()
                // 参数可以带时间，如果没有事件，默认是0， 代表会阻塞，
                // selector.wakeup() 结果返回的是 0
                while (selector.select(500) > 0) {
                    // 返回的有状态的fd集合
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    // 对比NIO， NIO是需要每一个fd都去调用系统调用，浪费资源，而多路复用器则是调用了依次 select方法，得到多个fd的状态
                    // 不管是哪种多路复用器，内核只返回状态，程序一样需要一个个去处理每一个 R/W
                    // socket拿到的状态分两种：(1) accept (2) 通信 R/W
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();
                        if (key.isAcceptable()) {
                            // 如果接收一个新的连接，
                            // 语义上， accept 接受连接，且返回新连接的 fd, 那新的 fd 怎么处理？
                            // select, poll：因为内核没有单独维护这个空间，则在JVM中保存和listen的fd4放到一起
                            // epoll: 通过epoll_ctl 把客户端 fd 注册到内核空间
                            acceptHandler(key);
                        } else if (key.isReadable()) {
                            // 在当前线程里，这个方法时可能会阻塞的
                            acceptHandler(key);
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
            // 接收客户端
            SocketChannel client = ssc.accept();
            // 注册非阻塞
            client.configureBlocking(false);
            // 未来读写数据通过 buffer 使用处理
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            // 调用了 register
            // select、poll : jvm里开辟一个数组将 fd7 放进去
            // epoll: epoll_crl(fd3, ADD, fd7,
            client.register(selector, SelectionKey.OP_READ, buffer);
            System.out.println("接收了一个新的客户端连接： " + client.getRemoteAddress());
        }catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void readHandler (SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer)key.attachment();
        buffer.clear();
        int read = 0;
        try {
            while (true) {
                read = client.read(buffer);
                if (read > 0) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        client.write(buffer);
                    }
                    buffer.clear();
                } else if (read == 0) {
                    break;
                } else {
                    client.close();
                    break;
                }
            }
        }catch (IOException e) {
            e.printStackTrace();;
        }
    }

    public static void main(String[] args) {
        SocketMultiplexingSingleThread server = new SocketMultiplexingSingleThread();
        server.start();
    }
}
