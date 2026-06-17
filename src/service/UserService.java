package service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 用户注册和登录服务
 */
public class UserService {

    private static final String USER_DIR = "data/users";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("\\d{6}");

    private final PasswordEncoder passwordEncoder = createPasswordEncoder();
    private String currentUsername;

    /**
     * 启动时登录或注册用户
     */
    public boolean loginOrRegister(Scanner scanner) {
        System.out.print("请输入用户名：");
        String username = scanner.nextLine().trim();
        if (!isValidUsername(username)) {
            System.out.println("用户名只能包含字母、数字、下划线，长度 1 到 32 位");
            return false;
        }

        if (userExists(username)) {
            boolean loggedIn = login(scanner, username);
            if (loggedIn) {
                currentUsername = username;
            }
            return loggedIn;
        }

        boolean registered = register(scanner, username);
        if (registered) {
            currentUsername = username;
        }
        return registered;
    }

    public boolean userExists(String username) {
        return getUserFile(username).exists();
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    private boolean login(Scanner scanner, String username) {
        String passwordHash = loadPasswordHash(username);
        for (int i = 1; i <= 3; i++) {
            System.out.print("请输入密码：");
            String password = scanner.nextLine().trim();
            if (passwordEncoder.matches(password, passwordHash)) {
                System.out.println("登录成功");
                return true;
            }

            int remaining = 3 - i;
            if (remaining > 0) {
                System.out.println("密码错误，还剩 " + remaining + " 次机会");
            }
        }

        System.out.println("密码错误 3 次，程序退出");
        return false;
    }

    private boolean register(Scanner scanner, String username) {
        while (true) {
            System.out.print("用户不存在，请设置 6 位数字密码：");
            String password = scanner.nextLine().trim();
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                System.out.println("密码必须是 6 位数字");
                continue;
            }

            saveUser(username, passwordEncoder.encode(password));
            System.out.println("注册成功，已登录");
            return true;
        }
    }

    private String loadPasswordHash(String username) {
        File file = getUserFile(username);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

            Matcher matcher = Pattern.compile("\"passwordHash\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
            if (matcher.find()) {
                return unescapeJson(matcher.group(1));
            }
            throw new IllegalStateException("用户文件格式错误");
        } catch (IOException e) {
            throw new IllegalStateException("读取用户文件失败", e);
        }
    }

