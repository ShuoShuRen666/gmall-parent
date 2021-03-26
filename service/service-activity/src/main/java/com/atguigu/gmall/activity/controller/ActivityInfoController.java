package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.ActivityInfoService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.ActivityInfo;
import com.atguigu.gmall.model.activity.ActivityRuleVo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/admin/activity/activityInfo")
public class ActivityInfoController {

    @Autowired
    private ActivityInfoService activityInfoService;

    @ApiOperation(value = "获取分页列表")
    @GetMapping("{page}/{limit}")
    public Result getPage(@PathVariable Long page ,@PathVariable Long limit){
        Page<ActivityInfo> infoPage = new Page<>(page,limit);
        IPage<ActivityInfo> pageModel = activityInfoService.getPage(infoPage);
        return Result.ok(pageModel);
    }

    @ApiOperation("新增")
    @PostMapping("save")
    public Result save(@RequestBody ActivityInfo activityInfo){
        activityInfo.setCreateTime(new Date());
        activityInfoService.save(activityInfo);
        return Result.ok();
    }

    @ApiOperation("通过id回显数据")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id){
        ActivityInfo activityInfo = activityInfoService.getById(id);
        activityInfo.setActivityTypeString(ActivityType.getNameByType(activityInfo.getActivityType()));
        return Result.ok(activityInfo);
    }

    @ApiOperation("修改")
    @PutMapping("update")
    public Result update(@RequestBody ActivityInfo activityInfo){
        activityInfoService.updateById(activityInfo);
        return Result.ok();
    }

    @ApiOperation("删除")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        activityInfoService.removeById(id);
        return Result.ok();
    }

    @ApiOperation("根据id列表批量删除")
    @DeleteMapping("batchRemove")
    public Result batchRemove(@RequestBody List<Long> idList){
        activityInfoService.removeByIds(idList);
        return Result.ok();
    }

    @ApiOperation("保存活动规则,先删除，再添加")
    @PostMapping("saveActivityRule")
    public Result saveActivityRule(@RequestBody ActivityRuleVo activityRuleVo){
        activityInfoService.saveActivityRule(activityRuleVo);
        return Result.ok();
    }

    //根据关键字获取sku列表，活动使用
    @GetMapping("findSkuInfoByKeyword/{keyword}")
    public Result findSkuInfoByKeyword(@PathVariable String keyword){
        return Result.ok(activityInfoService.findSkuInfoByKeyword(keyword));
    }

    //获取活动规则
    @GetMapping("findActivityRuleList/{id}")
    public Result findActivityRuleList(@PathVariable Long id){
        return Result.ok(activityInfoService.findActivityRuleList(id));
    }


}
