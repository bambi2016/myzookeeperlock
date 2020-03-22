package com.bambi.myzookeeperlock.zookeeper;

public class MyLockTest {
    public static void main(String[] args){
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Lock lock = new MyzookeeperLock();
                    lock.lock();
                    System.out.println("开始");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("结束");
                    lock.unlock();
                }
            }).start();
        }

    }
}
