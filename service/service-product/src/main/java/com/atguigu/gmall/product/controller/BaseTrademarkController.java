package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.util.List;

@RestController
@RequestMapping("admin/product/baseTrademark")
@Api(tags = "品牌列表")
public class BaseTrademarkController {

    @Autowired
    private BaseTrademarkService baseTrademarkService;

    @ApiOperation(value = "获取品牌分页列表")
    @GetMapping("{page}/{limit}")
    public Result getTrademarkByPage(@PathVariable Long page,@PathVariable Long limit){
        Page<BaseTrademark> baseTrademarkPage = new Page<>(page, limit);
        IPage<BaseTrademark> baseTrademarkIPage = baseTrademarkService.getPage(baseTrademarkPage);
        return Result.ok(baseTrademarkIPage);
    }

    @ApiOperation("根据id获取品牌")
    @GetMapping("get/{id}")
    public Result getTrademarkById(@PathVariable String id){
        BaseTrademark baseTrademark = baseTrademarkService.getById(id);
        return Result.ok(baseTrademark);
    }

    @ApiOperation("添加品牌")
    @PostMapping("save")
    public Result saveTrademark(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }

    @ApiOperation("修改品牌")
    @PutMapping("update")
    public Result updateById(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.updateById(baseTrademark);
        return Result.ok();
    }

    @ApiOperation("删除品牌")
    @DeleteMapping("remove/{id}")
    public Result deleteById(@PathVariable Long id){
        baseTrademarkService.removeById(id);
        return Result.ok();
    }

    @ApiOperation("获取品牌属性")
    @GetMapping("getTrademarkList")
    public Result<List<BaseTrademark>> getTrademarkList(){
        List<BaseTrademark> trademarkList = baseTrademarkService.list(null);
        return Result.ok(trademarkList);
    }

    @ApiOperation("根据关键字获取Trademark列表，活动使用")
    @GetMapping("findBaseTrademarkByKeyword/{keyword}")
    public Result findBaseTrademarkByKeyword(@PathVariable String keyword){
        List<BaseTrademark> baseTrademarkList = baseTrademarkService.findBaseTrademarkByKeyword(keyword);
        return Result.ok(baseTrademarkList);
    }

    /**
     * 根据trademarkId列表获取trademark列表，活动使用
     * @param trademarkIdList
     * @return
     */
    @PostMapping("inner/findBaseTrademarkByTrademarkIdList")
    public List<BaseTrademark> findBaseTrademarkByTrademarkIdList(@RequestBody List<Long> trademarkIdList) {
        return baseTrademarkService.findBaseTrademarkByTrademarkIdList(trademarkIdList);
    }
}
