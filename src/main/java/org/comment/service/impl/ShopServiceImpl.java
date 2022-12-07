package org.comment.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.comment.dto.Result;
import org.comment.entity.Shop;
import org.comment.mapper.ShopMapper;
import org.comment.service.IShopService;
import org.comment.utils.CacheClient;
import org.comment.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.comment.utils.RedisConstants.*;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
        // Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺信息不存在");
        }
        return Result.ok(shop);
    }

    // 向缓存中存入空值解决缓存穿透
    private Shop queryWithPassThrough(Long id) {
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2.判断redis中是否存在商铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            // redis中存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson != null) {
            // 缓存中存在空值
            return null;
        }

        // 3.不存在，查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            // 数据库中不存在，向缓存中写入空值，避免缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 4.数据库中存在，将商铺数据写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 5.返回商铺信息
        return shop;
    }

    // 利用互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id) {
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2.判断redis中是否存在商铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            // redis中存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson != null) {
            // 缓存中存在空值
            return null;
        }

        // 3.不存在，查询数据库，开始缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            // 3.1 获取互斥锁
            boolean lock = tryLock(lockKey);

            // 3.2 判断获取互斥锁是否成功
            if (!lock) {
                // 获取互斥锁失败，线程休眠一会儿
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 3.3 成功获取到互斥锁，查询数据库，重建缓存
            // 重新检查redis中是否存在数据，防止重复更新缓存
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shopJson)) {
                // redis中存在，直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            shop = getById(id);
            if (shop == null) {
                // 数据库中不存在，向缓存中写入空值，避免缓存穿透
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 数据库中存在，将商铺数据写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 3.4 释放互斥锁，保证互斥锁在任何时候都能够释放
            unLock(lockKey);
        }
        // 5.返回商铺信息
        return shop;
    }

    // 缓存重建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 利用逻辑过期解决缓存击穿
    private Shop queryWithLogicalExpire(Long id) {
        // 1.从redis中查询商铺缓存
        String redisDataJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2.判断redis中是否存在商铺信息
        if (redisDataJson == null) {
            // redis中不存在，直接返回
            return null;
        }

        // 3.存在
        // 3.1 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return shop;
        }

        // 已过期
        // 3.2 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);

        // 重新检查缓存是否过期，防止重复更新缓存
        redisDataJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 缓存已经更新，直接返回
            return JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        }

        if (lock) {
            // 成功获取互斥锁
            // 3.3 开启独立线程更新缓存数据
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> this.saveShop(id, 20L));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unLock(lockKey);
            }
        }

        // 4.返回过期商铺信息
        return shop;
    }

    private boolean tryLock(String key) {
        // 利用setnx命令模拟互斥锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 提前将店铺信息导入redis，模拟缓存击穿
    public void saveShop(Long id, long expireSeconds) {
        // 1.查询店铺数据
        Shop shop = getById(id);

        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        // 1.更新数据库
        boolean result = updateById(shop);
        if (!result) {
            log.error("店铺信息更新失败！");
            return Result.fail("店铺信息更新失败！");
        }

        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
