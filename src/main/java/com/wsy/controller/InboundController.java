package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.Inbound;
import com.wsy.entity.Inventory;
import com.wsy.entity.OperationLog;
import com.wsy.entity.User;
import com.wsy.mapper.InboundMapper;
import com.wsy.mapper.InventoryMapper;
import com.wsy.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/inbound")
public class InboundController {

    @Autowired
    private InboundMapper inboundMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/list")
    public Result<?> list(HttpServletRequest request) {
        authSupport.requireLogin(request);
        QueryWrapper<Inbound> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("inbound_date").orderByDesc("id");
        List<Inbound> list = inboundMapper.selectList(wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalInboundAmount = BigDecimal.ZERO;

        for (Inbound inbound : list) {
            Inventory inventory = inventoryMapper.selectById(inbound.getInventoryId());

            String productName = inventory != null ? inventory.getProductName() : inbound.getProductName();
            BigDecimal price = inventory != null ? safe(inventory.getPrice()) : safe(inbound.getPrice());
            String specification = inventory != null ? inventory.getSpecification() : inbound.getSpecification();
            String supplier = inventory != null ? inventory.getSupplier() : inbound.getSupplier();
            String unit = inventory != null ? inventory.getUnit() : inbound.getUnit();
            int initStockQty = inventory != null ? defaultInt(inventory.getInitStockQty()) : defaultInt(inbound.getInitStockQty());

            if (productName == null || productName.isBlank()) {
                continue;
            }
            totalInboundAmount = totalInboundAmount.add(safe(inbound.getInboundAmount()));

            Map<String, Object> row = new HashMap<>();
            row.put("id", inbound.getId());
            row.put("inventoryId", inbound.getInventoryId());
            row.put("inboundDate", inbound.getInboundDate());
            row.put("productName", productName);
            row.put("price", price);
            row.put("specification", specification);
            row.put("supplier", supplier);
            row.put("unit", unit);
            row.put("initStockQty", initStockQty);
            row.put("certNo", inbound.getCertNo());
            row.put("inboundQty", defaultInt(inbound.getInboundQty()));
            row.put("inboundAmount", safe(inbound.getInboundAmount()));
            row.put("productionDate", inbound.getProductionDate());
            row.put("validDate", inbound.getValidDate());
            row.put("operator", inbound.getOperator());
            row.put("createTime", inbound.getCreateTime());
            row.put("remark", inbound.getRemark());
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", rows);
        result.put("totalInboundAmount", totalInboundAmount.setScale(2, RoundingMode.HALF_UP));
        return Result.success(result);
    }

    @PostMapping("/add")
    public Result<?> add(HttpServletRequest request, @RequestBody Inbound inbound) {
        User user = authSupport.requireRole(request, 1);

        if (inbound.getInventoryId() == null || inbound.getInboundQty() == null || inbound.getInboundQty() <= 0 || inbound.getInboundDate() == null) {
            return Result.error("请填写完整的入库信息");
        }

        Inventory inventory = inventoryMapper.selectById(inbound.getInventoryId());
        if (inventory == null) {
            return Result.error("关联库存物品不存在");
        }

        BigDecimal inboundAmount = safe(inventory.getPrice()).multiply(BigDecimal.valueOf(inbound.getInboundQty())).setScale(2, RoundingMode.HALF_UP);
        inbound.setInboundAmount(inboundAmount);
        inbound.setOperator(user.getUsername());
        inbound.setProductName(inventory.getProductName());
        inbound.setPrice(safe(inventory.getPrice()));
        inbound.setSpecification(inventory.getSpecification());
        inbound.setSupplier(inventory.getSupplier());
        inbound.setUnit(inventory.getUnit());
        inbound.setInitStockQty(defaultInt(inventory.getInitStockQty()));
        inboundMapper.insert(inbound);

        int currentQty = defaultInt(inventory.getCurrentStockQty()) + inbound.getInboundQty();
        inventory.setCurrentStockQty(currentQty);
        inventoryMapper.updateById(inventory);

        addLog(user.getUsername(), "ADD", "inbound", "新增入库: " + inventory.getProductName() + " x" + inbound.getInboundQty(), inbound.getId());
        addLog(user.getUsername(), "UPDATE", "inventory", "入库导致库存变更: " + inventory.getProductName() + " +" + inbound.getInboundQty(), inventory.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", inbound.getId());
        result.put("message", "入库成功");
        return Result.success(result);
    }

    @PutMapping("/update")
    public Result<?> update(HttpServletRequest request, @RequestBody Inbound inbound) {
        User user = authSupport.requireRole(request, 1);

        if (inbound.getId() == null) {
            return Result.error("ID不能为空");
        }
        Inbound oldInbound = inboundMapper.selectById(inbound.getId());
        if (oldInbound == null) {
            return Result.error("入库记录不存在");
        }

        if (inbound.getInboundQty() == null || inbound.getInboundQty() <= 0 || inbound.getInboundDate() == null) {
            return Result.error("请填写完整的入库信息");
        }

        // 恢复旧的库存数量
        if (oldInbound.getInventoryId() != null) {
            Inventory oldInventory = inventoryMapper.selectById(oldInbound.getInventoryId());
            if (oldInventory != null) {
                int restoredQty = defaultInt(oldInventory.getCurrentStockQty()) - defaultInt(oldInbound.getInboundQty());
                oldInventory.setCurrentStockQty(restoredQty);
                inventoryMapper.updateById(oldInventory);
            }
        }

        // 更新新的库存数量
        if (inbound.getInventoryId() != null) {
            Inventory inventory = inventoryMapper.selectById(inbound.getInventoryId());
            if (inventory != null) {
                BigDecimal inboundAmount = safe(inventory.getPrice()).multiply(BigDecimal.valueOf(inbound.getInboundQty())).setScale(2, RoundingMode.HALF_UP);
                inbound.setInboundAmount(inboundAmount);
                inbound.setProductName(inventory.getProductName());
                inbound.setPrice(safe(inventory.getPrice()));
                inbound.setSpecification(inventory.getSpecification());
                inbound.setSupplier(inventory.getSupplier());
                inbound.setUnit(inventory.getUnit());
                inbound.setInitStockQty(defaultInt(inventory.getInitStockQty()));

                int currentQty = defaultInt(inventory.getCurrentStockQty()) + inbound.getInboundQty();
                inventory.setCurrentStockQty(currentQty);
                inventoryMapper.updateById(inventory);
                addLog(user.getUsername(), "UPDATE", "inventory", "入库更新导致库存变更: " + inventory.getProductName() + " -> " + currentQty, inventory.getId());
            }
        }

        inbound.setOperator(user.getUsername());
        inboundMapper.updateById(inbound);

        addLog(user.getUsername(), "UPDATE", "inbound", "更新入库: " + inbound.getProductName() + " x" + inbound.getInboundQty(), inbound.getId());

        return Result.success("入库记录更新成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);

        Inbound inbound = inboundMapper.selectById(id);
        if (inbound == null) {
            return Result.error("入库记录不存在");
        }

        // 恢复库存数量
        if (inbound.getInventoryId() != null) {
            Inventory inventory = inventoryMapper.selectById(inbound.getInventoryId());
            if (inventory != null) {
                int restoredQty = defaultInt(inventory.getCurrentStockQty()) - defaultInt(inbound.getInboundQty());
                inventory.setCurrentStockQty(restoredQty);
                inventoryMapper.updateById(inventory);
            }
        }

        addLog(user.getUsername(), "DELETE", "inbound", "删除入库: " + inbound.getProductName() + " x" + inbound.getInboundQty(), id);
        if (inbound.getInventoryId() != null) {
            addLog(user.getUsername(), "UPDATE", "inventory", "删除入库导致库存回滚: " + inbound.getProductName() + " -" + inbound.getInboundQty(), inbound.getInventoryId());
        }

        inboundMapper.deleteById(id);
        return Result.success("入库记录删除成功");
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
