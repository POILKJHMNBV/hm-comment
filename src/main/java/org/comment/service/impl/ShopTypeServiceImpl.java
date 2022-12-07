package org.comment.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.comment.dto.Result;
import org.comment.entity.ShopType;
import org.comment.mapper.ShopTypeMapper;
import org.comment.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.stream.Collectors;

import static org.comment.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从redis中查询店铺类型信息
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        List<ShopType> shopTypes;

        // 2.redis中存在店铺类型数据，返回数据
        if (ObjectUtil.isNotEmpty(shopTypeList)){
            shopTypes = shopTypeList.stream().map(shopType -> JSONUtil.toBean(shopType, ShopType.class)).collect(Collectors.toList());
            return Result.ok(shopTypes);
        }

        // 3.redis中不存在店铺类型数据，查询数据库
        shopTypes = query().orderByAsc("sort").list();

        // 4.判断数据库中是否存在店铺类型数据
        if (shopTypes == null) {
            // 数据库中不存在
            return Result.fail("店铺类型信息不存在！");
        }

        // 5.将店铺类型数据写入redis
        shopTypes.forEach(shopType -> stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType)));

        return Result.ok(shopTypes);
    }
}
