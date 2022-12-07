package org.comment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.comment.dto.Result;
import org.comment.entity.Voucher;
import org.comment.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher")
@Tag(name = "VoucherController", description = "优惠券web接口")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    @Operation(summary = "查询店铺的优惠券列表")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 新增秒杀券
     * @param voucher 秒杀券信息
     * @return  秒杀券id
     */
    @PostMapping("/secKill")
    @Operation(summary = "新增秒杀券")
    public Result addSecKillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSecKillVoucher(voucher);
        return Result.ok(voucher.getId());
    }
}
