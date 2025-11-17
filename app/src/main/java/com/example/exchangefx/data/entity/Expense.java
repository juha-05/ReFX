package com.example.exchangefx.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * expense 테이블 Entity
 * SQLite에 "expense" 테이블로 만들어짐.
 */
@Entity(tableName = "expense")
public class Expense {

    // id : PK, auto
    @PrimaryKey(autoGenerate = true)
    public long id;

    // 지출한 날짜·시간 (millis)
    @ColumnInfo(name = "date_millis")
    public long dateMillis;

    // Frankfurter용 날짜 문자열 "YYYY-MM-DD"
    @NonNull
    @ColumnInfo(name = "date_str")
    public String dateStr;

    // 지출 금액 (원래 통화 기준)
    @ColumnInfo(name = "amount")
    public double amount;

    // 통화 코드 ("USD", "EUR", "JPY", "KRW", ...)
    @NonNull
    @ColumnInfo(name = "currency")
    public String currency;

    // 카테고리 ("교통", "식비", "쇼핑", ...)
    @NonNull
    @ColumnInfo(name = "category")
    public String category;

    // 메모 (옵션)
    @ColumnInfo(name = "memo")
    public String memo;

    // row 생성 시각 (옵션, millis)
    @ColumnInfo(name = "created_at")
    public long createdAt;

    // row 수정 시각 (옵션, millis)
    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    // Room이 쓸 기본 생성자 (필수 X지만 습관적으로 둬도 됨)
    public Expense(@NonNull String dateStr,
                   double amount,
                   @NonNull String currency,
                   @NonNull String category) {
        this.dateStr = dateStr;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
    }
}
