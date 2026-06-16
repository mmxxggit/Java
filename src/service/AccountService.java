package service;

import java.time.LocalDate;
import java.util.*;
import model.Transaction;
import storage.JsonStorage;

/**
 * 记账业务逻辑
 */
public class AccountService {

    private JsonStorage storage = new JsonStorage();

    /**
     * 添加交易记录
     */
    public Transaction addTransaction(String type, double amount, String category, String note, String date) {
        if (date == null || date.isEmpty()) {
            date = LocalDate.now().toString();
        }
        Transaction t = new Transaction(0, type, amount, category, note, date);
        storage.save(t，true);//FIX:修复了参数错误
        return t;
    }

    /**
     * 删除交易记录
     */
    public boolean deleteTransaction(int id) {
        return storage.delete(id);
    }

    /**
     * 获取月度统计
     */
    public Map<String, Object> getMonthlySummary(int year, int month) {
        // TODO: 实现月度统计
        // 返回 { "income": 总收入, "expense": 总支出, "net": 净收入 }
        return null;
    }

    /**
     * 获取分类统计
     */
    public List<Map<String, Object>> getCategoryBreakdown(int year, int month) {
        List<Transaction> all = storage.findAll();
        Map<String, Integer> categoryTotals = new HashMap<>();
        int totalExpense = 0;

        for (Transaction t : all) {
            if (!"expense".equals(t.getType())) continue;
            // 简单按月筛选
            if (t.getDate() != null && t.getDate().startsWith(year + "-" + String.format("%02d", month))) {
                int amountCents = (int) (t.getAmount() * 100);
                categoryTotals.merge(t.getCatgory(), amountCents, Integer::sum);
                totalExpense += amountCents;
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryTotals.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("category", entry.getKey());
            item.put("amount", entry.getValue());
            item.put("percentage", entry.getValue() / totalExpense * 100);
            result.add(item);
        }
        return result;
    }

    /**
     * 导出 CSV
     */
    public void exportToCSV(String filePath) {
        // TODO: 实现 CSV 导出
    }
}
