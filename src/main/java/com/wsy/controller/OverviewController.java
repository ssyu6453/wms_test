package com.wsy.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wsy.common.AuthSupport;
import com.wsy.common.Result;
import com.wsy.entity.Inbound;
import com.wsy.entity.Inventory;
import com.wsy.entity.Outbound;
import com.wsy.mapper.InboundMapper;
import com.wsy.mapper.InventoryMapper;
import com.wsy.mapper.OutboundMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    @Autowired
    private AuthSupport authSupport;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InboundMapper inboundMapper;

    @Autowired
    private OutboundMapper outboundMapper;

    @GetMapping("/year")
    public Result<?> year(HttpServletRequest request, @RequestParam(value = "year", required = false) Integer year) {
        authSupport.requireLogin(request);
        int targetYear = year == null ? Calendar.getInstance().get(Calendar.YEAR) : year;

        List<Inventory> inventories = inventoryMapper.selectList(new QueryWrapper<Inventory>().orderByAsc("product_name"));
        List<Inbound> inbounds = inboundMapper.selectList(new QueryWrapper<Inbound>().orderByAsc("inbound_date"));
        List<Outbound> outbounds = outboundMapper.selectList(new QueryWrapper<Outbound>().orderByAsc("outbound_date"));

        Map<Integer, Map<Integer, BigDecimal>> inboundAmountMap = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> inboundQtyMap = new HashMap<>();
        Map<Integer, Map<Integer, BigDecimal>> outboundAmountMap = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> outboundQtyMap = new HashMap<>();

        for (Inbound inbound : inbounds) {
            if (inbound.getInboundDate() == null || getYear(inbound.getInboundDate()) != targetYear) {
                continue;
            }
            int month = getMonth(inbound.getInboundDate());
            mergeQty(inboundQtyMap, inbound.getInventoryId(), month, inbound.getInboundQty());
            mergeAmount(inboundAmountMap, inbound.getInventoryId(), month, inbound.getInboundAmount());
        }

        for (Outbound outbound : outbounds) {
            if (outbound.getOutboundDate() == null || getYear(outbound.getOutboundDate()) != targetYear) {
                continue;
            }
            int month = getMonth(outbound.getOutboundDate());
            mergeQty(outboundQtyMap, outbound.getInventoryId(), month, outbound.getOutboundQty());
            mergeAmount(outboundAmountMap, outbound.getInventoryId(), month, outbound.getOutboundAmount());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalInitAmount = BigDecimal.ZERO;
        List<Map<String, Object>> monthlyAmountSummary = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            Map<String, Object> monthTotal = new HashMap<>();
            monthTotal.put("month", m);
            monthTotal.put("inboundAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            monthTotal.put("outboundAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            monthTotal.put("balanceAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            monthlyAmountSummary.add(monthTotal);
        }

        for (Inventory inventory : inventories) {
            int runningQty = defaultInt(inventory.getInitStockQty());
            BigDecimal runningAmount = safe(inventory.getInitStockAmount());
            totalInitAmount = totalInitAmount.add(runningAmount);

            Map<String, Object> row = new HashMap<>();
            row.put("inventoryId", inventory.getId());
            row.put("productName", inventory.getProductName());
            row.put("initStockQty", runningQty);
            row.put("initStockAmount", runningAmount);

            for (int month = 1; month <= 12; month++) {
                int inQty = getQty(inboundQtyMap, inventory.getId(), month);
                BigDecimal inAmount = getAmount(inboundAmountMap, inventory.getId(), month);
                int outQty = getQty(outboundQtyMap, inventory.getId(), month);
                BigDecimal outAmount = getAmount(outboundAmountMap, inventory.getId(), month);

                runningQty = runningQty + inQty - outQty;
                runningAmount = runningAmount.add(inAmount).subtract(outAmount).setScale(2, RoundingMode.HALF_UP);

                row.put("m" + month + "InQty", inQty);
                row.put("m" + month + "OutQty", outQty);
                row.put("m" + month + "BalanceQty", runningQty);

                row.put("m" + month + "InAmount", inAmount);
                row.put("m" + month + "OutAmount", outAmount);
                row.put("m" + month + "BalanceAmount", runningAmount);

                Map<String, Object> monthTotal = monthlyAmountSummary.get(month - 1);
                monthTotal.put("inboundAmount", safe((BigDecimal) monthTotal.get("inboundAmount")).add(inAmount));
                monthTotal.put("outboundAmount", safe((BigDecimal) monthTotal.get("outboundAmount")).add(outAmount));
                monthTotal.put("balanceAmount", safe((BigDecimal) monthTotal.get("balanceAmount")).add(runningAmount));
            }
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("year", targetYear);
        result.put("list", rows);
        result.put("totalInitAmount", totalInitAmount.setScale(2, RoundingMode.HALF_UP));
        result.put("monthlyAmountSummary", monthlyAmountSummary);
        return Result.success(result);
    }

    private void mergeQty(Map<Integer, Map<Integer, Integer>> target, Integer inventoryId, int month, Integer qty) {
        target.computeIfAbsent(inventoryId, k -> new HashMap<>());
        Map<Integer, Integer> monthMap = target.get(inventoryId);
        monthMap.put(month, monthMap.getOrDefault(month, 0) + defaultInt(qty));
    }

    private void mergeAmount(Map<Integer, Map<Integer, BigDecimal>> target, Integer inventoryId, int month, BigDecimal amount) {
        target.computeIfAbsent(inventoryId, k -> new HashMap<>());
        Map<Integer, BigDecimal> monthMap = target.get(inventoryId);
        monthMap.put(month, safe(monthMap.getOrDefault(month, BigDecimal.ZERO)).add(safe(amount)));
    }

    private int getQty(Map<Integer, Map<Integer, Integer>> data, Integer inventoryId, int month) {
        if (!data.containsKey(inventoryId)) {
            return 0;
        }
        return data.get(inventoryId).getOrDefault(month, 0);
    }

    private BigDecimal getAmount(Map<Integer, Map<Integer, BigDecimal>> data, Integer inventoryId, int month) {
        if (!data.containsKey(inventoryId)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return safe(data.get(inventoryId).getOrDefault(month, BigDecimal.ZERO));
    }

    private int getYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    private int getMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.MONTH) + 1;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
