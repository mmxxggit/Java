# 个人记账本 — Java 版

## 构建与运行

```bash
# 编译
javac -d out src/**/*.java

# 运行
java -cp out cli.Main
```

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
