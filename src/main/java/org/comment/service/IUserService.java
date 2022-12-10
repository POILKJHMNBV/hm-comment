package org.comment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.comment.dto.Result;
import org.comment.entity.User;


public interface IUserService extends IService<User> {
     String code(String phone);
     User login(String phone);
     Result sign();
     Result signCount();
}
