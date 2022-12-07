package org.comment.service.impl;

import org.comment.CommentApplicationTests;
import org.comment.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


class UserServiceImplTest extends CommentApplicationTests {

    @Autowired
    private IUserService userService;

    @Test
    void testCode() {
        String code = userService.code("13768902489");
        System.out.println(code);
    }
}