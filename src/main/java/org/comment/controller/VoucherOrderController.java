package org.comment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.comment.dto.Result;
import org.comment.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-order")
@Tag(name = "VoucherOrderController", description = "优惠券订单web接口")
public class VoucherOrderController {

    @Resource(name = "BlockingQueueAsynchronousVoucherOrderServiceImpl")
    private IVoucherOrderService voucherOrderService;

    /**
     * 新增秒杀券订单
     * @param voucherId 秒杀券id
     * @return  订单id
     */
    @PostMapping("/seckill/{id}")
    @Operation(summary = "新增秒杀券订单")
    public Result secKillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.secKillVoucher(voucherId);
    }
}
