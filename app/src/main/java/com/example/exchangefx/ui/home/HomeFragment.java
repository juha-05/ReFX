package com.example.exchangefx.ui.home;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.exchangefx.R;
import com.example.exchangefx.data.dao.ExpenseDao;
import com.example.exchangefx.data.db.AppDatabase;
import com.example.exchangefx.data.entity.Expense;
import com.example.exchangefx.ui.expense.ExpenseList;
import com.example.exchangefx.ui.main.MainActivity;
import com.example.exchangefx.ui.chart.ChartsNav;
import com.example.exchangefx.ui.expense.ExpenseEditNav;
import com.example.exchangefx.ui.settings.Settings;
import com.example.exchangefx.utils.FrankfurterCall;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 홈 화면 Fragment
 * 1) 상단 카드: 이번 달 총 지출 (환율 기준: 오늘 / 지출 시점)
 * 2) 중간: "2025.11.04" → 앱 실행일 기준 날짜로 표시
 * 3) 하단: 최근 지출 3~5개 (실제 DB 데이터)
 * 4) FAB: 빠른 지출 추가 (지출 추가 Fragment로 이동)
 */
public class HomeFragment extends Fragment {

    private TextView tvTotalExpenseAmount;
    private TextView tvSectionDate;
    private RadioButton rbToday;
    private RadioButton rbAtSpend;
    private RecyclerView recyclerRecent;
    private FloatingActionButton fabQuickAdd;

    private RecentExpenseAdapter recentAdapter;

    private ExpenseDao expenseDao;
    private FrankfurterCall fxRateClient;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private enum FxBasis {
        TODAY,
        AT_SPEND
    }

    private FxBasis currentBasis = FxBasis.TODAY;

    private static final int MAX_RECENT = 5;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // View 찾기
        tvTotalExpenseAmount = v.findViewById(R.id.tv_total_expense_amount);
        tvSectionDate        = v.findViewById(R.id.tv_section_date);
        rbToday              = v.findViewById(R.id.rb_today);
        rbAtSpend            = v.findViewById(R.id.rb_at_spend);
        recyclerRecent       = v.findViewById(R.id.recycler_recent);
        fabQuickAdd          = v.findViewById(R.id.fab_quick_add);
        BottomNavigationView bottomNav = v.findViewById(R.id.bottom_navigation);
        TextView tvChevron   = v.findViewById(R.id.tv_chevron);

        // 이번 달 총 지출 카드 우측 '>' → 지출 내역 Fragment로 이동
        tvChevron.setOnClickListener(view -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity())
                        .replace(new ExpenseList(), true); // backstack에 쌓기
            }
        });


        // DB, FX 클라이언트 준비
        expenseDao   = AppDatabase.getInstance(requireContext()).expenseDao();
        fxRateClient = new FrankfurterCall();

        // 2번: "2025.11.04" 부분을 오늘 날짜로 표시
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy. MM. dd", Locale.KOREA);
        String todayStr = sdf.format(new Date());
        tvSectionDate.setText(todayStr);

        // 최근 지출 RecyclerView 설정
        recentAdapter = new RecentExpenseAdapter();
        recyclerRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerRecent.setAdapter(recentAdapter);

        // 환율 기준 라디오 버튼 리스너
        rbToday.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentBasis = FxBasis.TODAY;
                recalcAndRender();
            }
        });
        rbAtSpend.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentBasis = FxBasis.AT_SPEND;
                recalcAndRender();
            }
        });

        // 기본값: 오늘 기준
        rbToday.setChecked(true);

        // 4번: FAB → 지출 추가 화면으로
        fabQuickAdd.setOnClickListener(view -> {
            Intent intent = new Intent(requireActivity(), ExpenseEditNav.class);
            intent.putExtra(ExpenseEditNav.EXTRA_OPEN_ADD, true);
            startActivity(intent);
        });

        // 하단 네비게이션 바
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // 이미 홈이므로 아무 것도 안 해도 됨
                return true;
            } else if (id == R.id.nav_add) {
                Intent intent = new Intent(requireActivity(), ExpenseEditNav.class);
                intent.putExtra(ExpenseEditNav.EXTRA_OPEN_ADD, true);
                startActivity(intent);
                return true;
            } else if (id == R.id.nav_charts) {
                startActivity(new Intent(requireActivity(), ChartsNav.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(requireActivity(), Settings.class));
                return true;
            }
            return false;
        });

        // 처음 진입 시 데이터 로드
        recalcAndRender();

        return v;
    }

    /**
     * DB에서 지출을 가져와서
     * - 환율 기준에 따라 총액(₩) 계산
     * - 최근 지출 3~5개 어댑터에 세팅
     */
    private void recalcAndRender() {
        ioExecutor.execute(() -> {
            List<Expense> all = expenseDao.getAll();  // date_millis DESC 정렬됨

            double totalKrw = 0.0;

            for (Expense e : all) {
                double amount = e.amount;
                String currency = e.currency;

                if (currency == null || currency.isEmpty()) {
                    // 통화 정보 없으면 그냥 스킵
                    continue;
                }

                double rate = 1.0;
                if (!"KRW".equalsIgnoreCase(currency)) {
                    String dateParam;
                    if (currentBasis == FxBasis.TODAY) {
                        dateParam = "latest";
                    } else {
                        // 지출 시점 환율: dateStr 사용, 없으면 latest
                        if (e.dateStr != null && !e.dateStr.isEmpty()) {
                            // Frankfurter 날짜 포맷: yyyy-MM-dd 가정
                            dateParam = e.dateStr;
                        } else {
                            dateParam = "latest";
                        }
                    }
                    try {
                        rate = fxRateClient.getRateToKrw(dateParam, currency);
                    } catch (IOException | JSONException ex) {
                        ex.printStackTrace();
                        // 오류난 애는 그냥 total 계산에서 제외
                        continue;
                    }
                }

                totalKrw += (amount * rate);
            }

            double finalTotalKrw = totalKrw;

            // 최근 N개만 추려서 어댑터에 전달
            int max = Math.min(all.size(), MAX_RECENT);
            List<Expense> recent = all.subList(0, max);

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    tvTotalExpenseAmount.setText(formatKrw(finalTotalKrw));
                    recentAdapter.setItems(recent);
                });
            }
        });
    }

    private String formatKrw(double amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.KOREA);
        return nf.format(amount);
    }
}
