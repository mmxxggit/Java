package cli;

import service.AccountService;
import java.util.Scanner;

/**
 * 命令行入口
 */
public class Main {

    private static AccountService service = new AccountService();

    /**
     * 程序主入口方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 创建Scanner对象用于读取用户输入
        Scanner scanner = new Scanner(System.in);
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
