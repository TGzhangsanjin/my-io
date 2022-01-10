package com.io.file;

import java.io.*;

/**
 * 文件 IO
 * @Author 张三金
 * @Date 2022/1/10 0010 17:40
 * @Company jzb
 * @Version 1.0.0
 */
public class OSFileIo {

    static byte[] data = "123456789\n".getBytes();
    static String path = "/root/testfileio/out.txt";



    // 直接写文件，没有经过jvm的优化，每写依次 data 就有一次系统调用
    public static void testBaseFileIO () {
        File file = new File(path);
        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            while (true) {
                // 不断的往文件里面写文件
                outputStream.write(data);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 经过了 jvm 的缓存优化，当数据达到默认的 8kb的时候，才会调用一次系统调用，数据没有达到 8kb的时候都存放在 jvm的缓冲区中
    public static void testBufferedFileIO () {
        File file = new File(path);
        try {
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            while (true) {
                out.write(data);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
