package com.atguigu.gmall.list.service;

import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;

import java.io.IOException;

public interface SearchService {

    /**
     * 上架商品。把数据添加到es中
     * @param skuId
     */
    void upperGoods(Long skuId);

    /**
     * 下架商品，把数据从es中删除
     * @param skuId
     */
    void lowerGoods(Long skuId);

    /**
     * 更新热点
     * @param skuId
     */
    void incrHotScore(Long skuId);

    /**
     * 检索数据接口
     * @param searchParam  用户输入的检索条件
     * @return
     */
    SearchResponseVo search(SearchParam searchParam) throws IOException;
}
