package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.Inbound;
import com.wsy.entity.Inventory;
import com.wsy.entity.OperationLog;
import com.wsy.entity.Outbound;
import com.wsy.entity.User;
import com.wsy.mapper.InboundMapper;
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
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InboundMapper inboundMapper;

    @Autowired
    private OutboundMapper outboundMapper;

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/list")
    public Result<?> list(HttpServletRequest request) {
        authSupport.requireLogin(request);
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("update_time");
        List<Inventory> inventories = inventoryMapper.selectList(wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalInventoryAmount = BigDecimal.ZERO;

        for (Inventory item : inventories) {
            int inboundQty = sumInboundQty(item.getId());
            BigDecimal inboundAmount = sumInboundAmount(item.getId());
            int outboundQty = sumOutboundQty(item.getId());
            BigDecimal outboundAmount = sumOutboundAmount(item.getId());
            BigDecimal stockAmount = safeMul(item.getPrice(), item.getCurrentStockQty());
            totalInventoryAmount = totalInventoryAmount.add(stockAmount);

            Map<String, Object> row = new HashMap<>();
            row.put("id", item.getId());
            row.put("productName", item.getProductName());
            row.put("price", safe(item.getPrice()));
            row.put("specification", item.getSpecification());
            row.put("supplier", item.getSupplier());
            row.put("unit", item.getUnit());
            row.put("initStockQty", defaultInt(item.getInitStockQty()));
            row.put("initStockAmount", safe(item.getInitStockAmount()));
            row.put("inboundQty", inboundQty);
            row.put("inboundAmount", inboundAmount);
            row.put("outboundQty", outboundQty);
            row.put("outboundAmount", outboundAmount);
            row.put("currentStockQty", defaultInt(item.getCurrentStockQty()));
            row.put("safeStockQty", defaultInt(item.getSafeStockQty()));
            row.put("stockAmount", stockAmount);
            row.put("remark", item.getRemark());
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", rows);
        result.put("totalInventoryAmount", totalInventoryAmount.setScale(2, RoundingMode.HALF_UP));
        return Result.success(result);
    }

    @GetMapping("/options")
    public Result<?> options(HttpServletRequest request) {
        authSupport.requireLogin(request);
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("product_name");
        List<Inventory> list = inventoryMapper.selectList(wrapper);
        List<Map<String, Object>> options = new ArrayList<>();
        for (Inventory item : list) {
            Map<String, Object> option = new HashMap<>();
            option.put("id", item.getId());
            option.put("productName", item.getProductName());
            option.put("price", safe(item.getPrice()));
            option.put("specification", item.getSpecification());
            option.put("supplier", item.getSupplier());
            option.put("unit", item.getUnit());
            option.put("initStockQty", defaultInt(item.getInitStockQty()));
            option.put("safeStockQty", defaultInt(item.getSafeStockQty()));
            option.put("currentStockQty", defaultInt(item.getCurrentStockQty()));
            options.add(option);
        }
        return Result.success(options);
    }

    @PostMapping("/save")
    public Result<?> save(HttpServletRequest request, @RequestBody Inventory inventory) {
        User user = authSupport.requireRole(request, 1);
        if (inventory.getProductName() == null || inventory.getProductName().isBlank()) {
            return Result.error("品名不能为空");
        }
        if (inventory.getPrice() == null || inventory.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("价格不能为空且不能为负数");
        }

        if (inventory.getInitStockQty() == null) {
            inventory.setInitStockQty(0);
        }
        if (inventory.getSafeStockQty() == null) {
            inventory.setSafeStockQty(5);
        }
        if (inventory.getCurrentStockQty() == null) {
            inventory.setCurrentStockQty(inventory.getInitStockQty());
        }
        if (inventory.getInitStockAmount() == null) {
            inventory.setInitStockAmount(safeMul(inventory.getPrice(), inventory.getInitStockQty()));
        }

        if (inventory.getId() == null) {
            inventoryMapper.insert(inventory);
            addLog(user.getUsername(), "ADD", "inventory", "新增库存物品: " + inventory.getProductName(), inventory.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("id", inventory.getId());
            result.put("message", "新增库存物品成功，操作人: " + user.getUsername());
            return Result.success(result);
        }

        Inventory old = inventoryMapper.selectById(inventory.getId());
        if (old == null) {
            return Result.error("库存物品不存在");
        }
        inventoryMapper.updateById(inventory);
        addLog(user.getUsername(), "UPDATE", "inventory", "更新库存物品: " + inventory.getProductName(), inventory.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("id", inventory.getId());
        result.put("message", "库存物品修改成功，操作人: " + user.getUsername());
        return Result.success(result);
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);
        Inventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) {
            return Result.error("库存物品不存在");
        }

        QueryWrapper<Inbound> inWrapper = new QueryWrapper<>();
        inWrapper.eq("inventory_id", id);
        Long inboundCount = inboundMapper.selectCount(inWrapper);

        QueryWrapper<Outbound> outWrapper = new QueryWrapper<>();
        outWrapper.eq("inventory_id", id);
        Long outboundCount = outboundMapper.selectCount(outWrapper);

        if ((inboundCount != null && inboundCount > 0) || (outboundCount != null && outboundCount > 0)) {
            return Result.error("该库存物品已产生出入库记录，无法删除");
        }

        inventoryMapper.deleteById(id);
        addLog(user.getUsername(), "DELETE", "inventory", "删除库存物品: " + inventory.getProductName(), id);
        return Result.success("库存物品删除成功");
    }

    @GetMapping("/warnings")
    public Result<?> warnings(HttpServletRequest request) {
        authSupport.requireLogin(request);
        List<Inventory> list = inventoryMapper.selectList(new QueryWrapper<Inventory>().orderByAsc("product_name"));

        List<Map<String, Object>> lessThanFive = new ArrayList<>();
        List<Map<String, Object>> equalsZero = new ArrayList<>();
        List<Map<String, Object>> lessThanZero = new ArrayList<>();

        for (Inventory item : list) {
            int qty = defaultInt(item.getCurrentStockQty());
            Map<String, Object> row = warningRow(item, qty);
            if (qty > 0 && qty < 5) {
                lessThanFive.add(row);
            }
            if (qty == 0) {
                equalsZero.add(row);
            }
            if (qty < 0) {
                lessThanZero.add(row);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("lessThanFive", lessThanFive);
        result.put("equalsZero", equalsZero);
        result.put("lessThanZero", lessThanZero);
        result.put("lessThanFiveCount", lessThanFive.size());
        result.put("equalsZeroCount", equalsZero.size());
        result.put("lessThanZeroCount", lessThanZero.size());
        return Result.success(result);
    }

    private Map<String, Object> warningRow(Inventory item, int qty) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", item.getId());
        row.put("productName", item.getProductName());
        row.put("specification", item.getSpecification());
        row.put("supplier", item.getSupplier());
        row.put("unit", item.getUnit());
        row.put("safeStockQty", defaultInt(item.getSafeStockQty()));
        row.put("currentStockQty", qty);
        return row;
    }

    private int sumInboundQty(Integer inventoryId) {
        QueryWrapper<Inbound> wrapper = new QueryWrapper<>();
        wrapper.eq("inventory_id", inventoryId).select("IFNULL(SUM(inbound_qty),0) AS inboundQty");
        List<Map<String, Object>> maps = inboundMapper.selectMaps(wrapper);
        return toInt(maps);
    }

    private BigDecimal sumInboundAmount(Integer inventoryId) {
        QueryWrapper<Inbound> wrapper = new QueryWrapper<>();
        wrapper.eq("inventory_id", inventoryId).select("IFNULL(SUM(inbound_amount),0) AS inboundAmount");
        List<Map<String, Object>> maps = inboundMapper.selectMaps(wrapper);
        return toDecimal(maps);
    }

    private int sumOutboundQty(Integer inventoryId) {
        QueryWrapper<Outbound> wrapper = new QueryWrapper<>();
        wrapper.eq("inventory_id", inventoryId).select("IFNULL(SUM(outbound_qty),0) AS outboundQty");
        List<Map<String, Object>> maps = outboundMapper.selectMaps(wrapper);
        return toInt(maps);
    }

    private BigDecimal sumOutboundAmount(Integer inventoryId) {
        QueryWrapper<Outbound> wrapper = new QueryWrapper<>();
        wrapper.eq("inventory_id", inventoryId).select("IFNULL(SUM(outbound_amount),0) AS outboundAmount");
        List<Map<String, Object>> maps = outboundMapper.selectMaps(wrapper);
        return toDecimal(maps);
    }

    private int toInt(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return 0;
        }
        Object value = maps.get(0).values().stream().findFirst().orElse(0);
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal toDecimal(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Object value = maps.get(0).values().stream().findFirst().orElse(BigDecimal.ZERO);
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal safeMul(BigDecimal price, Integer qty) {
        BigDecimal safePrice = safe(price);
        int safeQty = defaultInt(qty);
        return safePrice.multiply(BigDecimal.valueOf(safeQty)).setScale(2, RoundingMode.HALF_UP);
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
