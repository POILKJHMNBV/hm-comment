package org.comment;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;

@Slf4j
class RedissonTest extends CommentApplicationTests{

    @Resource
    private RedissonClient redissonClient;

    @Test
    void testReentrantLock() {
        method1();
    }

    public void method1() {
        log.info("method1开始获取锁........");
        RLock rLock = redissonClient.getLock("voucherOrder");
        boolean isLock = rLock.tryLock();
        if (!isLock) {
            log.error("method1获取锁失败！");
        }
        log.info("method1开始执行..........");
        System.out.println("I am method1");
        log.info("method1开始调用method2.........");
        method2();
        log.info("method2执行结束.........");
        log.info("method1执行结束..........");
        log.info("method1开始释放锁........");
        rLock.unlock();
    }

    public void method2() {
        log.info("method2开始获取锁........");
        RLock rLock = redissonClient.getLock("voucherOrder");
        boolean isLock = rLock.tryLock();
        if (!isLock) {
            log.error("method2获取锁失败！");
        }
        log.info("method2开始执行..........");
        System.out.println("I am method2");
        log.info("method2执行结束..........");
        log.info("method2开始释放锁........");
        rLock.unlock();
    }
}
