-- 1.参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]
-- 1.3.订单id
local voucherOrderId = ARGV[3]

-- 2.数据key
-- 2.1 库存key
local stockKey = 'secKill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'secKill:voucherOrder:' .. voucherId

-- 3.脚本业务
-- 3.1 判断库存是否充足
if (tonumber(redis.call('GET', stockKey)) <= 0) then
    -- 3.2 库存不足，返回1
    return 1
end
-- 3.3 判断用户是否下单
if (redis.call('SISMEMBER', orderKey, userId) == 1) then
    -- 3.4 用户已经下过单，返回2
    return 2
end

-- 3.4 扣减库存
redis.call('INCRBY', stockKey, -1)
-- 3.5 下单
redis.call('SADD', orderKey, userId)
-- 3.6.发送消息到队列中， XADD stream.voucherOrders * k1 v1 k2 v2 ...
redis.call('XADD', 'stream.voucherOrders', '*', 'userId', userId, 'voucherId', voucherId, 'id', voucherOrderId)
return 0