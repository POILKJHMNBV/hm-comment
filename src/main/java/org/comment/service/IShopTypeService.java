package org.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.comment.dto.Result;
import org.comment.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {
    Result queryTypeList();
}
