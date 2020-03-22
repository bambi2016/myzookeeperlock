package com.bambi.myzookeeperlock.zookeeper;

public interface Lock {
    void lock();
    void unlock();
}
