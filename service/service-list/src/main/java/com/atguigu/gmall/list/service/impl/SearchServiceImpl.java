package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.swing.text.Highlighter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 上架商品。把数据添加到es中
     * @param skuId
     */
    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();

        //创建异步编排对象
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            //根据skuId获取sku基本信息和图片列表
            SkuInfo skuInfo = productFeignClient.getAttrValueList(skuId);
            if (skuInfo != null) {
                //设置基本信息
                goods.setId(skuInfo.getId());
                goods.setPrice(skuInfo.getPrice().doubleValue());
                goods.setTitle(skuInfo.getSkuName());
                goods.setDefaultImg(skuInfo.getSkuDefaultImg());
                goods.setCreateTime(new Date());
            }
            return skuInfo;
        });

        CompletableFuture<Void> trademarkCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //获取品牌信息
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            if (trademark != null) {
                //设置品牌信息
                goods.setTmId(trademark.getId());
                goods.setTmName(trademark.getTmName());
                goods.setTmLogoUrl(trademark.getLogoUrl());
            }
        });

        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
            //获取分类信息
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            if (categoryView != null) {
                //设置分类信息
                goods.setCategory1Id(categoryView.getCategory1Id());
                goods.setCategory1Name(categoryView.getCategory1Name());
                goods.setCategory2Id(categoryView.getCategory2Id());
                goods.setCategory2Name(categoryView.getCategory2Name());
                goods.setCategory3Id(categoryView.getCategory3Id());
                goods.setCategory3Name(categoryView.getCategory3Name());
            }
        });

        CompletableFuture<Void> SearchAttrCompletableFuture = CompletableFuture.runAsync(() -> {
            //获取平台属性
            List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
            if (!StringUtils.isEmpty(attrList)) {
                List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
                    SearchAttr searchAttr = new SearchAttr();
                    searchAttr.setAttrId(baseAttrInfo.getId());
                    searchAttr.setAttrName(baseAttrInfo.getAttrName());
                    searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());
                    return searchAttr;
                }).collect(Collectors.toList());
                //设置平台属性集合
                goods.setAttrs(searchAttrList);
            }
        });

        //汇总
        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                trademarkCompletableFuture,
                categoryViewCompletableFuture,
                SearchAttrCompletableFuture
                ).join();

        goodsRepository.save(goods);
    }

    /**
     * 下架商品，把数据从es中删除
     * @param skuId
     */
    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    /**
     * 更新热点
     * @param skuId
     */
    @Override
    public void incrHotScore(Long skuId) {
        /*
        1.使用redis记录商品被访问的次数，
            a.采用什么数据类型
            b.key如何取名
        2.当达到一定规则的时候，更新es
         */
        String hotKey = "hotScore";
        //第一个参数是key，第二个是成员，第三个是步长
        Double hotScore = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId" + skuId, 1);

        //当访问量达到10次的时候，更新es
        if(!StringUtils.isEmpty(hotScore)){
            if(hotScore % 10 == 0){
                Optional<Goods> optional = goodsRepository.findById(skuId);
                Goods goods = optional.get();
                //修改热度排名的值
                goods.setHotScore(hotScore.longValue());
                goodsRepository.save(goods);
            }
        }

    }

    /**
     * 检索数据接口
     * @param searchParam  用户输入的检索条件
     * @return
     */
    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        /*
        1.通过java代码编写dsl语句
        2.将dsl语句查询到的结果集，封装给SearchResponseVO对象
         */
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        //进行查询操作,获取到查询结果（响应）
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        //需要将 SearchResponse 封装成 SearchResponseVo
        //给 SearchResponseVo 部分属性赋值 trademarkList , attrsList , goodsList , total
        SearchResponseVo responseVo = this.parseSearchResult(searchResponse);
        /*
        给 SearchResponseVo 部分属性赋值
        private Integer pageSize;//每页显示的内容
        private Integer pageNo;//当前页面
        private Long totalPages;//总页数
         */
        responseVo.setPageSize(searchParam.getPageSize());
        responseVo.setPageNo(searchParam.getPageNo());
        //在工作中总结出来的求总页数的公式，responseVo.getTotal() + searchParam.getPageSize()-1)/searchParam.getPageSize()
        long totalPages = (responseVo.getTotal() + searchParam.getPageSize()-1)/searchParam.getPageSize();
        responseVo.setTotalPages(totalPages);
        return responseVo;
    }

    /**
     * 数据封装
     * @param searchResponse
     * @return
     */
    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        //给 SearchResponseVo 部分属性赋值  trademarkList , attrsList , goodsList , total
        SearchHits hits = searchResponse.getHits();

        //1.设置品牌集合 trademarkList
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        //通过map来获取到对应的数据  Aggregation ->  ParsedLongTerms
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            //声明一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();

            //设置品牌id
            String keyAsString = bucket.getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(keyAsString));

            //设置品牌name
            ParsedStringTerms tmNameAgg = bucket.getAggregations().get("tmNameAgg");
            //品牌name
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);

            //设置品牌url
            ParsedStringTerms tmUrlAgg = bucket.getAggregations().get("tmLogoUrlAgg");
            //品牌name
            String tmUrl = tmUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmUrl);

            return searchResponseTmVo;
        }).collect(Collectors.toList());

        searchResponseVo.setTrademarkList(trademarkList);



        //2.设置平台属性集合  attrsList  attrAgg 属于nested数据类型
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        //转完之后再获取attrIdAgg
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        //获取对应的平台属性数据
        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
            //获取到平台属性id
            String keyAsString = bucket.getKeyAsString();
            searchResponseAttrVo.setAttrId(Long.parseLong(keyAsString));

            //获取平台属性名
            ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseAttrVo.setAttrName(attrName);

            //获取平台属性值集合
            //平台属性值名称对应多个数据，需要循环获取到里面的每一个key 所对应的数据
            ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attrValueAgg");
            List<? extends Terms.Bucket> buckets = attrValueAgg.getBuckets();

