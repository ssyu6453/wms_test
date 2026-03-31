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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/year")
    public Result<?> year(HttpServletRequest request, @RequestParam(value = "year", required = false) Integer year) {
        authSupport.requireLogin(request);
        int targetYear = year == null ? Calendar.getInstance().get(Calendar.YEAR) : year;

        List<Map<String, Object>> dbRows = jdbcTemplate.queryForList(
                "SELECT * FROM t_io_overview WHERE stat_year = ? ORDER BY product_name",
                targetYear
        );
        if (dbRows.isEmpty() && year == null) {
            List<Map<String, Object>> latestYearRows = jdbcTemplate.queryForList("SELECT MAX(stat_year) AS y FROM t_io_overview");
            if (!latestYearRows.isEmpty()) {
                Integer latestYear = intVal(getIgnoreCase(latestYearRows.get(0), "y"));
                if (latestYear != null && latestYear > 0) {
                    targetYear = latestYear;
                    dbRows = jdbcTemplate.queryForList(
                            "SELECT * FROM t_io_overview WHERE stat_year = ? ORDER BY product_name",
                            targetYear
                    );
                }
            }
        }
        if (!dbRows.isEmpty()) {
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

            for (Map<String, Object> dbRow : dbRows) {
                Map<String, Object> row = new HashMap<>();
                Object productNameObj = getIgnoreCase(dbRow, "product_name");
                row.put("inventoryId", intVal(getIgnoreCase(dbRow, "id")));
                row.put("productName", productNameObj == null ? "" : String.valueOf(productNameObj));
                row.put("initStockQty", intVal(getIgnoreCase(dbRow, "init_stock_qty")));
                BigDecimal initAmount = decimalVal(getIgnoreCase(dbRow, "init_stock_amount"));
                row.put("initStockAmount", initAmount);
                totalInitAmount = totalInitAmount.add(initAmount);

                for (int month = 1; month <= 12; month++) {
                    int inQty = intVal(getIgnoreCase(dbRow, "m" + month + "_in_qty"));
                    int outQty = intVal(getIgnoreCase(dbRow, "m" + month + "_out_qty"));
                    int balanceQty = intVal(getIgnoreCase(dbRow, "m" + month + "_balance_qty"));
                    BigDecimal inAmount = decimalVal(getIgnoreCase(dbRow, "m" + month + "_in_amount"));
                    BigDecimal outAmount = decimalVal(getIgnoreCase(dbRow, "m" + month + "_out_amount"));
                    BigDecimal balanceAmount = decimalVal(getIgnoreCase(dbRow, "m" + month + "_balance_amount"));

                    row.put("m" + month + "InQty", inQty);
                    row.put("m" + month + "OutQty", outQty);
                    row.put("m" + month + "BalanceQty", balanceQty);
                    row.put("m" + month + "InAmount", inAmount);
                    row.put("m" + month + "OutAmount", outAmount);
                    row.put("m" + month + "BalanceAmount", balanceAmount);

                    Map<String, Object> monthTotal = monthlyAmountSummary.get(month - 1);
                    monthTotal.put("inboundAmount", safe((BigDecimal) monthTotal.get("inboundAmount")).add(inAmount));
                    monthTotal.put("outboundAmount", safe((BigDecimal) monthTotal.get("outboundAmount")).add(outAmount));
                    monthTotal.put("balanceAmount", safe((BigDecimal) monthTotal.get("balanceAmount")).add(balanceAmount));
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

    private Object getIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int intVal(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal decimalVal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
