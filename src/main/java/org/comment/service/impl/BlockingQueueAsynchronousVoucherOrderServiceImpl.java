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
 * 利用阻塞队列异步秒杀优惠券
 */
@Slf4j
@Service("BlockingQueueAsynchronousVoucherOrderServiceImpl")
public class BlockingQueueAsynchronousVoucherOrderServiceImpl
        extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISecKillVoucherService secKillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SEC_KILL_SCRIPT;
    static {
        SEC_KILL_SCRIPT = new DefaultRedisScript<>();
        SEC_KILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SEC_KILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    private final BlockingQueue<VoucherOrder> voucherOrderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 使用异步线程执行创建订单的任务
    private final ExecutorService secKill_VoucherOrder_Executor = Executors.newSingleThreadExecutor();

    // 使用内部类封装异步线程要执行的任务
    private class VoucherHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.从阻塞队列中取出优惠券信息
                    VoucherOrder voucherOrder = voucherOrderTasks.take();

                    // 2.创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("创建订单异常！", e);
                }
            }
        }

        private void handlerVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();

            // 利用redisson实现分布式锁
            RLock rLock = redissonClient.getLock("lock:voucherOrder" + userId);
            boolean isLock = rLock.tryLock();
            if (!isLock) {
                log.error("获取锁失败！");
                return;
            }

            try {
                // 创建订单
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                // 释放锁
                rLock.unlock();
            }
        }
    }

    // 类初始化完成后就执行此方法，等待阻塞队列中的订单信息
    @PostConstruct
    public void init() {
        secKill_VoucherOrder_Executor.submit(new VoucherHandler());
    }

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

        // 3.获取用户ID
        Long userId = UserHolder.getUser().getId();

        // 4.执行Lua脚本，判断库存是否充足，用户是否已经购买过该优惠券
        Long result = stringRedisTemplate.execute(SEC_KILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        if (result != null && result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "该用户已经购买过此优惠券");
        }

        // 5.将订单信息保存至阻塞队列，由异步线程线程从阻塞队列取出订单信息创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long voucherOrderId = redisIdWorker.nextId("voucherOrder");
        voucherOrder.setId(voucherOrderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrderTasks.add(voucherOrder);

        // 6.获取代理对象，控制事务
        if (proxy == null) {
            proxy = (IVoucherOrderService) AopContext.currentProxy();
        }
        return Result.ok(voucherOrderId);
    }

    @Override
    public Result createVoucherOrder(Long voucherId) {
        return null;
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();

        // 1.一人一单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count == 1) {
            // 用户已经购买过该优惠券
            log.error("该用户已经购买过此优惠券！");
            return;
        }

        // 2.扣减库存
        boolean result = secKillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!result) {
            log.error("库存不足！");
        }

        // 3.创建订单
        save(voucherOrder);
    }
}
