package com.example.exchangefx.ui.chart;

import android.content.Intent;
import android.graphics.Color;
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

    private void loadChartData() {
        ioExecutor.execute(() -> {
            List<Expense2> all = expenseDao.getAllExpenses();

            Map<String, Double> catMap   = new LinkedHashMap<>();
            Map<String, Double> monthMap = new TreeMap<>();

            for (Expense2 e : all) {

                // DB에 저장된 targetAmount(KRW 기준)를 그대로 사용
                double amountKrw = e.targetAmount;
                if (Double.isNaN(amountKrw) || Double.isInfinite(amountKrw)) {
                    amountKrw = 0.0;
                }

                // ---------------- 카테고리별 합계 ----------------
                String category = (e.category == null || e.category.isEmpty())
                        ? "기타"
                        : e.category;

                double prevCat = catMap.containsKey(category)
                        ? catMap.get(category) : 0.0;
                catMap.put(category, prevCat + amountKrw);

                // ---------------- 월별 합계 (yyyy-MM 기준) ----------------
                if (e.spendDate != null && e.spendDate.length() >= 7) {
                    String ym = e.spendDate.substring(0, 7); // "YYYY-MM"
                    double prevMonth = monthMap.containsKey(ym)
                            ? monthMap.get(ym) : 0.0;
                    monthMap.put(ym, prevMonth + amountKrw);
                }
            }

            // 계산한 합계를 필드에 반영
            sumByCategory.clear();
            sumByCategory.putAll(catMap);

            sumByMonth.clear();
            sumByMonth.putAll(monthMap);

            // UI 갱신
            runOnUiThread(() -> {
                if (rbCategory.isChecked()) {
                    showCategoryChart();
                } else {
                    showMonthlyChart();
                }
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

            // 0 이하인 값은 차트에서 스킵
            if (value <= 0f) continue;

            entries.add(new PieEntry(value, entry.getKey()));  // 라벨 = 카테고리 이름
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextSize(18f);

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor(String.valueOf(R.color.point_blue)));
        colors.add(Color.parseColor("6DA7F2"));
        colors.add(Color.parseColor("#AAD1E7"));
        colors.add(Color.parseColor("#A0C4F2"));
        colors.add(Color.parseColor("#CEDEF2"));
        colors.add(Color.parseColor("#023373"));


        dataSet.setColors(colors);

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
