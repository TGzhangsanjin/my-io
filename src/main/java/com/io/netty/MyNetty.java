package com.io.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MyNetty {

    @Test
    public void myByteBuf () {
        // 两个参数，一个是初始容量，一个是最大容量
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(10, 20);
//        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.heapBuffer(10, 20);
        // pool 池化
//        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer(10, 20);
        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4,5});
        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4,5});
        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4,5});
        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4,5});
        print(buf);

        buf.writeBytes(new byte[]{1,2,3,4,5});
        print(buf);


    }

    public static void print (ByteBuf buf) {
        System.out.println("buf.isReadable() : " + buf.isReadable()); // 是否可读
        System.out.println("buf.readerIndex() : " +buf.readerIndex()); // 可以从哪儿开始读
        System.out.println("buf.readableBytes() : " + buf.readableBytes()); // 可读多少字节
        System.out.println("buf.isWritable() : " + buf.isWritable()); // 是否可写
        System.out.println("buf.writerIndex() : " + buf.writerIndex()); // 可以从哪儿开始写
        System.out.println("buf.writableBytes() : " + buf.writableBytes()); // 可写多少字节
        System.out.println("buf.capacity() : " + buf.capacity()); // 真实容量
        System.out.println("buf.maxCapacity() : " + buf.maxCapacity()); // 最大容量
        System.out.println("buf.isDirect() : " + buf.isDirect()); // 是否堆外分配

        System.out.println("==============================");
    }

    /**
     * 客户端
     * 1. 主动发送数据
     * 2. 别人什么时候给我发？ event selector
     *
     * 服务端： nc -l localhost 9090
     */
    @Test
    public void loopExecutor() {
        // group 可以看成一个线程池
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(() -> {
            try {
                while(true) {
                    System.out.println("hello world0001!");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        selector.execute(() -> {
            try {
                while(true) {
                    System.out.println("hello world0002!");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * IO 中监听事件是第一步，读写是第二步
     */
    @Test
    public void clientMode () {

        try {
            NioEventLoopGroup thread = new NioEventLoopGroup(1);

            // 得到一个客户端
            NioSocketChannel client = new NioSocketChannel();
            // 注册
            thread.register(client); // epoll_ctl(5, ADD, 3)

            // 响应式： 添加自己的 handler
            ChannelPipeline pipeline = client.pipeline();
            pipeline.addLast(new MyInHandler());

            // reactor 异步的特征
            ChannelFuture connect = client.connect(new InetSocketAddress("192.168.72.10", 9090));
            ChannelFuture sync = connect.sync();

            // 准备一个 buffer， 去向服务端发送数据
            ByteBuf buf = Unpooled.copiedBuffer("Hello server".getBytes());
            ChannelFuture send = client.writeAndFlush(buf);
            send.sync();

            // 等着连接关闭
            sync.channel().closeFuture().sync();

            System.out.println("client over....");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void serverMode () {
        try{
            NioEventLoopGroup thread = new NioEventLoopGroup(1);
            NioServerSocketChannel server = new NioServerSocketChannel();
            thread.register(server);

            ChannelPipeline pipeline = server.pipeline();
            pipeline.addLast(new MyAcceptHandler(thread, new ChannelInit())); // 除了接收客户端，并且注册到 selector

            // 指不定什么时候就需要accept 客户端
            ChannelFuture bind = server.bind(new InetSocketAddress("192.168.31.172", 9090));

            bind.sync().channel().closeFuture().sync();
            System.out.println("server close......");
        }catch (Exception e) {
            e.printStackTrace();
        }

    }

    // 为啥要有一个 initHandler? 目的是为了不把 @ChannelHandler.Sharable 参数暴露给用户
    @ChannelHandler.Sharable
    public class ChannelInit extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            Channel client = ctx.channel();
            ChannelPipeline pipeline = client.pipeline();
            pipeline.addLast(new MyInHandler()); // 2. client::pipeline[ChannelInit]

            // 过河拆桥，pipeline 中的channelInit 可以移除了
            // MyInHandler 加到 pipeLine 中之后，pipeLine 里面的ChannelInit 就没用了
            ctx.pipeline().remove(this);
        }
    }

    // @ChannelHandler.Sharable 这个参数强压给用户是不合适的，
//    @ChannelHandler.Sharable
    public class MyInHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("client registered！");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("channel active...");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            // read方法会挪动读取的指针
//            CharSequence str = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8);
            CharSequence str = buf.getCharSequence(0, buf.readableBytes(), CharsetUtil.UTF_8);
            System.out.println(str);
            // 客户端收到处理消息后，再把数据发回给服务端
            ctx.writeAndFlush(buf);
        }
    }

    public class MyAcceptHandler extends ChannelInboundHandlerAdapter {

        EventLoopGroup selector;

        ChannelHandler handler;

        public MyAcceptHandler (EventLoopGroup thread, ChannelHandler myInHandler) {
            this.selector = thread;
            this.handler  = myInHandler;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("server registered...");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // 注意这里读取到的是一个客户端的 socket channel , 框架自己去 accept了
            // 监听的 listen socket -》 accept -》 client
            // 普通的 socket  -》 R/W -》 data
            NioSocketChannel client = (NioSocketChannel) msg;

            // 2. 响应式的  handler
            ChannelPipeline pipeline = client.pipeline();
            pipeline.addLast(handler);  // 1. client::pipeline[ChannelInit]

            // 1. client 需要注册
            selector.register(client);

        }
    }


    @Test
    public void nettyClient () {
        try{
            NioEventLoopGroup group = new NioEventLoopGroup(1);
            Bootstrap bs = new Bootstrap();
            ChannelFuture future = bs.group(group)
                    .channel(NioSocketChannel.class)
//                    .handler(new ChannelInit())
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new MyInHandler());
                        }
                    })
                    .connect(new InetSocketAddress("192.168.72.10", 9090));
            Channel client = future.sync().channel();

            ByteBuf buf = Unpooled.copiedBuffer("Hello server".getBytes());
            ChannelFuture send = client.writeAndFlush(buf);
            send.sync();
            client.closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void nettyServer () {
        try{
            NioEventLoopGroup group = new NioEventLoopGroup(1);
            ServerBootstrap bs = new ServerBootstrap();
            ChannelFuture bind = bs.group(group, group)
                    .channel(NioServerSocketChannel.class)
//                    .childHandler(new ChannelInit())
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new MyInHandler());
                        }
                    })
                    .bind(new InetSocketAddress("192.168.31.172", 9090));

            bind.sync().channel().closeFuture().sync();

        }catch (Exception e) {
            e.printStackTrace();
        }

    }

}
