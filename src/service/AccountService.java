package service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import model.Transaction;
import storage.JsonStorage;

/**
 * 记账业务逻辑
 */
public class AccountService {

    private JsonStorage storage = new JsonStorage();

    public AccountService() {
    }

    public AccountService(String username) {
        storage = new JsonStorage(username);
    }

    /**
     * 添加交易记录
     */
    public Transaction addTransaction(String type, double amount, String category, String note, String date) {
        return addTransaction(type, BigDecimal.valueOf(amount), category, note, date);
    }

    /**
     * 添加交易记录
     */
    public Transaction addTransaction(String type, String amount, String category, String note, String date) {
        if (amount == null || !amount.trim().matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("金额必须是数字，且最多保留两位小数");
        }
        return addTransaction(type, new BigDecimal(amount.trim()), category, note, date);
    }

    private Transaction addTransaction(String type, BigDecimal amount, String category, String note, String date) {
        String normalizedType = normalizeType(type);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("金额最多保留两位小数");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("种类不能为空");
        }
        if (date == null || date.trim().isEmpty()) {
            date = LocalDate.now().toString();
        } else {
            date = date.trim();
            try {
                LocalDate.parse(date);
            } catch (Exception e) {
                throw new IllegalArgumentException("日期格式必须为 yyyy-MM-dd");
            }
        }
        Transaction t = new Transaction(0, normalizedType, amount.setScale(2).doubleValue(), category.trim(),
                note == null ? "" : note.trim(), date);
        storage.save(t,true);//FIX:修复了参数错误
        return t;
    }

    private String normalizeType(String type) {
        if (type == null) {
            throw new IllegalArgumentException("类型必须是 收入 或 支出");
        }

        String value = type.trim();
        if ("收入".equals(value) || "income".equalsIgnoreCase(value)) {
            return "收入";
        }
        if ("支出".equals(value) || "expense".equalsIgnoreCase(value)) {
            return "支出";
        }
        throw new IllegalArgumentException("类型必须是 收入 或 支出");
    }

    /**
     * 列出交易记录
     */
    public List<Transaction> listTransactions(boolean sortByAmountDesc) {
        List<Transaction> transactions = storage.findAll();
        if (sortByAmountDesc) {
            sortByAmountDesc(transactions);
        } else {
            sortByIdAsc(transactions);
        }
        return transactions;
    }

    private void sortByIdAsc(List<Transaction> transactions) {
        for (int i = 0; i < transactions.size() - 1; i++) {
            for (int j = 0; j < transactions.size() - 1 - i; j++) {
                if (transactions.get(j).getId() > transactions.get(j + 1).getId()) {
                    swap(transactions, j, j + 1);
                }
            }
        }
    }

    private void sortByAmountDesc(List<Transaction> transactions) {
        for (int i = 0; i < transactions.size() - 1; i++) {
            for (int j = 0; j < transactions.size() - 1 - i; j++) {
                Transaction current = transactions.get(j);
                Transaction next = transactions.get(j + 1);
                if (current.getAmount() < next.getAmount()
                        || (current.getAmount() == next.getAmount() && current.getId() > next.getId())) {
                    swap(transactions, j, j + 1);
                }
            }
        }
    }

    private void swap(List<Transaction> transactions, int first, int second) {
        Transaction temp = transactions.get(first);
        transactions.set(first, transactions.get(second));
        transactions.set(second, temp);
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
        String targetMonth = year + "-" + String.format("%02d", month);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (Transaction transaction : storage.findAll()) {
            if (!targetMonth.equals(getMonthKey(transaction.getDate()))) {
                continue;
            }

            BigDecimal amount = BigDecimal.valueOf(transaction.getAmount()).setScale(2);
            if (isIncome(transaction.getType())) {
                income = income.add(amount);
            } else if (isExpense(transaction.getType())) {
                expense = expense.add(amount);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("month", targetMonth);
        summary.put("income", income);
        summary.put("expense", expense);
        summary.put("net", income.subtract(expense));
        return summary;
    }

    /**
     * 获取所有月份的统计信息
     */
    public List<Map<String, Object>> getMonthlyStatistics() {
        Map<String, BigDecimal[]> monthlyTotals = new TreeMap<>();

        for (Transaction transaction : storage.findAll()) {
            String month = getMonthKey(transaction.getDate());
            if (month == null) {
                continue;
            }

            BigDecimal[] totals = monthlyTotals.computeIfAbsent(month,
                    key -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal amount = BigDecimal.valueOf(transaction.getAmount()).setScale(2);
            if (isIncome(transaction.getType())) {
                totals[0] = totals[0].add(amount);
            } else if (isExpense(transaction.getType())) {
                totals[1] = totals[1].add(amount);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : monthlyTotals.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("month", entry.getKey());
            item.put("income", entry.getValue()[0]);
            item.put("expense", entry.getValue()[1]);
            result.add(item);
        }
        return result;
    }

    private String getMonthKey(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}.*")) {
            return null;
        }
        return date.substring(0, 7);
    }

    private boolean isIncome(String type) {
        return "收入".equals(type) || "income".equalsIgnoreCase(type);
    }

    private boolean isExpense(String type) {
        return "支出".equals(type) || "expense".equalsIgnoreCase(type);
    }

    /**
     * 获取分类统计
     */
    public List<Map<String, Object>> getCategoryBreakdown(int year, int month) {
        List<Transaction> all = storage.findAll();
        Map<String, Integer> categoryTotals = new HashMap<>();
        int totalExpense = 0;

        for (Transaction t : all) {
            if (!isExpense(t.getType())) continue;
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
