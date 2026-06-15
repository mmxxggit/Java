package storage;

import model.Transaction;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON 文件存储实现
 */
public class JsonStorage {

    private static final String DATA_FILE = "data/transactions.json";
    private List<Transaction> cache = new ArrayList<>();
    private int nextId = 1;

    public JsonStorage() {
        loadFromFile();
    }

    /**
     * 保存一条交易记录
     */
    public void save(Transaction transaction, boolean flush) {
        transaction.setId(nextId++);
        cache.add(transaction);
        try {
            saveToFile();
        } catch (Exception e) {
            // 暂时忽略
        }
    }

    /**
     * 查询所有记录
     */
    public List<Transaction> findAll() {
        return new ArrayList<>(cache);
    }

    /**
     * 按 ID 查询
     */
    public Transaction findById(int id) {
        for (Transaction t : cache) {
            if (t.getId() == id) return t;
        }
        return null;
    }

    /**
     * 按日期范围查询
     */
    public List<Transaction> findByDateRange(String startDate, String endDate) {
        return cache.stream()
                .filter(t -> t.getDate().compareTo(startDate) >= 0 && t.getDate().compareTo(endDate) <= 0)
                .collect(Collectors.toList());
    }

    /**
     * 按分类查询
     */
    public List<Transaction> findByCategory(String category) {
        return cache.stream()
                .filter(t -> t.getCatgory().equals(category))
                .collect(Collectors.toList());
    }

    /**
     * 删除记录
     */
    public boolean delete(int id) {
        return cache.removeIf(t -> t.getId() == id);
    }

    /**
     * 更新记录
     */
    public boolean update(int id, Transaction updated) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getId() == id) {
                updated.setId(id);
                cache.set(i, updated);
                saveToFile();
                return true;
            }
        }
        return false;
    }

    private void loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) return;
        // TODO: 从 JSON 文件加载数据
        // 提示：可以使用简单的文本解析，或引入 Gson/Jackson
    }

    private void saveToFile() {
        File file = new File(DATA_FILE);
        file.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("[");
            for (int i = 0; i < cache.size(); i++) {
                Transaction t = cache.get(i);
                writer.printf("  {\"id\":%d, \"type\":\"%s\", \"amount\":%.2f, \"category\":\"%s\", \"note\":\"%s\", \"date\":\"%s\"}",
                        t.getId(), t.getType(), t.getAmount(), t.getCatgory(), t.getNote(), t.getDate());
                if (i < cache.size() - 1) writer.println(",");
                else writer.println();
            }
            writer.println("]");
        } catch (IOException e) {
            // 文件写入失败
        }
    }
}
