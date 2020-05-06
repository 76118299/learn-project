package singlieton;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * 单线程Reactor
 */
public class SinglietionThreadReactor implements Runnable {
    Selector selector;
    ServerSocketChannel serverSocketChannel;

    public SinglietionThreadReactor() throws IOException {
         serverSocketChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverSocketChannel.configureBlocking(false);
        //注册接收连接事件
        SelectionKey selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        //将新连接处理器作为属性，绑定到selectionKey
        selectionKey.attach(new AcceptorHandler());

    }

    public void run() {
        while (!Thread.interrupted()){

            try {
                /**
                 * 轮询select 返回就绪的channel的数量
                 * 没用则阻塞
                 */
                selector.select();
                //获取channel的selectKey
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                //迭代selectionkey
                while (iterator.hasNext()){
                    //获取SelectionKey
                    SelectionKey sk = iterator.next();
                    //对selectionKey进行分发
                    dispatche(sk);

                }
                //清空selectionKeys
                selectionKeys.clear();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void dispatche(SelectionKey sk) {
        //获取绑定的处理器
        Runnable handler = (Runnable) sk.attachment();
        if(handler!=null){
            handler.run();
        }
    }

    /**
     * 连接处理器
     */
    class AcceptorHandler implements Runnable{

        public void run() {
            try {
                //获取客户端channel
                SocketChannel channel = serverSocketChannel.accept();
                //处理客户端channel
                new EchoHandler(selector,channel);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
class EchoHandler implements Runnable{
    final SocketChannel channel;
    final Selector selector;
    SelectionKey selectionKey;
    final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    static  final int RECIEVING=0,SENDING=1;
    int state = RECIEVING;

    public EchoHandler( Selector selector,SocketChannel channel) throws IOException {
        this.channel = channel;
        this.selector = selector;

        //设置客户端通道非阻塞
        channel.configureBlocking(false);
        selectionKey = channel.register(selector, 0);
        selectionKey.attach(this);
        //注册读就绪事件
        selectionKey.interestOps(SelectionKey.OP_READ);
        selector.wakeup();
    }
    public void run() {
        //向通道写数据
        if(state == SENDING){
            try {
                channel.write(byteBuffer);
                //buffer切换些模式
                byteBuffer.clear();
                //byteBuffer.flip();
                //写完数据注册读就绪事件
                selectionKey.interestOps(SelectionKey.OP_READ);
                state = RECIEVING;

            } catch (IOException e) {
                e.printStackTrace();
            }
            //从通道读取数据
        }else if(state == RECIEVING){
            try {
                int length = 0;
                while ( (length= channel.read(byteBuffer))>0){
                    System.out.printf(new String(byteBuffer.array(),0,length));
                }
                byteBuffer.flip();
                //注册写就绪事件
                selectionKey.interestOps(SelectionKey.OP_WRITE);
                state = SENDING;
            }catch (Exception e){
                e.printStackTrace();
            }

        }
    }
}
