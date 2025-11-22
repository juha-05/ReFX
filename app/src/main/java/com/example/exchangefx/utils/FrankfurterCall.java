package com.example.exchangefx.utils;

import android.content.Context;

import com.example.exchangefx.data.db.AppDatabase2;
import com.example.exchangefx.data.dao.FxRateCacheDao;
import com.example.exchangefx.data.entity.FxRateCache;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Frankfurter API + DB 캐시 + 메모리 캐시
 */
public class FrankfurterCall {

    private static final String BASE_URL = "https://api.frankfurter.app/";
    private final OkHttpClient client = new OkHttpClient();

    // 메모리 캐시 (앱 실행 중만 유지)
    private final Map<String, Double> memoryCache = new ConcurrentHashMap<>();

    /**
     * DB + 메모리 캐시 기반 환율 조회
     */
    public double getRateWithCache(
            Context context,
            String fxDate,
            String base,
            String target
    ) throws IOException {

        if (base.equalsIgnoreCase(target)) return 1.0;

        // Frankfurter 형식 보정
        String date = (fxDate == null || fxDate.isEmpty()) ? "latest" : fxDate;

        String key = date + "|" + base + "|" + target;

        // -------------------------
        // 1) 메모리 캐시 확인
        // -------------------------
        if (memoryCache.containsKey(key)) {
            return memoryCache.get(key);
        }

        // -------------------------
        // 2) DB 캐시 확인
        // -------------------------
        FxRateCacheDao dao = AppDatabase2.getInstance(context).fxRateCacheDao();
        FxRateCache cached = dao.getCachedRate(date, base, target);

        if (cached != null) {
            memoryCache.put(key, cached.rate);
            return cached.rate;
        }

        // -------------------------
        // 3) API 호출
        // -------------------------
        double rate = fetchRateFromApi(date, base, target);

        // -------------------------
        // 4) DB 캐시에 저장
        // -------------------------
        FxRateCache fx = new FxRateCache(
                date,
                base,
                target,
                rate
        );
        dao.insertRate(fx);

        // -------------------------
        // 5) 메모리 캐시 저장
        // -------------------------
        memoryCache.put(key, rate);

        return rate;
    }

    /**
     * 실 API 호출
     */
    private double fetchRateFromApi(String date, String base, String target) throws IOException {

        String path = date.equals("latest") ? "latest" : date;

        String url = BASE_URL + path + "?from=" + base + "&to=" + target;

        Request request = new Request.Builder().url(url).build();

        try (Response resp = client.newCall(request).execute()) {

            if (!resp.isSuccessful()) {
                throw new IOException("HTTP Error: " + resp.code());
            }

            String json = resp.body().string();
            JSONObject root = new JSONObject(json);
            JSONObject rates = root.getJSONObject("rates");

            return rates.getDouble(target);
        } catch (Exception e) {
            throw new IOException("Frankfurter API parsing error: " + e.getMessage());
        }
    }
}
