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
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
/* 
用户相关服务
用户数据使用经过AES256加密的JSON文件存储，只有用户登录之后才会解密并读取。用户数据存放在user里，按照用户名进行对应。
经过加密的数据会经过base64编码。

*/

/**
 * 用户注册和登录服务
 */
public class UserService {
    /** 存储路径 */
    private static final String USER_DIR = "data/users";
    /** 用户名规则 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");
    /** 口令规则 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("\\d{6}");
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    /** 迭代10000次 */
    private static final int PBKDF2_ITERATIONS = 10000;
    /** 随机数 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordEncoder passwordEncoder = createPasswordEncoder();
    private String currentUsername;
    private String currentPassword;

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
        /* 登录 */
        if (userExists(username)) {
            boolean loggedIn = login(scanner, username);
            if (loggedIn) {
                currentUsername = username;
            }
            return loggedIn;
        }
        /* 注册 */
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

    /**
     * 登录
     * @param scanner 用户输入
     * @param username 输入的用户名
     * @return
     */
    private boolean login(Scanner scanner, String username) {
        String userJson = loadUserJson(username);
        for (int i = 1; i <= 3; i++) {
            System.out.print("请输入密码：");
            String password = scanner.nextLine().trim();
            try {
                String passwordHash = loadPasswordHash(userJson, password);
                if (passwordEncoder.matches(password, passwordHash)) {
                    currentPassword = password;
                    System.out.println("登录成功");
                    return true;
                }
            } catch (IllegalStateException e) {
                // GCM 认证失败或旧文件密码校验失败，都按密码错误处理。
            }

            int remaining = 3 - i;
            if (remaining > 0) {
                System.out.println("密码错误，还剩 " + remaining + " 次机会");
            }
        }

        System.out.println("密码错误 3 次，程序退出");
        return false;
    }

    /**
     * 注册
     * @param scanner
     * @param username
     * @return
     */
    private boolean register(Scanner scanner, String username) {
        while (true) {
            System.out.print("用户不存在，请设置 6 位数字密码：");
            String password = scanner.nextLine().trim();
            if (!PASSWORD_PATTERN.matcher(password).matches()) {
                System.out.println("密码必须是 6 位数字");
                continue;
            }

            currentPassword = password;
            saveUser(username, passwordEncoder.encode(password), password);
            System.out.println("注册成功，已登录");
            return true;
        }
    }
    
    /**
     * 获取用户文件
     * @param username
     * @return 加密的字符串
     */
    private String loadUserJson(String username) {
        // 用bufferedReader按行读取
        try (BufferedReader reader = new BufferedReader(new FileReader(getUserFile(username)))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            return json.toString();
        } catch (IOException e) {
            throw new IllegalStateException("读取用户文件失败", e);
        }
    }
    /**
     * 
     * @param userJson
     * @param password
     * @return
     */
    private String loadPasswordHash(String userJson, String password) {
        if (isEncryptedUserData(userJson)) {
            String payload = decryptUserData(userJson, password);
            String encryptedPasswordHash = getJsonField(payload, "passwordHash");
            if (!encryptedPasswordHash.isEmpty()) {
                return encryptedPasswordHash;
            }
        }

        Matcher matcher = Pattern.compile("\"passwordHash\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(userJson);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        throw new IllegalStateException("用户密钥文件格式错误");
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    private void saveUser(String username, String passwordHash, String password) {
        File file = getUserFile(username);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("创建用户目录失败");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            String payload = "{\"passwordHash\":\"" + escapeJson(passwordHash)
                    + "\",\"createdAt\":\"" + escapeJson(LocalDateTime.now().toString())
                    + "\",\"transactions\":[]}";
            writer.println(encryptUserData(username, payload, password));
        } catch (IOException e) {
            throw new IllegalStateException("保存用户文件失败", e);
        }
    }
    /* 加密json，选择AES256-GCM，PBKDF2WithHmacSHA256，迭代次数为10000 */
    public static String encryptUserData(String username, String payloadJson, String password) {
        try {
            byte[] salt = new byte[16];
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(salt);
            SECURE_RANDOM.nextBytes(iv);

            SecretKeySpec key = deriveAesKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(payloadJson.getBytes("UTF-8"));
            // 写入加密后的内容包括具体的方式，salt，iv和密文
            // 使用base64编码，方便存储和传输
            return "{\n"
                    + "  \"username\":\"" + escapeJsonStatic(username) + "\",\n"
                    + "  \"encryption\":\"AES-256-GCM\",\n"
                    + "  \"kdf\":\"PBKDF2WithHmacSHA256\",\n"
                    + "  \"iterations\":" + PBKDF2_ITERATIONS + ",\n"
                    + "  \"salt\":\"" + Base64.getEncoder().encodeToString(salt) + "\",\n"
                    + "  \"iv\":\"" + Base64.getEncoder().encodeToString(iv) + "\",\n"
                    + "  \"cipherText\":\"" + Base64.getEncoder().encodeToString(cipherText) + "\"\n"
                    + "}";
        } catch (Exception e) {
            throw new IllegalStateException("用户数据加密失败", e);
        }
    }

    /* 解密json */
    public static String decryptUserData(String encryptedJson, String password) {
        try {
            int iterations = Integer.parseInt(getJsonField(encryptedJson, "iterations"));
            byte[] salt = Base64.getDecoder().decode(getJsonField(encryptedJson, "salt"));
            byte[] iv = Base64.getDecoder().decode(getJsonField(encryptedJson, "iv"));
            byte[] cipherText = Base64.getDecoder().decode(getJsonField(encryptedJson, "cipherText"));

            SecretKeySpec key = deriveAesKey(password, salt, iterations);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), "UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("用户数据解密失败，密码或文件内容不正确", e);
        }
    }

    public static boolean isEncryptedUserData(String json) {
        return json != null && json.contains("\"cipherText\"") && json.contains("\"AES-256-GCM\"");
    }

    public static String getJsonField(String json, String fieldName) {
        Pattern stringPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher stringMatcher = stringPattern.matcher(json);
        if (stringMatcher.find()) {
            return unescapeJsonStatic(stringMatcher.group(1));
        }

        Pattern numberPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)");
        Matcher numberMatcher = numberPattern.matcher(json);
        if (numberMatcher.find()) {
            return numberMatcher.group(1);
        }
        return "";
    }

    private static SecretKeySpec deriveAesKey(String password, byte[] salt) throws Exception {
        return deriveAesKey(password, salt, PBKDF2_ITERATIONS);
    }

    private static SecretKeySpec deriveAesKey(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, AES_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
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
        return escapeJsonStatic(value);
    }

    private static String escapeJsonStatic(String value) {
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
        return unescapeJsonStatic(value);
    }

    private static String unescapeJsonStatic(String value) {
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
