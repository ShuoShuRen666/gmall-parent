package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.CouponInfoService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.activity.CouponRuleVo;
import com.atguigu.gmall.model.enums.CouponType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/activity/couponInfo")
@Slf4j
public class CouponInfoController {

    @Autowired
    private CouponInfoService couponInfoService;

    @ApiOperation("分页获取优惠券列表")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page, @PathVariable Long limit){
        Page<CouponInfo> pageParam = new Page<>(page, limit);
        IPage<CouponInfo> couponInfoIPage = couponInfoService.selectPage(pageParam);
        return Result.ok(couponInfoIPage);
    }

    @ApiOperation("根据id回显数据")
    @GetMapping("get/{id}")
    public Result get(@PathVariable String id){
        CouponInfo couponInfo = couponInfoService.getById(id);
        couponInfo.setCouponTypeString(CouponType.getNameByType(couponInfo.getCouponType()));
        return Result.ok(couponInfo);
    }

    @ApiOperation("新增优惠券信息")
    @PostMapping("save")
    public Result save(@RequestBody CouponInfo couponInfo){
        couponInfoService.save(couponInfo);
        return Result.ok();
    }

    @ApiOperation("修改优惠券信息")
    @PutMapping("update")
    public Result updateById(@RequestBody CouponInfo couponInfo){
        couponInfoService.updateById(couponInfo);
        return Result.ok();
    }

    @ApiOperation("删除优惠券信息")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        couponInfoService.removeById(id);
        return Result.ok();
    }

    @ApiOperation("根据id列表删除优惠券信息")
    @DeleteMapping("batchRemove")
    public Result batchRemoveByIdList(@RequestBody List<Long> idList){
        couponInfoService.removeByIds(idList);
        return Result.ok();
    }

    @ApiOperation("新增优惠券规则（大保存）")
    @PostMapping("saveCouponRule")
    public Result saveCouponRule(@RequestBody CouponRuleVo couponRuleVo){
        couponInfoService.saveCouponRule(couponRuleVo);
        return Result.ok();
    }

    @ApiOperation("获取优惠券规则信息")
    @GetMapping("findCouponRuleList/{id}")
    public Result findCouponRuleList(@PathVariable Long id){
        Map<String, Object> couponInfoRangeList = couponInfoService.findCouponRuleList(id);
        return Result.ok(couponInfoRangeList);
    }

    @ApiOperation("根据关键字获取优惠券列表，活动使用")
    @GetMapping("findCouponByKeyword/{keyword}")
    public Result findCouponByKeyword(@PathVariable String keyword){
        return Result.ok(couponInfoService.findCouponByKeyword(keyword));
    }
}
