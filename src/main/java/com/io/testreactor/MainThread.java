package com.io.testreactor;

public class MainThread {

    public static void main(String[] args) {
        // 这里不过关于IO和业务的事情

        // 1. 创建 IO Thread （一个或多个）
//        SelectorThreadGroup group = new SelectorThreadGroup(1);
        // 一个线程负责accept，每个都会被分配 client，进行R/W
        // boss 有自己的线程组
        SelectorThreadGroup boss = new SelectorThreadGroup(3);

        // worker 也有自己的线程组
        SelectorThreadGroup worker = new SelectorThreadGroup(3);

        // boss 多持有worker的引用
        boss.setWorker(worker);

        // 2. 把监听的server注册到某一个 selector 上
        // boss 里面选一个线程注册listen，触发bind，从而，这个被选中的线程得持有 workerGroup 的引用,
        // 因为未来listen一旦accept得到client后的worker的去worker中next出一个线程分配
        boss.bind(9999);
        boss.bind(8888);
        boss.bind(7777);
        boss.bind(6666);
    }
}
