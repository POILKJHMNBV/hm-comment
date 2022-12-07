package org.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.comment.dto.Result;
import org.comment.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result queryShopById(Long id);

    Result update(Shop shop);
}
