package org.comment;

import org.comment.entity.Shop;
import org.comment.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.comment.utils.RedisConstants.SHOP_GEO_KEY;

class GeoTest extends CommentApplicationTests {

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testGeoAdd() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();

        // 2.将店铺信息按照TypeId分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        // 3.将店铺的经纬度信息和id写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1 获取店铺类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;

            // 3.2 获取同类型店铺的集合
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());

            for (Shop shop : shops) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}
