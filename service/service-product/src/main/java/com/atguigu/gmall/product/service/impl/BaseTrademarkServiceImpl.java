package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.mapper.BaseTrademarkMapper;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BaseTrademarkServiceImpl extends ServiceImpl<BaseTrademarkMapper, BaseTrademark> implements BaseTrademarkService  {

    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    /**
     * 获取分页列表
     * @param pageParam
     * @return
     */
    @Override
    public IPage<BaseTrademark> getPage(Page<BaseTrademark> pageParam) {

        QueryWrapper<BaseTrademark> queryWrapper = new QueryWrapper<BaseTrademark>().orderByAsc();

        return baseTrademarkMapper.selectPage(pageParam,queryWrapper);
    }

    //根据关键字获取spu列表，活动使用
    @Override
    public List<BaseTrademark> findBaseTrademarkByKeyword(String keyword) {
        return baseTrademarkMapper.selectList(new QueryWrapper<BaseTrademark>().like("tm_name",keyword));
    }

    //根据trademarkId列表获取trademark列表，活动使用
    @Override
    public List<BaseTrademark> findBaseTrademarkByTrademarkIdList(List<Long> trademarkIdList) {
        return baseTrademarkMapper.selectBatchIds(trademarkIdList);
    }
}
