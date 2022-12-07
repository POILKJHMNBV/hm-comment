package org.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.comment.dto.Result;
import org.comment.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result secKillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
