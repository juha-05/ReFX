package com.example.exchangefx.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Frankfurter API 에서 환율을 가져오는 간단한 클라이언트.
 * - OkHttp 사용
 * - 메모리 캐시(Map) 사용 (같은 날짜/통화 조합이면 다시 요청 안 함)
 */
public class FrankfurterCall {

    private static final String BASE_URL = "https://api.frankfurter.app/";
    private final OkHttpClient client = new OkHttpClient();

    // key: date|FROM|TO   e.g. "latest|USD|KRW"  값: 환율(double)
    private final Map<String, Double> inMemoryCache = new ConcurrentHashMap<>();

    /**
     * FROM 통화 → TO 통화 환율을 동기적으로 가져온다.
     * dateOrLatest: "latest" 또는 "YYYY-MM-DD"
     */
    public double getRateSync(String dateOrLatest, String from, String to)
            throws IOException, JSONException {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to 통화 코드는 null 이면 안 됩니다.");
        }

        if (from.equalsIgnoreCase(to)) {
            return 1.0;
        }

        String datePart = (dateOrLatest == null || dateOrLatest.isEmpty())
                ? "latest"
                : dateOrLatest;

        String key = datePart + "|" + from + "|" + to;

        // 1) 메모리 캐시에 있으면 그대로 사용
        Double cached = inMemoryCache.get(key);
        if (cached != null) {
            return cached;
        }

        // 2) Frankfurter 호출
        String path = "latest".equals(datePart) ? "latest" : datePart;
        String url = BASE_URL + path + "?from=" + from + "&to=" + to;

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Frankfurter HTTP error: " + resp.code());
            }

            String body = resp.body().string();
            JSONObject json = new JSONObject(body);
            JSONObject rates = json.getJSONObject("rates");
            double rate = rates.getDouble(to);

            inMemoryCache.put(key, rate);
            return rate;
        }
    }

    /**
     * FROM → KRW 환율 편의 함수
     */
    public double getRateToKrw(String dateOrLatest, String from)
            throws IOException, JSONException {
        return getRateSync(dateOrLatest, from, "KRW");
    }
}
