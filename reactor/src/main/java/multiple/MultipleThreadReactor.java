package multiple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多线程 reactor
 */
public class MultipleThreadReactor {
    ServerSocketChannel serverSocket;
    AtomicInteger next = new AtomicInteger(0);
    Selector[] selectors = new Selector[2];
    SubReactor[] subReactors = null;

    /**
     * 初始化
     *
     * @throws IOException
     */
    public MultipleThreadReactor() throws IOException {
        selectors[0] = Selector.open();
        selectors[1] = Selector.open();
        serverSocket = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress(6666);
        serverSocket.bind(address);
        //设置非阻塞
        serverSocket.configureBlocking(false);
        //第一个选择器监控新连接事件
        SelectionKey selectionKey = serverSocket.register(selectors[0], SelectionKey.OP_ACCEPT);
        //绑定handler
        selectionKey.attach(new AcceptHandler());
        //第一个子反应器 负责第一个选择器 负责接收新连接
        SubReactor subReactor1 = new SubReactor(selectors[0]);
        //第二个子反应器 负责第二选择器 负责io的读写
        SubReactor subReactor2 = new SubReactor(selectors[1]);
        subReactors = new SubReactor[]{subReactor1, subReactor2};
    }

    private void startService() {
        //一个子反应器对应一个线程
        new Thread(subReactors[0]).start();
        new Thread(subReactors[1]).start();
    }

    class SubReactor implements Runnable {
        public SubReactor(Selector selector) {
            this.selector = selector;
        }

        private Selector selector;

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    //等待channerl
                    selector.select();
                    //获取selectionKey
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> it = selectionKeys.iterator();
                    //遍历SelectionKey
                    while (it.hasNext()) {
                        SelectionKey sk = it.next();
                        //反应器负责dispatcher收到的事件
                        dispatch(sk);
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        private void dispatch(SelectionKey sk) {
            //获取之前绑定的AcceptHandler
            Runnable handler = (Runnable) sk.attachment();
            if (handler != null) {
                handler.run();
            }

        }
    }

    /**
     * 接收新连接
     */
    class AcceptHandler implements Runnable {

        @Override
        public void run() {
            try {
                //接收新连接
                SocketChannel channel = serverSocket.accept();
                if (channel != null) {
                    new MultiThreadEchoHandler(selectors[next.get()], channel);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (next.incrementAndGet() == selectors.length) {
                next.set(0);
            }
        }
    }

    /**
     * 处理io读写
     */
    class MultiThreadEchoHandler implements Runnable {
        Selector selector;
        SocketChannel socketChannel;
        SelectionKey selectionKey;
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        final static int RECIEVING = 0, SENDING = 1;
        int status = RECIEVING;
        //引入线程池
        ExecutorService pool = Executors.newFixedThreadPool(4);

        public MultiThreadEchoHandler(Selector selector, SocketChannel socketChannel) throws ClosedChannelException {
            this.selector = selector;
            this.socketChannel = socketChannel;
            //取消选择键 再设置刚兴趣的IO事件
            SelectionKey sk = socketChannel.register(selector, 0);
            sk.attach(this);
            //向sk 注册Read就绪事件
            sk.interestOps(SelectionKey.OP_READ);
            selector.wakeup();

        }

        @Override
        public void run() {
            //异步任务
            pool.execute(new AsyncTask());
        }

        public synchronized void asyncRun() {
            try {
                if (status == SENDING) {
                    //写通道
                    socketChannel.write(byteBuffer);
                    //写完后，准备开始通道，byteBuffer 切换写模式
                    byteBuffer.clear();
                    //写完注册 read 就绪事件
                    selectionKey.interestOps(SelectionKey.OP_READ);
                    //写完后 进入接收状态
                    status = RECIEVING;

                } else if (status == RECIEVING) {
                    //从通道接收数据
                    int length = 0;
                    while ((length = socketChannel.read(byteBuffer)) > 0) {

                    }
                    byteBuffer.flip();
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                    status = SENDING;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //异步任务的内部类
        class AsyncTask implements Runnable{

            @Override
            public void run() {
                MultiThreadEchoHandler.this.asyncRun();
            }
        }
    }



}
