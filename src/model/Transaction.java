package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 */
public class Transaction {

    private int id;
    private String type;        // "income" 或 "expense"
    private double amount;      // 金额（元）
    private String catgory;     // 分类
    private String note;        // 备注
    private String date;        // 日期，格式不固定
    private LocalDateTime createdAt;

    public Transaction() {
    }

    public Transaction(int id, String type, double amount, String category, String note, String date) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.catgory = category;
        this.note = note;
        this.date = date;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCatgory() { return catgory; }
    public void setCatgory(String catgory) { this.catgory = catgory; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
