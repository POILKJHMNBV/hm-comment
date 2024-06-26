package org.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 利用Redis中的Stream数据结构异步秒杀优惠券
 */
@Slf4j
@Service("StreamAsynchronousVoucherOrderServiceImpl")
public class StreamAsynchronousVoucherOrderServiceImpl
        extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISecKillVoucherService secKillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SEC_KILL_SCRIPT;
    static {
        SEC_KILL_SCRIPT = new DefaultRedisScript<>();
        SEC_KILL_SCRIPT.setLocation(new ClassPathResource("seckillByStream.lua"));
        SEC_KILL_SCRIPT.setResultType(Long.class);
    }

    // 使用异步线程执行创建订单的任务
    private final ExecutorService secKill_VoucherOrder_Executor = Executors.newSingleThreadExecutor();

    // 使用内部类封装异步线程要执行的任务
    private class VoucherHandler implements Runnable {

        private final String queueName = "stream.voucherOrders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.从消息队列中取出优惠券信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 没有消息，进行下一次循环
                        continue;
                    }

                    // 3.创建订单，确认消息
                    createAndAckOrder(list);
                } catch (Exception e) {
                    log.error("创建订单异常！", e);
                    // 处理异常消息
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 没有异常信息，结束循环
                        break;
                    }

                    // 3.创建订单，确认消息
                    createAndAckOrder(list);
                } catch (Exception e) {
                    log.error("处理pending订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        private void createAndAckOrder(List<MapRecord<String, Object, Object>> list) {
            // 1.创建订单
            MapRecord<String, Object, Object> record = list.get(0);
            Map<Object, Object> value = record.getValue();
            VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
            proxy.createVoucherOrder(voucherOrder);

            // 2.确认消息
            stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
        }
    }

    // 类初始化完成后就执行此方法，等待阻塞队列中的订单信息
//    @PostConstruct
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
        long voucherOrderId = redisIdWorker.nextId("voucherOrder");     // 创建订单id
        Long result = stringRedisTemplate.execute(SEC_KILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(voucherOrderId));

        if (result != null && result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "该用户已经购买过此优惠券");
        }

        // 5.获取代理对象，控制事务
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
