package org.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.comment.dto.Result;
import org.comment.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);

    void addSecKillVoucher(Voucher voucher);

}
