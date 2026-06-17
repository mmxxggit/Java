# 个人记账本 — Java 版

## 构建与运行

```bash
# 编译
javac -d out src/**/*.java

# 运行
java -cp out cli.Main

# Spring Boot 服务端构建
mvn -DskipTests package

# 启动 HTTP 同步服务端
mvn spring-boot:run
```

## HTTP 同步服务端

服务端默认端口：`8080`

接口：

```bash
# 上传当前用户账本 JSON 到服务端
curl -X POST "http://localhost:8080/api/sync/upload?username=alice" \
  -H "Content-Type: application/json" \
  --data-binary @data/users/alice.json

# 从服务端拉取用户账本 JSON
curl "http://localhost:8080/api/sync/pull/alice"

# 查询服务端整体状态
curl "http://localhost:8080/api/sync/status"

# 查询单个用户同步状态
curl "http://localhost:8080/api/sync/status/alice"
```

服务端文件存储目录：`server-data/users/`

已实现 API：
- `POST /api/sync/upload?username=<username>`：上传本地账本 JSON 到服务端。
- `GET /api/sync/pull/{username}`：拉取服务端保存的账本 JSON。
- `GET /api/sync/status`：查询服务端保存的用户数量和各用户状态。
- `GET /api/sync/status/{username}`：查询指定用户状态。

## 项目结构

```
Java/
├── README.md
├── src/
│   ├── model/
│   │   └── Transaction.java
│   ├── storage/
│   │   └── JsonStorage.java
│   ├── service/
│   │   └── AccountService.java
│   └── cli/
│       └── Main.java
└── data/
```

## 设计思路

（请在此补充你的设计说明）
