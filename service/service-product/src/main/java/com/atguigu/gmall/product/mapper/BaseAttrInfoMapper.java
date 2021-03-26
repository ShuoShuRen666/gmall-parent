package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lettuce.core.dynamic.annotation.Param;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BaseAttrInfoMapper extends BaseMapper<BaseAttrInfo> {

    //因为传递的参数是多个，需要@Patam注解的指定
    List<BaseAttrInfo> selectBaseAttrInfoList(
            @Param("category1Id") Long category1Id,
            @Param("category2Id") Long category2Id,
            @Param("category3Id") Long category3Id);

    List<BaseAttrInfo> selectBaseAttrInfoListBySkuId(@Param("skuId") Long skuId);
}
