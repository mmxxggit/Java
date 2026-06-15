package cli;

import service.AccountService;
import java.util.Scanner;

/**
 * 命令行入口
 */
public class Main {

    private static AccountService service = new AccountService();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 个人记账本 ===");
        System.out.println("输入 help 查看命令列表");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "help":
                    printHelp();
                    break;
                case "add":
                    // TODO: 解析参数并添加记录
                    System.out.println("功能待实现");
                    break;
                case "list":
                    // TODO: 列出记录
                    System.out.println("功能待实现");
                    break;
                case "delete":
                    // TODO: 删除记录
                    System.out.println("功能待实现");
                    break;
                case "stats":
                    // TODO: 统计信息
                    System.out.println("功能待实现");
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
        System.out.println("  list   - 查看记录");
        System.out.println("  delete - 删除记录");
        System.out.println("  stats  - 统计信息");
        System.out.println("  exit   - 退出");
    }
}
