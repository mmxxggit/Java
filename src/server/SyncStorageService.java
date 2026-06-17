package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * 服务端文件存储。
 */
@Service
public class SyncStorageService {

    private static final Path SERVER_DATA_DIR = Paths.get("server-data", "users");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");

    public Map<String, Object> upload(String username, String body) {
        validateUsername(username);
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("上传内容不能为空");
        }

        try {
            Files.createDirectories(SERVER_DATA_DIR);
            Path target = userFile(username);
            Files.write(target, body.getBytes(StandardCharsets.UTF_8));
            return userStatus(username);
        } catch (IOException e) {
            throw new IllegalStateException("上传失败：" + e.getMessage(), e);
        }
    }

    public String pull(String username) {
        validateUsername(username);
        Path file = userFile(username);
        if (!Files.exists(file)) {
            return null;
        }

        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("拉取失败：" + e.getMessage(), e);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> users = new ArrayList<>();

        try {
            Files.createDirectories(SERVER_DATA_DIR);
            try (var stream = Files.list(SERVER_DATA_DIR)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> users.add(userStatus(usernameFromFile(path))));
            }
        } catch (IOException e) {
            throw new IllegalStateException("查询状态失败：" + e.getMessage(), e);
        }

        result.put("userCount", users.size());
        result.put("users", users);
        result.put("serverTime", Instant.now().toString());
        return result;
    }

    public Map<String, Object> userStatus(String username) {
        validateUsername(username);
        Path file = userFile(username);
        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("exists", Files.exists(file));

        if (!Files.exists(file)) {
            result.put("recordCount", "unknown");
            return result;
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            boolean encrypted = content.contains("\"cipherText\"") && content.contains("\"AES-256-GCM\"");
            result.put("sizeBytes", Files.size(file));
            result.put("lastModified", Files.getLastModifiedTime(file).toInstant().toString());
            result.put("encrypted", encrypted);
            result.put("recordCount", encrypted ? "unknown" : countPlainTransactions(content));
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("查询用户状态失败：" + e.getMessage(), e);
        }
    }

    private int countPlainTransactions(String json) {
        String transactions = extractTransactionsArray(json);
        if (transactions == null) {
            return -1;
        }

        int count = 0;
        Matcher matcher = Pattern.compile("\\{[^}]*\"id\"\\s*:").matcher(transactions);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String extractTransactionsArray(String json) {
        Matcher matcher = Pattern.compile("\"transactions\"\\s*:").matcher(json);
        if (!matcher.find()) {
            return null;
        }

        int start = json.indexOf('[', matcher.end());
        if (start < 0) {
            return null;
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
        return null;
    }

    private Path userFile(String username) {
        return SERVER_DATA_DIR.resolve(username + ".json");
    }

    private String usernameFromFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".json".length());
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线，长度 1 到 32 位");
        }
    }
}
