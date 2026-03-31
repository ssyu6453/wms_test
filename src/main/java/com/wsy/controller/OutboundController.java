package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.Inventory;
import com.wsy.entity.OperationLog;
import com.wsy.entity.Outbound;
import com.wsy.entity.User;
import com.wsy.mapper.InventoryMapper;
import com.wsy.mapper.OperationLogMapper;
import com.wsy.mapper.OutboundMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    @Autowired
    private OutboundMapper outboundMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/list")
    public Result<?> list(HttpServletRequest request) {
        authSupport.requireLogin(request);
        QueryWrapper<Outbound> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("outbound_date").orderByDesc("id");
        List<Outbound> list = outboundMapper.selectList(wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalOutboundAmount = BigDecimal.ZERO;

        for (Outbound outbound : list) {
            Inventory inventory = inventoryMapper.selectById(outbound.getInventoryId());

            String productName = inventory != null ? inventory.getProductName() : outbound.getProductName();
            BigDecimal price = inventory != null ? safe(inventory.getPrice()) : safe(outbound.getPrice());
            String specification = inventory != null ? inventory.getSpecification() : outbound.getSpecification();
            String supplier = inventory != null ? inventory.getSupplier() : outbound.getSupplier();
            String unit = inventory != null ? inventory.getUnit() : outbound.getUnit();

            if (productName == null || productName.isBlank()) {
                continue;
            }
            totalOutboundAmount = totalOutboundAmount.add(safe(outbound.getOutboundAmount()));

            Map<String, Object> row = new HashMap<>();
            row.put("id", outbound.getId());
            row.put("outboundDate", outbound.getOutboundDate());
            row.put("productName", productName);
            row.put("price", price);
            row.put("specification", specification);
            row.put("supplier", supplier);
            row.put("unit", unit);
            row.put("outboundQty", defaultInt(outbound.getOutboundQty()));
            row.put("outboundAmount", safe(outbound.getOutboundAmount()));
            row.put("purpose", outbound.getPurpose());
            row.put("remark", outbound.getRemark());
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", rows);
        result.put("totalOutboundAmount", totalOutboundAmount.setScale(2, RoundingMode.HALF_UP));
        return Result.success(result);
    }

    @PostMapping("/add")
    public Result<?> add(HttpServletRequest request, @RequestBody Outbound outbound) {
        User user = authSupport.requireRole(request, 1);

        if (outbound.getInventoryId() == null || outbound.getOutboundQty() == null || outbound.getOutboundQty() <= 0 || outbound.getOutboundDate() == null) {
            return Result.error("请填写完整的出库信息");
        }

        Inventory inventory = inventoryMapper.selectById(outbound.getInventoryId());
        if (inventory == null) {
            return Result.error("关联库存物品不存在");
        }

        BigDecimal outboundAmount = safe(inventory.getPrice()).multiply(BigDecimal.valueOf(outbound.getOutboundQty())).setScale(2, RoundingMode.HALF_UP);
        outbound.setOutboundAmount(outboundAmount);
        outbound.setOperator(user.getUsername());
        outbound.setProductName(inventory.getProductName());
        outbound.setPrice(safe(inventory.getPrice()));
        outbound.setSpecification(inventory.getSpecification());
        outbound.setSupplier(inventory.getSupplier());
        outbound.setUnit(inventory.getUnit());
        outboundMapper.insert(outbound);

        int currentQty = defaultInt(inventory.getCurrentStockQty()) - outbound.getOutboundQty();
        inventory.setCurrentStockQty(currentQty);
        inventoryMapper.updateById(inventory);

        addLog(user.getUsername(), "ADD", "outbound", "新增出库: " + inventory.getProductName() + " x" + outbound.getOutboundQty(), outbound.getId());

        return Result.success("出库成功");
    }

    @PutMapping("/update")
    public Result<?> update(HttpServletRequest request, @RequestBody Outbound outbound) {
        User user = authSupport.requireRole(request, 1);

        if (outbound.getId() == null) {
            return Result.error("ID不能为空");
        }
        Outbound oldOutbound = outboundMapper.selectById(outbound.getId());
        if (oldOutbound == null) {
            return Result.error("出库记录不存在");
        }

        if (outbound.getOutboundQty() == null || outbound.getOutboundQty() <= 0 || outbound.getOutboundDate() == null) {
            return Result.error("请填写完整的出库信息");
        }

        // 恢复旧的库存数量
        if (oldOutbound.getInventoryId() != null) {
            Inventory oldInventory = inventoryMapper.selectById(oldOutbound.getInventoryId());
            if (oldInventory != null) {
                int restoredQty = defaultInt(oldInventory.getCurrentStockQty()) + defaultInt(oldOutbound.getOutboundQty());
                oldInventory.setCurrentStockQty(restoredQty);
                inventoryMapper.updateById(oldInventory);
            }
        }

        // 更新新的库存数量
        if (outbound.getInventoryId() != null) {
            Inventory inventory = inventoryMapper.selectById(outbound.getInventoryId());
            if (inventory != null) {
                BigDecimal outboundAmount = safe(inventory.getPrice()).multiply(BigDecimal.valueOf(outbound.getOutboundQty())).setScale(2, RoundingMode.HALF_UP);
                outbound.setOutboundAmount(outboundAmount);
                outbound.setProductName(inventory.getProductName());
                outbound.setPrice(safe(inventory.getPrice()));
                outbound.setSpecification(inventory.getSpecification());
                outbound.setSupplier(inventory.getSupplier());
                outbound.setUnit(inventory.getUnit());

                int currentQty = defaultInt(inventory.getCurrentStockQty()) - outbound.getOutboundQty();
                inventory.setCurrentStockQty(currentQty);
                inventoryMapper.updateById(inventory);
            }
        }

        outbound.setOperator(user.getUsername());
        outboundMapper.updateById(outbound);

        addLog(user.getUsername(), "UPDATE", "outbound", "更新出库: " + outbound.getProductName() + " x" + outbound.getOutboundQty(), outbound.getId());

        return Result.success("出库记录更新成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);

        Outbound outbound = outboundMapper.selectById(id);
        if (outbound == null) {
            return Result.error("出库记录不存在");
        }

        // 恢复库存数量
        if (outbound.getInventoryId() != null) {
            Inventory inventory = inventoryMapper.selectById(outbound.getInventoryId());
            if (inventory != null) {
                int restoredQty = defaultInt(inventory.getCurrentStockQty()) + defaultInt(outbound.getOutboundQty());
                inventory.setCurrentStockQty(restoredQty);
                inventoryMapper.updateById(inventory);
            }
        }

        addLog(user.getUsername(), "DELETE", "outbound", "删除出库: " + outbound.getProductName() + " x" + outbound.getOutboundQty(), id);

        outboundMapper.deleteById(id);
        return Result.success("出库记录删除成功");
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void addLog(String operator, String opType, String module, String description, Integer targetId) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOpType(opType);
        log.setModule(module);
        log.setDescription(description);
        log.setTargetId(targetId);
        log.setCreateTime(new Date());
        logMapper.insert(log);
    }
}
