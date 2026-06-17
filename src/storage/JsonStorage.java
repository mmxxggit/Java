package storage;

import model.Transaction;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JSON 文件存储实现
 */
public class JsonStorage {

    private static final String DATA_FILE = "data/transactions.json";
    private static final String USER_DIR = "data/users";

    private final File dataFile;
    private final boolean userBookFile;
    private List<Transaction> cache = new ArrayList<>();
    private int nextId = 1;
    private String username;
    private String passwordHash;
    private String createdAt;

    public JsonStorage() {
        this(new File(DATA_FILE), false);
    }

    public JsonStorage(String username) {
        this(new File(USER_DIR, username + ".json"), true);
        this.username = username;
    }

    private JsonStorage(File dataFile, boolean userBookFile) {
        this.dataFile = dataFile;
        this.userBookFile = userBookFile;
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
        } catch (RuntimeException e) {
            cache.remove(transaction);
            nextId--;
            throw e;
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
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getId() == id) {
                List<Transaction> snapshot = copyCache();
                int originalNextId = nextId;
                try {
                    cache.remove(i);
                    resetIds();
                    saveToFile();
                    return true;
                } catch (RuntimeException e) {
                    cache = snapshot;
                    nextId = originalNextId;
                    throw e;
                }
            }
        }
        return false;
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
        if (!dataFile.exists()) return;
        try {
            String json = readFile(dataFile);
            String transactionJson = json;
            if (userBookFile) {
                username = getStringField(json, "username");
                passwordHash = getStringField(json, "passwordHash");
                createdAt = getStringField(json, "createdAt");
                transactionJson = getTransactionsArray(json);
            }

            loadTransactions(transactionJson);
        } catch (Exception e) {
            throw new IllegalStateException("读取 JSON 文件失败", e);
        }
    }

    private void loadTransactions(String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }

        Pattern objectPattern = Pattern.compile("\\{([^}]*)\\}");
        Matcher objectMatcher = objectPattern.matcher(json);
        int maxId = 0;
        while (objectMatcher.find()) {
            Map<String, String> values = parseObjectFields(objectMatcher.group(1));
            if (!values.containsKey("id")) {
                continue;
            }

            int id = Integer.parseInt(values.getOrDefault("id", "0"));
            double amount = Double.parseDouble(values.getOrDefault("amount", "0"));
            Transaction transaction = new Transaction(id, values.get("type"), amount,
                    values.get("category"), values.get("note"), values.get("date"));
            cache.add(transaction);
            maxId = Math.max(maxId, id);
        }
        nextId = maxId + 1;
    }

    private void saveToFile() {
        File parent = dataFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(dataFile))) {
            if (userBookFile) {
                saveUserBook(writer);
            } else {
                saveTransactionsArray(writer, "");
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入 JSON 文件失败", e);
        }
    }

    private void saveUserBook(PrintWriter writer) {
        writer.println("{");
        writer.println("  \"username\":\"" + escapeJson(username) + "\",");
        writer.println("  \"passwordHash\":\"" + escapeJson(passwordHash) + "\",");
        writer.println("  \"createdAt\":\"" + escapeJson(createdAt) + "\",");
        writer.println("  \"transactions\":");
        saveTransactionsArray(writer, "  ");
        writer.println("}");
    }

    private void saveTransactionsArray(PrintWriter writer, String indent) {
        writer.println(indent + "[");
        for (int i = 0; i < cache.size(); i++) {
            Transaction t = cache.get(i);
            writer.printf(Locale.US,
                    indent + "  {\"id\":%d, \"type\":\"%s\", \"amount\":%.2f, \"category\":\"%s\", \"note\":\"%s\", \"date\":\"%s\"}",
                    t.getId(), escapeJson(t.getType()), t.getAmount(), escapeJson(t.getCatgory()),
                    escapeJson(t.getNote()), escapeJson(t.getDate()));
            if (i < cache.size() - 1) writer.println(",");
            else writer.println();
        }
        writer.println(indent + "]");
    }

    private Map<String, String> parseObjectFields(String objectBody) {
        Map<String, String> values = new HashMap<>();
        Pattern fieldPattern = Pattern.compile("\"(id|type|amount|category|note|date)\"\\s*:\\s*(\"(?:\\\\.|[^\"])*\"|-?\\d+(?:\\.\\d+)?)");
        Matcher fieldMatcher = fieldPattern.matcher(objectBody);
        while (fieldMatcher.find()) {
            String value = fieldMatcher.group(2);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = unescapeJson(value.substring(1, value.length() - 1));
            }
            values.put(fieldMatcher.group(1), value);
        }
        return values;
    }

    private String getStringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return "";
    }

    private String getTransactionsArray(String json) {
        Matcher matcher = Pattern.compile("\"transactions\"\\s*:").matcher(json);
        if (!matcher.find()) {
            return "";
        }

        int start = json.indexOf('[', matcher.end());
        if (start < 0) {
            return "";
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        throw new IllegalStateException("transactions 数组格式错误");
    }

    private String readFile(File file) throws IOException {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        return json.toString();
    }

    private void resetIds() {
        for (int i = 0; i < cache.size(); i++) {
            cache.get(i).setId(i + 1);
        }
        nextId = cache.size() + 1;
    }

    private List<Transaction> copyCache() {
        List<Transaction> copied = new ArrayList<>();
        for (Transaction transaction : cache) {
            Transaction copy = new Transaction(transaction.getId(), transaction.getType(), transaction.getAmount(),
                    transaction.getCatgory(), transaction.getNote(), transaction.getDate());
            copy.setCreatedAt(transaction.getCreatedAt());
            copied.add(copy);
        }
        return copied;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaping) {
                switch (current) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case 'r': result.append('\r'); break;
                    case 'n': result.append('\n'); break;
                    case 't': result.append('\t'); break;
                    default: result.append(current);
                }
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else {
                result.append(current);
            }
        }
        if (escaping) {
            result.append('\\');
        }
        return result.toString();
    }
}
