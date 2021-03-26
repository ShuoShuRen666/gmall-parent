package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;

import java.util.List;

public interface UserAddressService {

    /**
     * 根据用户id查询用户收货地址列表
     * @param userId
     * @return
     */
    List<UserAddress> findUserAddressListByUserId(String userId);
}
