package cli;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import model.Transaction;
import service.AccountService;
import service.UserService;

/**
 * 命令行入口
 */
public class Main {

    private static AccountService service;
    private static UserService userService = new UserService();

    /**
     * 程序主入口方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 创建Scanner对象用于读取用户输入
        Scanner scanner = new Scanner(System.in);
        try {
            if (!userService.loginOrRegister(scanner)) {
                return;
            }
        } catch (IllegalStateException e) {
            System.out.println("用户认证失败：" + e.getMessage());
            return;
        }

        service = new AccountService(userService.getCurrentUsername());
        // 打印程序标题
        System.out.println("=== 个人记账本 ===");
        // 提示用户输入help查看命令列表
        System.out.println("输入 help 查看命令列表");

        // 无限循环，持续接收用户输入
        while (true) {
            // 打印提示符
            System.out.print("> ");
            // 读取用户输入并去除首尾空格
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "help":
                    printHelp();
                    break;
                case "add":
                    addTransaction(scanner);
                    break;
                case "list":
                    // list子命令决定排序
                    String listOption = parts.length > 1 ? parts[1].trim() : "";
                    listTransactions(listOption);
                    break;
                case "delete":
                    // 按id删除
                    String deleteId = parts.length > 1 ? parts[1].trim() : "";
                    deleteTransaction(deleteId);
                    break;
                case "stats":
                    printStats();
                    break;
                case "quit":
                case "exit":
                    System.out.println("再见！");
                    return;
                default:
                    System.out.println("未知命令：" + command + "，输入 help 查看帮助");
            }
        }
    }

    private static void printHelp() {
        System.out.println("命令列表：");
        System.out.println("  add    - 添加记录");
        System.out.println("  list   - 查看记录，默认按 ID 排序");
        System.out.println("  list -amount - 按金额由大到小排序");
        System.out.println("  delete <id> - 删除指定 ID 的记录");
        System.out.println("  stats  - 按月统计收入和支出");
        System.out.println("  exit   - 退出");
    }

    
    private static void addTransaction(Scanner scanner) {
        try {
            System.out.print("类型（收入/支出）：");
            String type = scanner.nextLine().trim();

            System.out.print("金额（最多两位小数）：");
            String amount = scanner.nextLine().trim();

            System.out.print("种类：");
            String category = scanner.nextLine().trim();

            System.out.print("备注：");
            String note = scanner.nextLine().trim();

            System.out.print("日期（yyyy-MM-dd，留空为今天）：");
            String date = scanner.nextLine().trim();

            Transaction transaction = service.addTransaction(type, amount, category, note, date);
            System.out.println("添加成功，记录 ID：" + transaction.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("添加失败：" + e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println("添加失败：" + e.getMessage());
        }
    }

    private static void listTransactions(String option) {
        boolean sortByAmountDesc = false;
        if (!option.isEmpty()) {
            if ("-amount".equalsIgnoreCase(option)) {
                sortByAmountDesc = true;
            } else {
                System.out.println("未知 list 参数：" + option + "，可用参数：-amount");
                return;
            }
        }

        List<Transaction> transactions = service.listTransactions(sortByAmountDesc);
        if (transactions.isEmpty()) {
            System.out.println("暂无记录");
            return;
        }

        System.out.println("ID | 类型 | 金额 | 种类 | 备注 | 日期");
        for (Transaction transaction : transactions) {
            System.out.printf(Locale.US, "%d | %s | %.2f | %s | %s | %s%n",
                    transaction.getId(),
                    valueOrEmpty(transaction.getType()),
                    transaction.getAmount(),
                    valueOrEmpty(transaction.getCatgory()),
                    valueOrEmpty(transaction.getNote()),
                    valueOrEmpty(transaction.getDate()));
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void deleteTransaction(String idText) {
        if (idText.isEmpty()) {
            System.out.println("请输入要删除的记录 ID，例如：delete 2");
            return;
        }

        try {
            int id = Integer.parseInt(idText);
            if (id <= 0) {
                System.out.println("删除失败：ID 必须是正整数");
                return;
            }

            boolean deleted = service.deleteTransaction(id);
            if (deleted) {
                System.out.println("删除成功，记录 ID 已重新编号");
            } else {
                System.out.println("未找到 ID 为 " + id + " 的记录");
            }
        } catch (NumberFormatException e) {
            System.out.println("删除失败：ID 必须是正整数");
        } catch (IllegalStateException e) {
            System.out.println("删除失败：" + e.getMessage());
        }
    }

    private static void printStats() {
        List<Map<String, Object>> monthlyStats = service.getMonthlyStatistics();
        if (monthlyStats.isEmpty()) {
            System.out.println("暂无统计数据");
            return;
        }

        for (Map<String, Object> item : monthlyStats) {
            String month = (String) item.get("month");
            BigDecimal income = (BigDecimal) item.get("income");
            BigDecimal expense = (BigDecimal) item.get("expense");
            System.out.println(month + "：收入+" + formatAmount(income) + "，支出-" + formatAmount(expense));
        }
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }
}
