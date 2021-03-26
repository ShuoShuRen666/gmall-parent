package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.mapper.SkuInfoMapper;
import com.atguigu.gmall.product.service.SkuInfoService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkuInfoServiceImpl implements SkuInfoService {

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    //根据关键字获取sku列表
    @Override
    public List<SkuInfo> findSkuInfoByKeyword(String keyword) {
        QueryWrapper<SkuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("sku_name",keyword);
        return skuInfoMapper.selectList(queryWrapper);
    }
}