//            List<String> collect = buckets.stream().map(bucket1 -> {
//                String keyAsString1 = bucket1.getKeyAsString();
//                return keyAsString1;
//            }).collect(Collectors.toList());
            //表示通过 Terms.Bucket::getKeyAsString 来获取key
            List<String> values = buckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
            searchResponseAttrVo.setAttrValueList(values);

            return searchResponseAttrVo;
        }).collect(Collectors.toList());

        searchResponseVo.setAttrsList(attrsList);


        //3.设置商品集合 goodsList
        SearchHit[] subHits = hits.getHits();
        List<Goods> goodsList = new ArrayList<>();
        if (subHits != null) {
            for (SearchHit subHit : subHits) {
                //是一个Goods.class  组成的json字符串
                String sourceAsString = subHit.getSourceAsString();

                //将sourceAsString 变为Goods字符串
                Goods goods = JSON.parseObject(sourceAsString, Goods.class);
                //细节： 获取到高亮字段，如果是通过关键词搜索，需要高亮显示
                if (subHit.getHighlightFields().get("title") != null) {
                    //说明是通过关键词搜索的
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }

                goodsList.add(goods);
            }
            searchResponseVo.setGoodsList(goodsList);
        }


        //4.设置总条数 total
        searchResponseVo.setTotal(hits.getTotalHits());
        return searchResponseVo;
    }


    /**
     * 编写 dsl 语句
     * @param searchParam 用户输入的查询条件
     * @return
     */
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        //构建查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //query -- bool
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //query -- bool -- filter
        //boolQueryBuilder.filter()
        //判断用户是否根据分类id查询
        if (searchParam.getCategory1Id() != null) {
            //query -- bool -- filter -- term
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id()));
        }
        if (searchParam.getCategory2Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id()));
        }
        if (searchParam.getCategory3Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id()));
        }

        //判断用户是否根据品牌过滤
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)) {
            //query -- bool -- filter -- term
            String[] split = trademark.split(":");
            if(split != null && split.length == 2){
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId",split[0]));
            }
        }

        //判断用户是否根据平台属性值过滤
        //props=23:4G:运行内存
        //平台属性Id ，平台属性值名称  平台属性名
        String[] props = searchParam.getProps();
        if (props != null && props.length > 0) {
            for (String prop : props) {
                //prop = 23:4G:运行内存
                String[] split = prop.split(":");
                if (split != null && split.length == 3) {
                    //创建两个bool
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();

                    //构建查询条件
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));
                    //TODO attrName为什么不要，做完了来看看
                    //将subBoolQuery 赋值到 boolQuery中
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));

                    //将boolQuery 赋值给总的boolQueryBuilder
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }

        //判断用户是否根据关键字过滤
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            //query -- bool -- must -- match   Operator.AND:表示关键字的分词 必须全部匹配（小米手机    小米   手机  ）
            boolQueryBuilder.must(QueryBuilders.matchQuery("title",searchParam.getKeyword()).operator(Operator.AND));
        }

        //最顶层 query 方法
        searchSourceBuilder.query(boolQueryBuilder);

        //分页查询
        //从第几条开始查询
        int from = (searchParam.getPageNo() - 1) * searchParam.getPageSize();
        searchSourceBuilder.from(from);
        //默认每页显示多少条数据
        searchSourceBuilder.size(searchParam.getPageSize());

        //排序
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)) {
            String[] split = order.split(":");
            if (split != null && split.length == 2) {
                String field = "";
                //判断数组中的第一个元素 ， 如果是1，则按照热度排序，是2 则按照价格排序。。
                switch (split[0]){
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "price";
                        break;
                }
                //使用三元表达式进行判断
                searchSourceBuilder.sort(field, "asc".equals(split[1]) ? SortOrder.ASC:SortOrder.DESC);
            }else {
                //默认排序规则
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }

        //高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("<span>");
        searchSourceBuilder.highlighter(highlightBuilder);

        //聚合
        //品牌聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        searchSourceBuilder.aggregation(termsAggregationBuilder);

        //  设置平台属性聚合 特殊的数据类型nested
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        //优化    设置需要的字段 id  defaultImage  title   price  其他字段在展示的时候显示为 null 即可
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);

        //GET /goods/info/_search
        //表明查询 goods库
        SearchRequest searchRequest = new SearchRequest("goods");
        //表明查询 info 表
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);

        //打印DSL语句
        System.out.println("DSL语句：\t" + searchSourceBuilder);

        return searchRequest;
    }
}
