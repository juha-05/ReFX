package com.example.exchangefx.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fx_rate_cache")
public class FxRateCache {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String fxDate;

    @NonNull
    public String baseCurrency;

    @NonNull
    public String targetCurrency;

    public double rate;

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
