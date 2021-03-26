package com.atguigu.gmall.activity.mapper;

import com.atguigu.gmall.model.activity.ActivityInfo;
import com.atguigu.gmall.model.activity.ActivityRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActivityInfoMapper extends BaseMapper<ActivityInfo> {

    List<Long> selectExistSkuIdList(@Param("skuIdList") List<Long> skuIdList);

    //根据skuId找到活动规则列表
    List<ActivityRule> selectActivityRuleList(Long skuId);

    //获取skuIdList对应的活动规则列表
    List<ActivityRule> selectCartActivityRuleList(@Param("skuIdList") List<Long> skuIdList);

}
