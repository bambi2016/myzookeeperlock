package com.bambi.myzookeeperlock.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MyzookeeperLock implements Lock {
    private static Logger logger = LoggerFactory.getLogger(MyzookeeperLock.class);

    private String address = "192.168.1.131:2181,192.168.1.122:2181,192.168.1.133:2181";
    private int timeout = 4000;
    private ZooKeeper zooKeeper;
    private String ephemeral_sequential = null;
    private String parent = "/parent";
    private String separator = "/";
    private String path = "child";
    private String before;
    //我看其他人写了latch这个变量，但个人觉得这个可以不要
    private CountDownLatch latch = new CountDownLatch(1);
    private CountDownLatch downLatch = new CountDownLatch(1);

    public MyzookeeperLock() {
    }

    @Override
    public void lock() {

        try {
            //1、连接到zookeeper服务器
            connection();
            //2、获取锁的过程
            waittolock();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    private void connection() throws IOException, InterruptedException {
        zooKeeper = new ZooKeeper(address, timeout, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                logger.debug("事件类型："+event.getType());
                if (event.getState().equals(Event.KeeperState.SyncConnected)) {
                    latch.countDown();
                }

            }
        });
        logger.debug("创建zooKeeper连接成功");
        latch.await();
        try {
            //如果父节点不存在，则创建父节点
            Stat exists = zooKeeper.exists(parent, false);
            logger.debug("父节点是否存在：" + exists);
            if (exists == null) {
                String persistent = zooKeeper.create(parent, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.debug("创建父节点成功:" + persistent);
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void waittolock() throws KeeperException, InterruptedException {
        //1、创建临时序列节点
        ephemeral_sequential = zooKeeper.create(parent + separator + path, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        logger.debug("创建成功：" + ephemeral_sequential);
        //2、判断该节点是否是排第一的节点
        List<String> children = zooKeeper.getChildren(parent, false);
        //如果为空或者长度为零直接返回（事实上这种情况不可能发生）
        if (children == null || children.size() == 0) {
            return;
        }
        Collections.sort(children);
        //如果排第一，直接返回
        String node = ephemeral_sequential.split("/")[2];
        if (node.equals(children.get(0))) {
            return;
        }
        //3、否则监听前一个节点
        before = parent + separator + children.get(children.indexOf(node) - 1);
        Stat exists = zooKeeper.exists(before, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                //如果前一个节点被删除，则downLatch-1，然后执行downLatch.await()之后的代码
                if (event.getType().equals(Event.EventType.NodeDeleted)) {
                    downLatch.countDown();
                }
            }
        });
        //说明刚才还有，现在被删了
        if (exists == null) {
            return;
        }
        downLatch.await();
        //4、加锁成功
        return;

    }


    /**
     * 释放锁
     * 一：删除节点
     * 二：关闭连接，即使删除节点失败，关闭连接之后，临时节点也会被删除
     */
    @Override
    public void unlock() {
        try {
            //删除创建的节点
            zooKeeper.delete(ephemeral_sequential, -1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        } finally {
            try {
                zooKeeper.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
