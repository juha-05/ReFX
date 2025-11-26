package com.example.ReFX.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.ReFX.data.entity.FxRateCache;

@Dao
public interface FxRateCacheDao {

    // 캐시 저장 (같은 날짜 + 통화 조합이 이미 있으면 갱신)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRate(FxRateCache fx);

    // 특정 날짜 + 통화 조합 캐시 조회
    @Query("SELECT * FROM fx_rate_cache WHERE fxDate = :fxDate AND baseCurrency = :base AND targetCurrency = :target LIMIT 1")
    FxRateCache getCachedRate(String fxDate, String base, String target);

    // 🔥 전체 캐시 삭제 (Settings 에서 "캐시 삭제" 버튼 클릭 시 사용)
    @Query("DELETE FROM fx_rate_cache")
    void clearCache();
}
