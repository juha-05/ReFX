package com.example.exchangefx.ui.chart;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import com.example.exchangefx.utils.FrankfurterCall;
import java.io.IOException;
import com.example.exchangefx.R;
import com.example.exchangefx.data.dao.ExpenseDao2;
import com.example.exchangefx.data.db.AppDatabase2;
import com.example.exchangefx.data.entity.Expense2;
import com.example.exchangefx.ui.expense.ExpenseEditNav;
import com.example.exchangefx.ui.main.MainActivity;
import com.example.exchangefx.ui.settings.Settings;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.exchangefx.utils.FrankfurterCall;
import java.io.IOException;

/**
 * 4장 - 시각화 Activity
 * - 카테고리별 라디오 버튼: 파이 차트
 * - 월별 라디오 버튼: 막대 차트 (x축: 월, y축: 원(KRW))
 * - 금액 기준은 DB에 저장된 targetAmount(KRW, 지출 시점 환율 기준) 사용
 */
public class ChartsNav extends AppCompatActivity {

    private PieChart pieChart;
    private BarChart barChart;
    private RadioButton rbCategory, rbMonthly;

    private ExpenseDao2 expenseDao;
    private ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // 환율 호출 + 캐시용
    private FrankfurterCall fxClient = new FrankfurterCall();

    // 미리 계산해둔 합계들
    private Map<String, Double> sumByCategory = new LinkedHashMap<>();
    private Map<String, Double> sumByMonth = new TreeMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charts_nav);

        pieChart   = findViewById(R.id.pieChart);
        barChart   = findViewById(R.id.barChart);
        rbCategory = findViewById(R.id.rbCategory);
        rbMonthly  = findViewById(R.id.rbMonthly);

        expenseDao = AppDatabase2.getInstance(this).expenseDao2();

        // 하단 BottomNavigation 설정
        setupBottomNav();

        // 라디오 버튼 그룹
        RadioGroup radioGroup = findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCategory) {
                showCategoryChart();
            } else if (checkedId == R.id.rbMonthly) {
                showMonthlyChart();
            }
        });

        // 차트 기본 설정
        setupPieChartStyle();
        setupBarChartStyle();

        // DB에서 지출 내역 읽어서 차트용 데이터 준비
        loadChartData();
    }

    // ---------------- BottomNavigation ----------------

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // 홈
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return true;
            }

            // 추가 (3장)
            if (id == R.id.nav_add) {
                startActivity(new Intent(this, ExpenseEditNav.class));
                return true;
            }

            // 차트 (현재 화면)
            if (id == R.id.nav_charts) {
                return true;
            }

            // 설정 (5장)
            if (id == R.id.nav_settings) {
                startActivity(new Intent(this, Settings.class));
                return true;
            }

            return false;
        });

        // 현재 화면이 "차트"이므로 선택된 상태로 표시
        bottomNav.setSelectedItemId(R.id.nav_charts);
    }

    // ---------------- DB에서 데이터 읽기 ----------------

    void loadChartData() {
        ioExecutor.execute(() -> {
            List<Expense2> all = expenseDao.getAllExpenses();

            Map<String, Double> catMap = new LinkedHashMap<>();
            Map<String, Double> monthMap = new TreeMap<>();

            for (Expense2 e : all) {

                String baseCur   = (e.baseCurrency == null) ? "" : e.baseCurrency;
                String targetCur = (e.targetCurrency == null) ? "" : e.targetCurrency;

                double amountKrw = 0.0;

                try {
                    // 1) targetCurrency가 이미 KRW인 경우 → targetAmount 그대로 사용
                    if ("KRW".equalsIgnoreCase(targetCur)) {
                        amountKrw = e.targetAmount;
                    }
                    // 2) baseCurrency가 KRW인 경우 → baseAmount 그대로 사용
                    else if ("KRW".equalsIgnoreCase(baseCur)) {
                        amountKrw = e.baseAmount;
                    }
                    // 3) 둘 다 KRW가 아니면: baseCurrency → KRW로 다시 환산
                    else {
                        double rateToKrw = fxClient.getRateWithCache(
                                getApplicationContext(),  // Context
                                e.fxDate,                 // 지출 당시 환율 날짜 (null이면 latest)
                                baseCur,                  // 저장된 외화 통화
                                "KRW"                     // 항상 KRW로 환산
                        );
                        amountKrw = e.baseAmount * rateToKrw;
                    }
                } catch (IOException ex) {
                    // 환율 불러오기 실패해도 일단 0원으로 처리 (카테고리는 남기기)
                    amountKrw = 0.0;
                }

                // ---------------- 카테고리별 합계 ----------------
                String category = (e.category == null || e.category.isEmpty())
                        ? "기타"
                        : e.category;
                catMap.put(category, catMap.getOrDefault(category, 0.0) + amountKrw);

                // ---------------- 월별 합계 (yyyy-MM 기준) ----------------
                if (e.spendDate != null && e.spendDate.length() >= 7) {
                    String ym = e.spendDate.substring(0, 7); // "yyyy-MM"
                    monthMap.put(ym, monthMap.getOrDefault(ym, 0.0) + amountKrw);
                }
            }

            // 계산 끝난 맵을 필드에 반영
            sumByCategory = catMap;
            sumByMonth = monthMap;

            runOnUiThread(() -> {
                // 기본은 카테고리별 차트 보여주기
                rbCategory.setChecked(true);
                showCategoryChart();
            });
        });
    }


    // ---------------- PieChart 설정 & 렌더 ----------------

    private void setupPieChartStyle() {
        pieChart.setUsePercentValues(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(45f);
        pieChart.setEntryLabelTextSize(12f);
        pieChart.getLegend().setEnabled(true);

        Description desc = new Description();
        desc.setText("카테고리별 지출 (KRW)");
        pieChart.setDescription(desc);
    }

    private void showCategoryChart() {
        pieChart.setVisibility(View.VISIBLE);
        barChart.setVisibility(View.GONE);

        List<PieEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Double> entry : sumByCategory.entrySet()) {
            float value = entry.getValue().floatValue();
            entries.add(new PieEntry(value, entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        // 색상은 라이브러리 기본 팔레트 사용 (별도 지정 X)
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate();
    }

    // ---------------- BarChart 설정 & 렌더 ----------------

    private void setupBarChartStyle() {
        barChart.setDrawGridBackground(false);
        barChart.setPinchZoom(false);
        barChart.setScaleEnabled(false);

        Description desc = new Description();
        desc.setText("월별 지출 (KRW)");
        barChart.setDescription(desc);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);

        barChart.getAxisRight().setEnabled(false);
    }

    private void showMonthlyChart() {
        pieChart.setVisibility(View.GONE);
        barChart.setVisibility(View.VISIBLE);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        int index = 0;
        for (Map.Entry<String, Double> entry : sumByMonth.entrySet()) {
            float value = entry.getValue().floatValue();
            if (value <= 0f) continue;

            entries.add(new BarEntry(index, value));
            labels.add(formatMonthLabel(entry.getKey())); // "yyyy-MM" -> "yy.MM"
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "월별 지출 (KRW)");
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.4f);

        barChart.setData(data);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());

        barChart.invalidate();
    }

    private String formatMonthLabel(String ym) {
        // "2025-11" -> "25.11"
        try {
            String year = ym.substring(2, 4);
            String month = ym.substring(5, 7);
            return String.format(Locale.getDefault(), "%s.%s", year, month);
        } catch (Exception e) {
            return ym;
        }
    }
}
