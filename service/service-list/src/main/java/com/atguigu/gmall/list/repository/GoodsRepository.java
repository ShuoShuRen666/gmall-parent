package com.atguigu.gmall.list.repository;

import com.atguigu.gmall.model.list.Goods;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

//最终继承了CrudRepository  可以对Goods进行crud方法
public interface GoodsRepository extends ElasticsearchRepository<Goods,Long> {
}
