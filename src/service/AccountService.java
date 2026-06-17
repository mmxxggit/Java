package service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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

    public AccountService(String username, String password) {
        storage = new JsonStorage(username, password);
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
            //用数组和map直接提取进行计算，不需要另外做运算
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
     * 从 CSV 导入记录，返回 {imported: 成功条数, skipped: 跳过条数}
     */
    public Map<String, Integer> importFromCSV(String filePath) {
        int imported = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                if (firstLine) {
                    firstLine = false;
                    if (!fields.isEmpty() && "id".equalsIgnoreCase(fields.get(0).trim())) {
                        continue;
                    }
                }

                if (fields.size() < 6) {
                    skipped++;
                    continue;
                }

                try {
                    addTransaction(fields.get(1), fields.get(2), fields.get(3), fields.get(4), fields.get(5));
                    imported++;
                } catch (IllegalArgumentException | IllegalStateException e) {
                    skipped++;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("导入 CSV 失败：" + e.getMessage(), e);
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        return result;
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
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            writer.println("id,type,amount,category,note,date");
            for (Transaction transaction : listTransactions(false)) {
                writer.printf(Locale.US, "%d,%s,%.2f,%s,%s,%s%n",
                        transaction.getId(),
                        escapeCsv(transaction.getType()),
                        transaction.getAmount(),
                        escapeCsv(transaction.getCatgory()),
                        escapeCsv(transaction.getNote()),
                        escapeCsv(transaction.getDate()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("导出 CSV 失败：" + e.getMessage(), e);
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (current == ',' && !inQuotes) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
