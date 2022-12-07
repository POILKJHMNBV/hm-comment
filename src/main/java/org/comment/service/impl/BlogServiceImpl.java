package org.comment.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.comment.dto.Result;
import org.comment.dto.ScrollResult;
import org.comment.dto.UserDTO;
import org.comment.entity.Blog;
import org.comment.entity.Follow;
import org.comment.entity.User;
import org.comment.mapper.BlogMapper;
import org.comment.service.IBlogService;
import org.comment.service.IFollowService;
import org.comment.service.IUserService;
import org.comment.utils.SystemConstants;
import org.comment.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static org.comment.utils.RedisConstants.BLOG_LIKED_KEY;
import static org.comment.utils.RedisConstants.FEED_KEY;

@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        // 获取当前页数据
        List<Blog> records = page.getRecords();

        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            blog.setIsLike(isBlogLiked(blog.getId()));
        });

        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {

        // 1.查询博客信息
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }

        // 2.查询用户信息
        queryBlogUser(blog);

        // 3.查看该用户是否给该博客点赞
        blog.setIsLike(isBlogLiked(blog.getId()));
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        if (isBlogLiked(id)) {
            // 已经点赞，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            // 未点赞，点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1.查询top5的点赞用户（按时间排序）zrange k 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2.查询用户信息
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);

        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        blog.setUserId(userDTO.getId());

        // 2.保存探店博文
        boolean isSuccess = save(blog);
        if (isSuccess) {
            // 3.向粉丝推送消息
            List<Follow> fans = followService.query().eq("follow_user_id", userDTO.getId()).list();
            if (fans == null || fans.isEmpty()) {
                return Result.ok(blog.getId());
            }
            long currentTimeMillis = System.currentTimeMillis();
            fans.forEach(fan -> stringRedisTemplate.opsForZSet().add(FEED_KEY + fan.getUserId(), blog.getId().toString(), currentTimeMillis));
        } else {
            log.error("保存探店笔记失败！");
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {

        log.info("offset = {}", offset);

        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2.查询收件箱 ZREVRANGEBYSCORE key max min WITHSCORE LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(FEED_KEY + userId, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 3.解析数据：blogId、minTime、offset
        List<Long> ids = new ArrayList<>();
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 3.1 获取id
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));

            // 3.2 获取时间戳
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        log.info("os = {}", os);

        // 4、根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();
        blogs.forEach(blog -> {
            blog.setIsLike(isBlogLiked(blog.getId()));
            queryBlogUser(blog);
        });

        // 5、封装并返回数据
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private boolean isBlogLiked(Long id) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO != null) {
            Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userDTO.getId().toString());
            return BooleanUtil.isTrue(score != null);
        }
        else
            return false;
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
