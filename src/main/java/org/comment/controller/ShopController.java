package org.comment.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.comment.dto.Result;
import org.comment.entity.Shop;
import org.comment.service.IShopService;
import org.comment.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static org.comment.utils.SystemConstants.MAX_PAGE_SIZE;

@RestController
@RequestMapping("/shop")
@Tag(name = "ShopController", description = "商铺web接口")
public class ShopController {

    @Resource
    private IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据id查询商铺具体信息")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryShopById(id);
    }


    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 当前页
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    @Operation(summary = "根据商铺类型分页查询商铺信息")
    public Result queryShopByType(@RequestParam("typeId") Integer typeId,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Shop> page = shopService.query().eq("type_id", typeId)
                .page(new Page<>(current, MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    @Operation(summary = "新增商铺信息")
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺信息
     * @return 更新结果
     */
    @PutMapping
    @Operation(summary = "更新商铺信息")
    public Result updateShop(@RequestBody Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空！");
        }
        return shopService.update(shop);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName (
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据商铺名称关键字分页查询商铺信息
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
