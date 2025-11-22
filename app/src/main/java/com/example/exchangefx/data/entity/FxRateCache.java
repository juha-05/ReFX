package com.example.exchangefx.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fx_rate_cache")
public class FxRateCache {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // YYYY-MM-DD 또는 "latest"
    @NonNull
    public String fxDate;

    // 예: USD
    @NonNull
    public String baseCurrency;

    // 예: KRW
    @NonNull
    public String targetCurrency;

    // 예: 1 USD = 1444.00 KRW
    public double rate;

    // 생성자 추가 (캐시 저장 시 필수)
    public FxRateCache(@NonNull String fxDate,
                       @NonNull String baseCurrency,
                       @NonNull String targetCurrency,
                       double rate) {
        this.fxDate = fxDate;
        this.baseCurrency = baseCurrency;
        this.targetCurrency = targetCurrency;
        this.rate = rate;
    }
}
