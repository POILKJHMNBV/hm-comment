package org.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.comment.dto.Result;
import org.comment.entity.SecKillVoucher;
import org.comment.entity.VoucherOrder;
import org.comment.mapper.VoucherOrderMapper;
import org.comment.service.ISecKillVoucherService;
import org.comment.service.IVoucherOrderService;
import org.comment.utils.RedisIdWorker;
import org.comment.utils.SimpleRedisLock;
import org.comment.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 利用分布式锁解决超卖，一人一单问题
 */
@Slf4j
@Service("VoucherOrderService")
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISecKillVoucherService secKillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result secKillVoucher(Long voucherId) {
        // 1.查询优惠券信息
        SecKillVoucher secKillVoucher = secKillVoucherService.getById(voucherId);
        if (secKillVoucher == null) {
            return Result.fail("优惠券不存在！");
        }

        // 2.判断秒杀是否开始和结束
        if (secKillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动尚未开始！");
        }
        if (secKillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已经结束！");
        }

        // 3.判断库存是否充足
        if (secKillVoucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        // 使用悲观锁控制并发访问（单机模式）
        /*synchronized (userId.toString().intern()) {
            // 获取代理对象，使事务生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*/

        // 利用redis的分布式锁控制集群的并发访问（集群模式）
        /*// 1.创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("voucherOrder" + userId, stringRedisTemplate);

        // 2.尝试获取锁
        boolean isLock = redisLock.tryLock(1);
        if (!isLock) {
            return Result.fail("该用户已经购买过此优惠券！");
        }
        // 获取代理对象，使事务生效
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            redisLock.unLock();
        }*/

        // 利用redisson实现分布式锁
        RLock rLock = redissonClient.getLock("lock:voucherOrder" + userId);
        boolean isLock = rLock.tryLock();
        if (!isLock) {
            return Result.fail("该用户已经购买过此优惠券！");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            rLock.unlock();
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1.一人一单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count == 1) {
            // 用户已经购买过该优惠券
            return Result.fail("该用户已经购买过此优惠券！");
        }

        // 2.扣减库存
        boolean result = secKillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();

        if (!result) {
            return Result.fail("库存不足！");
        }

        // 3.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long voucherOrderId = redisIdWorker.nextId("voucherOrder");
        voucherOrder.setId(voucherOrderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(voucherOrderId);
    }

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {}
}
