package com.wsy.controller;

import com.wsy.common.Result;
import com.wsy.entity.BasicInfo;
import com.wsy.mapper.BasicInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/basicInfo")
public class BasicInfoController {

    @Autowired
    private BasicInfoMapper basicInfoMapper;

    // 获取所有基础信息并在结尾带出总金额
    @GetMapping("/list")
    public Result<?> list() {
        List<BasicInfo> list = basicInfoMapper.selectList(null);
        BigDecimal totalAmount = basicInfoMapper.selectTotalAmount();
        
        // 如果表为空，会返回空，我们需要给个默认值 0
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }

        // 把列表和总金额一起打包返回给前端
        Map<String, Object> returnData = new HashMap<>();
        returnData.put("list", list);
        returnData.put("totalAmount", totalAmount);
        
        return Result.success(returnData);
    }

    // 新增基础信息
    @PostMapping("/add")
    public Result<?> add(@RequestBody BasicInfo basicInfo) {
        // 防止前端传过来的空字符串日期导致数据库报错
        if ("".equals(basicInfo.getProdDate())) {
            basicInfo.setProdDate(null);
        }
        if ("".equals(basicInfo.getValidDate())) {
            basicInfo.setValidDate(null);
        }

        // 后端自动计算 金额 = 价格 * 期初库存，免除前端计算误差
        if (basicInfo.getPrice() != null && basicInfo.getInitStock() != null) {
            basicInfo.setAmount(basicInfo.getPrice().multiply(new BigDecimal(basicInfo.getInitStock())));
        } else {
            basicInfo.setAmount(BigDecimal.ZERO);
        }
        basicInfoMapper.insert(basicInfo);
        return Result.success("新增基础信息成功");
    }

    // 编辑基础信息
    @PutMapping("/update")
    public Result<?> update(@RequestBody BasicInfo basicInfo) {
        if (basicInfo.getId() == null) {
            return Result.error("ID不能为空");
        }
        // 防止前端传过来的空字符串日期导致数据库报错
        if ("".equals(basicInfo.getProdDate())) {
            basicInfo.setProdDate(null);
        }
        if ("".equals(basicInfo.getValidDate())) {
            basicInfo.setValidDate(null);
        }

        // 后端自动计算 金额 = 价格 * 期初库存
        if (basicInfo.getPrice() != null && basicInfo.getInitStock() != null) {
            basicInfo.setAmount(basicInfo.getPrice().multiply(new BigDecimal(basicInfo.getInitStock())));
        } else {
            basicInfo.setAmount(BigDecimal.ZERO);
        }
        basicInfoMapper.updateById(basicInfo);
        return Result.success("更新基础信息成功");
    }

    // 删除基础信息
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Integer id) {
        basicInfoMapper.deleteById(id);
        return Result.success("删除成功");
    }
}