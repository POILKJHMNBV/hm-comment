package org.comment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.comment.dto.Result;
import org.comment.entity.SecKillVoucher;
import org.comment.entity.Voucher;
import org.comment.mapper.VoucherMapper;
import org.comment.service.ISecKillVoucherService;
import org.comment.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static org.comment.utils.RedisConstants.SEC_KILL_STOCK_KEY;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISecKillVoucherService secKillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSecKillVoucher(Voucher voucher) {
        // 1.保存优惠券
        save(voucher);

        // 2.保存秒杀信息
        SecKillVoucher secKillVoucher = new SecKillVoucher();
        secKillVoucher.setVoucherId(voucher.getId());
        secKillVoucher.setStock(voucher.getStock());
        secKillVoucher.setBeginTime(voucher.getBeginTime());
        secKillVoucher.setEndTime(voucher.getEndTime());
        secKillVoucherService.save(secKillVoucher);

        // 3.将秒杀信息存入redis
        stringRedisTemplate.opsForValue().set(SEC_KILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }
}
