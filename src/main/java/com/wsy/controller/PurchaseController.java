package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.Inbound;
import com.wsy.entity.Inventory;
import com.wsy.entity.OperationLog;
import com.wsy.entity.Purchase;
import com.wsy.entity.User;
import com.wsy.mapper.InboundMapper;
import com.wsy.mapper.InventoryMapper;
import com.wsy.mapper.OperationLogMapper;
import com.wsy.mapper.PurchaseMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    @Autowired
    private PurchaseMapper purchaseMapper;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InboundMapper inboundMapper;

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private AuthSupport authSupport;

    @GetMapping("/list")
    public Result<?> list(HttpServletRequest request) {
        authSupport.requireLogin(request);
        QueryWrapper<Purchase> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("purchase_date").orderByDesc("id");
        List<Purchase> list = purchaseMapper.selectList(wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalPurchaseAmount = BigDecimal.ZERO;
        for (Purchase purchase : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", purchase.getId());
            row.put("purchaseDate", purchase.getPurchaseDate());
            row.put("productName", purchase.getProductName());
            row.put("price", safe(purchase.getPrice()));
            row.put("specification", purchase.getSpecification());
            row.put("supplier", purchase.getSupplier());
            row.put("unit", purchase.getUnit());
            row.put("purchaseQty", defaultInt(purchase.getPurchaseQty()));
            row.put("purchaseAmount", safe(purchase.getPurchaseAmount()));
            row.put("status", purchase.getStatus());
            row.put("operator", purchase.getOperator());
            row.put("remark", purchase.getRemark());
            row.put("inventoryId", purchase.getInventoryId());
            row.put("receiveTime", purchase.getReceiveTime());
            row.put("createTime", purchase.getCreateTime());
            rows.add(row);
            totalPurchaseAmount = totalPurchaseAmount.add(safe(purchase.getPurchaseAmount()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", rows);
        result.put("totalPurchaseAmount", totalPurchaseAmount.setScale(2, RoundingMode.HALF_UP));
        return Result.success(result);
    }

    @PostMapping("/add")
    public Result<?> add(HttpServletRequest request, @RequestBody Purchase purchase) {
        User user = authSupport.requireRole(request, 1);
        if (purchase.getPurchaseDate() == null || purchase.getPurchaseQty() == null || purchase.getPurchaseQty() <= 0) {
            return Result.error("请填写完整采购信息");
        }
        if (purchase.getProductName() == null || purchase.getProductName().isBlank()) {
            return Result.error("品名不能为空");
        }
        if (purchase.getSupplier() == null || purchase.getSupplier().isBlank()) {
            return Result.error("供应商不能为空");
        }

        Inventory inventory = resolveInventory(purchase);
        if (inventory == null) {
            return Result.error("无法创建或匹配库存物品");
        }

        purchase.setInventoryId(inventory.getId());
        purchase.setProductName(inventory.getProductName());
        purchase.setPrice(safe(inventory.getPrice()));
        purchase.setSpecification(inventory.getSpecification());
        purchase.setSupplier(inventory.getSupplier());
        purchase.setUnit(inventory.getUnit());
        purchase.setPurchaseAmount(safe(inventory.getPrice()).multiply(BigDecimal.valueOf(defaultInt(purchase.getPurchaseQty()))).setScale(2, RoundingMode.HALF_UP));
        purchase.setStatus("PENDING");
        purchase.setOperator(user.getUsername());
        purchaseMapper.insert(purchase);

        addLog(user.getUsername(), "ADD", "purchase", "新增采购记录: " + purchase.getProductName() + " x" + purchase.getPurchaseQty(), purchase.getId());
        return Result.success("采购记录新增成功");
    }

    @PutMapping("/update")
    public Result<?> update(HttpServletRequest request, @RequestBody Purchase purchase) {
        User user = authSupport.requireRole(request, 1);
        if (purchase.getId() == null) {
            return Result.error("ID不能为空");
        }
        Purchase old = purchaseMapper.selectById(purchase.getId());
        if (old == null) {
            return Result.error("采购记录不存在");
        }
        if ("RECEIVED".equalsIgnoreCase(old.getStatus())) {
            return Result.error("已签收采购记录不允许修改");
        }

        Inventory inventory = resolveInventory(purchase);
        if (inventory == null) {
            return Result.error("无法创建或匹配库存物品");
        }

        purchase.setInventoryId(inventory.getId());
        purchase.setProductName(inventory.getProductName());
        purchase.setPrice(safe(inventory.getPrice()));
        purchase.setSpecification(inventory.getSpecification());
        purchase.setSupplier(inventory.getSupplier());
        purchase.setUnit(inventory.getUnit());
        purchase.setPurchaseAmount(safe(inventory.getPrice()).multiply(BigDecimal.valueOf(defaultInt(purchase.getPurchaseQty()))).setScale(2, RoundingMode.HALF_UP));
        purchase.setStatus(old.getStatus());
        purchase.setOperator(user.getUsername());
        purchaseMapper.updateById(purchase);

        addLog(user.getUsername(), "UPDATE", "purchase", "更新采购记录: " + purchase.getProductName() + " x" + purchase.getPurchaseQty(), purchase.getId());
        return Result.success("采购记录更新成功");
    }

    @PostMapping("/cancel/{id}")
    public Result<?> cancel(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);
        Purchase purchase = purchaseMapper.selectById(id);
        if (purchase == null) {
            return Result.error("采购记录不存在");
        }
        if ("RECEIVED".equalsIgnoreCase(purchase.getStatus())) {
            return Result.error("已签收采购记录不可取消");
        }

        purchase.setStatus("CANCELED");
        purchaseMapper.updateById(purchase);
        addLog(user.getUsername(), "UPDATE", "purchase", "取消采购记录: " + purchase.getProductName(), id);
        return Result.success("采购记录已取消");
    }

    @PostMapping("/receive/{id}")
    public Result<?> receive(HttpServletRequest request, @PathVariable Integer id) {
        User user = authSupport.requireRole(request, 1);
        Purchase purchase = purchaseMapper.selectById(id);
        if (purchase == null) {
            return Result.error("采购记录不存在");
        }
        if ("CANCELED".equalsIgnoreCase(purchase.getStatus())) {
            return Result.error("已取消采购记录不可签收");
        }
        if ("RECEIVED".equalsIgnoreCase(purchase.getStatus())) {
            return Result.error("该采购记录已签收");
        }

        Inventory inventory = inventoryMapper.selectById(purchase.getInventoryId());
        if (inventory == null) {
            return Result.error("关联库存物品不存在");
        }

        int newQty = defaultInt(inventory.getCurrentStockQty()) + defaultInt(purchase.getPurchaseQty());
        inventory.setCurrentStockQty(newQty);
        inventoryMapper.updateById(inventory);

        Inbound inbound = new Inbound();
        inbound.setInventoryId(inventory.getId());
        inbound.setInboundDate(new Date());
        inbound.setInboundQty(defaultInt(purchase.getPurchaseQty()));
        inbound.setInboundAmount(safe(inventory.getPrice()).multiply(BigDecimal.valueOf(defaultInt(purchase.getPurchaseQty()))).setScale(2, RoundingMode.HALF_UP));
        inbound.setProductName(inventory.getProductName());
        inbound.setPrice(safe(inventory.getPrice()));
        inbound.setSpecification(inventory.getSpecification());
        inbound.setSupplier(inventory.getSupplier());
        inbound.setUnit(inventory.getUnit());
        inbound.setInitStockQty(defaultInt(inventory.getInitStockQty()));
        inbound.setOperator(user.getUsername());
        inbound.setRemark("采购签收自动入库，采购单ID:" + purchase.getId());
        inboundMapper.insert(inbound);

        purchase.setStatus("RECEIVED");
        purchase.setReceiveTime(new Date());
        purchaseMapper.updateById(purchase);

        addLog(user.getUsername(), "UPDATE", "purchase", "采购签收并入库: " + purchase.getProductName() + " x" + purchase.getPurchaseQty(), purchase.getId());
        addLog(user.getUsername(), "ADD", "inbound", "采购签收自动生成入库记录", inbound.getId());
        return Result.success("签收成功，已同步入库");
    }

    private Inventory resolveInventory(Purchase purchase) {
        if (purchase.getInventoryId() != null) {
            Inventory byId = inventoryMapper.selectById(purchase.getInventoryId());
            if (byId != null) {
                return byId;
            }
        }

        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        wrapper.eq("product_name", purchase.getProductName());
        if (purchase.getSpecification() == null || purchase.getSpecification().isBlank()) {
            wrapper.and(w -> w.isNull("specification").or().eq("specification", ""));
        } else {
            wrapper.eq("specification", purchase.getSpecification());
        }
        Inventory existed = inventoryMapper.selectOne(wrapper);
        if (existed != null) {
            return existed;
        }

        Inventory inventory = new Inventory();
        inventory.setProductName(purchase.getProductName());
        inventory.setPrice(safe(purchase.getPrice()));
        inventory.setSpecification(purchase.getSpecification());
        inventory.setSupplier(purchase.getSupplier());
        inventory.setUnit(purchase.getUnit());
        inventory.setInitStockQty(0);
        inventory.setCurrentStockQty(0);
        inventory.setSafeStockQty(5);
        inventory.setInitStockAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        inventory.setRemark("由采购模块自动创建");
        inventoryMapper.insert(inventory);
        return inventory;
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
