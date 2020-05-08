package callable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class MyCallable implements Callable<String> {
    @Override
    public String call() throws Exception {
        System.out.printf("callable");
        return "callable";
    }
}

class Test{
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        MyCallable callable = new MyCallable();
        //FuturTask 是 callable 和 Thread的桥梁
        FutureTask<String> task = new FutureTask(callable);
        Thread t = new Thread(task);
        t.start();
        String s= task.get();
        System.out.printf(s);

    }
}
