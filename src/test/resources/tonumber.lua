if (tonumber(redis.call('GET', 'secKill:stock:12')) > 0) then
    -- 3.2 库存不足，返回1
    return tonumber(redis.call('GET', 'secKill:stock:12'))
end
return 0