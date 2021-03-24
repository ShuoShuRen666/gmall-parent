package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ListController {

    @Autowired
    private ListFeignClient listFeignClient;


    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model) {
        Result<Map> result = listFeignClient.list(searchParam);

        //排序 ${orderMap.type}   ${orderMap.sort}
        Map<String,Object> orderMap = this.orderByMap(searchParam.getOrder());
        model.addAttribute("orderMap",orderMap);

        // 面包屑处理 ${propsParamList}  ${trademarkParam}
        String trademarkParam = this.makeTrademark(searchParam.getTrademark());
        model.addAttribute("trademarkParam",trademarkParam);
        List<Map<String,String>> propsParamList = this.makeProps(searchParam.getProps());
        model.addAttribute("propsParamList",propsParamList);

        // ${urlParam} 存储一个主要的数据 urlParam = 记录用户通过什么条件进行检索
        //urlParam 记录 ？ 后面的所有条件
        String urlParam = this.makeUrlParam(searchParam);
        model.addAttribute("urlParam",urlParam);
        model.addAttribute("searchParam",searchParam);
        model.addAllAttributes(result.getData());

        return "list/index";
    }

    private Map<String, Object> orderByMap(String order) {
        Map<String,Object> orderMap = new HashMap<>();
        if (!StringUtils.isEmpty(order)) {
            String[] split = order.split(":");
            if (split != null && split.length == 2) {
                // 传递的哪个字段
                orderMap.put("type",split[0]);
                //升序还是降序
                orderMap.put("sort",split[1]);
            }
        }else {
            //默认排序规则
            orderMap.put("type", "1");
            orderMap.put("sort", "asc");
        }

        return orderMap;
    }

    /**
     * 处理平台属性条件回显 面包屑
     * @param props
     * @return
     */
    private List<Map<String, String>> makeProps(String[] props) {
        List<Map<String, String>> list = new ArrayList<>();
        if (props != null && props.length != 0) {
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split != null && split.length == 3) {
                    Map<String, String> map = new HashMap<>();
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrName",split[2]);
                    list.add(map);
                }
            }
        }

        return list;
    }

    /**
     * 处理品牌条件回显 面包屑
     * @param trademark
     * @return
     */
    private String makeTrademark(String trademark) {
        if (!StringUtils.isEmpty(trademark)) {
            String[] split = trademark.split(":");
            if (split != null && split.length == 2) {
                return "品牌:" + split[1];
            }
        }
        return null;
    }

    /**
     * 记录用户通过什么条件进行检索,返回总的url地址
     * @param searchParam
     * @return
     */
    private String makeUrlParam(SearchParam searchParam) {
        StringBuilder urlParam = new StringBuilder();
        //判断用户是否通过分类id检索
        //http://list.atguigu.cn/list.html?category3Id=61
        if (!StringUtils.isEmpty(searchParam.getCategory1Id())) {
            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }
        if (!StringUtils.isEmpty(searchParam.getCategory2Id())) {
            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
        }
        if (!StringUtils.isEmpty(searchParam.getCategory3Id())) {
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }

        //判断用户是否根据关键字检索
        //http://list.atguigu.cn/list.html?keyword=小米手机
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            urlParam.append("keyword=").append(searchParam.getKeyword());
        }

        //判断用户是否点击品牌检索
        if (!StringUtils.isEmpty(searchParam.getTrademark())) {
            if (urlParam.length() > 0) {
                urlParam.append("&trademark=").append(searchParam.getTrademark());
            }
        }

        //判断用户是否点击平台属性值检索
        //&props=106:安卓手机:手机一级
        if (!StringUtils.isEmpty(searchParam.getProps())) {
            for (String prop : searchParam.getProps()) {
                if (urlParam.length() > 0) {
                    urlParam.append("&props=").append(prop);
                }
            }
        }

        return "list.html?" + urlParam.toString();
    }
}