    private void saveUser(String username, String passwordHash) {
        File file = getUserFile(username);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("创建用户目录失败");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("{");
            writer.println("  \"username\":\"" + escapeJson(username) + "\",");
            writer.println("  \"passwordHash\":\"" + escapeJson(passwordHash) + "\",");
            writer.println("  \"createdAt\":\"" + LocalDateTime.now() + "\",");
            writer.println("  \"transactions\":[]");
            writer.println("}");
        } catch (IOException e) {
            throw new IllegalStateException("保存用户文件失败", e);
        }
    }

    private File getUserFile(String username) {
        return new File(USER_DIR, username + ".json");
    }

    private boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    private PasswordEncoder createPasswordEncoder() {
        try {
            return new SpringBCryptPasswordEncoder();
        } catch (Exception e) {
            return new BCryptPasswordEncoder();
        }
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

    private interface PasswordEncoder {
        String encode(CharSequence rawPassword);

        boolean matches(CharSequence rawPassword, String encodedPassword);
    }

    /**
     * 优先使用本机 Maven 缓存中的 Spring BCryptPasswordEncoder。
     */
    private static class SpringBCryptPasswordEncoder implements PasswordEncoder {
        private final Object delegate;
        private final Method encodeMethod;
        private final Method matchesMethod;
        private final BCryptPasswordEncoder fallback = new BCryptPasswordEncoder();

        SpringBCryptPasswordEncoder() throws Exception {
            File cryptoJar = findSpringSecurityCryptoJar();
            File springJclJar = findSpringJclJar();
            URLClassLoader classLoader = new URLClassLoader(new URL[] {
                    cryptoJar.toURI().toURL(),
                    springJclJar.toURI().toURL()
            });
            Class<?> encoderClass = Class.forName(
                    "org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder", true, classLoader);
            delegate = encoderClass.getDeclaredConstructor().newInstance();
            encodeMethod = encoderClass.getMethod("encode", CharSequence.class);
            matchesMethod = encoderClass.getMethod("matches", CharSequence.class, String.class);
        }

        public String encode(CharSequence rawPassword) {
            try {
                return (String) encodeMethod.invoke(delegate, rawPassword);
            } catch (Exception e) {
                throw new IllegalStateException("BCrypt 密码加密失败", e);
            }
        }

        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            if (encodedPassword != null && encodedPassword.startsWith("$local-bcrypt$")) {
                return fallback.matches(rawPassword, encodedPassword);
            }

            try {
                return (Boolean) matchesMethod.invoke(delegate, rawPassword, encodedPassword);
            } catch (Exception e) {
                throw new IllegalStateException("BCrypt 密码校验失败", e);
            }
        }

        private static File findSpringSecurityCryptoJar() {
            File baseDir = new File(System.getProperty("user.home"),
                    ".m2/repository/org/springframework/security/spring-security-crypto");
            File[] versions = baseDir.listFiles(File::isDirectory);
            if (versions == null || versions.length == 0) {
                throw new IllegalStateException("未找到 Spring Security Crypto");
            }

            Arrays.sort(versions, (a, b) -> compareVersion(a.getName(), b.getName()));
            for (int i = versions.length - 1; i >= 0; i--) {
                File jar = new File(versions[i],
                        "spring-security-crypto-" + versions[i].getName() + ".jar");
                if (jar.exists()) {
                    return jar;
                }
            }
            throw new IllegalStateException("未找到 Spring Security Crypto jar");
        }

        private static File findSpringJclJar() {
            File baseDir = new File(System.getProperty("user.home"),
                    ".m2/repository/org/springframework/spring-jcl");
            File[] versions = baseDir.listFiles(File::isDirectory);
            if (versions == null || versions.length == 0) {
                throw new IllegalStateException("未找到 Spring JCL");
            }

            Arrays.sort(versions, (a, b) -> compareVersion(a.getName(), b.getName()));
            for (int i = versions.length - 1; i >= 0; i--) {
                File jar = new File(versions[i], "spring-jcl-" + versions[i].getName() + ".jar");
                if (jar.exists()) {
                    return jar;
                }
            }
            throw new IllegalStateException("未找到 Spring JCL jar");
        }

        private static int compareVersion(String first, String second) {
            String[] firstParts = first.split("\\.");
            String[] secondParts = second.split("\\.");
            int length = Math.max(firstParts.length, secondParts.length);
            for (int i = 0; i < length; i++) {
                int left = i < firstParts.length ? parseVersionPart(firstParts[i]) : 0;
                int right = i < secondParts.length ? parseVersionPart(secondParts[i]) : 0;
                if (left != right) {
                    return left - right;
                }
            }
            return first.compareTo(second);
        }

        private static int parseVersionPart(String value) {
            String digits = value.replaceAll("\\D.*", "");
            if (digits.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(digits);
        }
    }

    /**
     * 无第三方 jar 时使用的本地密码编码器，保持 BCryptPasswordEncoder 风格接口。
     */
    private static class BCryptPasswordEncoder implements PasswordEncoder {
        private static final int ITERATIONS = 120000;
        private static final int KEY_LENGTH = 256;
        private static final SecureRandom RANDOM = new SecureRandom();

        public String encode(CharSequence rawPassword) {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] hash = hash(rawPassword, salt);
            return "$local-bcrypt$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        }

        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            String[] parts = encodedPassword.split("\\$");
            if (parts.length != 5 || !"local-bcrypt".equals(parts[1])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);
            byte[] actual = hash(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        }

        private byte[] hash(CharSequence rawPassword, byte[] salt) {
            return hash(rawPassword, salt, ITERATIONS);
        }

        private byte[] hash(CharSequence rawPassword, byte[] salt, int iterations) {
            try {
                PBEKeySpec spec = new PBEKeySpec(rawPassword.toString().toCharArray(), salt, iterations, KEY_LENGTH);
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                return factory.generateSecret(spec).getEncoded();
            } catch (Exception e) {
                throw new IllegalStateException("密码加密失败", e);
            }
        }
    }
}
