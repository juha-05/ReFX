package com.example.exchangefx.ui.expense;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.exchangefx.R;
import com.example.exchangefx.data.dao.ExpenseDao;
import com.example.exchangefx.data.db.AppDatabase;
import com.example.exchangefx.data.entity.Expense;
import com.example.exchangefx.utils.FrankfurterCall;

import org.json.JSONException;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExpenseList extends Fragment {

    // true  = 오늘 환율 기준
    // false = 지출 시점 환율 기준
    private boolean useToday = true;

    // 현재 기준 통화 (드롭다운에서 선택: KRW, USD, JPY, EUR, CNY)
    private String currentBaseCurrency = "KRW";

    // 선택된 연/월 (2025. 11 같은 것)
    private int selectedYear;
    private int selectedMonth; // 1 ~ 12

    private TextView tvTotalAmount;
    private TextView tvMonthYear;
    private TextView tvCurrencyDropdown;
    private Button btnTodayRate;
    private Button btnTransactionRate;
    private RecyclerView rvExpenseList;

    private ExpenseDao expenseDao;
    private FrankfurterCall fxClient;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private RowAdapter rowAdapter;

    public ExpenseList() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_expense_list, container, false);

        // ----- View 연결 -----

        // 1) 상단 뒤로가기 버튼
        v.findViewById(R.id.btn_back).setOnClickListener(view ->
                requireActivity().onBackPressed()
        );

        // 2) 요약 영역
        tvTotalAmount      = v.findViewById(R.id.tv_total_expense_list_amount);
        tvMonthYear        = v.findViewById(R.id.tv_month_year_selector);
        tvCurrencyDropdown = v.findViewById(R.id.tv_currency_dropdown);

        // 3) 환율 기준 토글 버튼
        btnTodayRate        = v.findViewById(R.id.btn_today_rate_list);
        btnTransactionRate  = v.findViewById(R.id.btn_transaction_rate_list);

        // 4) 리스트
        rvExpenseList = v.findViewById(R.id.rv_expense_list);

        // ----- DB & Frankfurter 클라이언트 준비 -----
        expenseDao = AppDatabase.getInstance(requireContext()).expenseDao();
        fxClient   = new FrankfurterCall();

        // ----- 기본 날짜(이번 달) 설정 -----
        Calendar cal = Calendar.getInstance();
        selectedYear  = cal.get(Calendar.YEAR);
        selectedMonth = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH는 0 기반

        tvMonthYear.setText(
                String.format(Locale.KOREA, "%04d. %02d ▾", selectedYear, selectedMonth)
        );

        // 월/연도 텍스트 클릭 → DatePickerDialog 로 월 선택
        tvMonthYear.setOnClickListener(view -> {
            DatePickerDialog dialog = new DatePickerDialog(
                    requireContext(),
                    (picker, year, month, dayOfMonth) -> {
                        selectedYear  = year;
                        selectedMonth = month + 1;

                        tvMonthYear.setText(
                                String.format(Locale.KOREA, "%04d. %02d ▾", year, month + 1)
                        );
                        recalcAndRender();
                    },
                    selectedYear,
                    selectedMonth - 1,
                    1
            );
            dialog.show();
        });

        // ----- 통화 드롭다운: KRW / USD / JPY / EUR / CNY -----
        tvCurrencyDropdown.setText("KRW ▾");

        tvCurrencyDropdown.setOnClickListener(view -> {
            final String[] currencies = {"KRW", "USD", "JPY", "EUR", "CNY"};

            new AlertDialog.Builder(requireContext())
                    .setTitle("통화 선택")
                    .setItems(currencies, (dialog, which) -> {
                        currentBaseCurrency = currencies[which];
                        tvCurrencyDropdown.setText(currentBaseCurrency + " ▾");
                        recalcAndRender();
                    })
                    .show();
        });

        // ----- 환율 기준 버튼 리스너 -----
        btnTodayRate.setOnClickListener(view -> {
            useToday = true;
            updateRateToggleUI();
            recalcAndRender();
        });

        btnTransactionRate.setOnClickListener(view -> {
            useToday = false;
            updateRateToggleUI();
            recalcAndRender();
        });

        // ----- RecyclerView 세팅 -----
        rowAdapter = new RowAdapter();
        rvExpenseList.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvExpenseList.setAdapter(rowAdapter);

        // 초기 UI 상태 반영 + 데이터 계산
        updateRateToggleUI();
        recalcAndRender();

        return v;
    }

    /** 환율 기준 버튼 UI 상태(선택/비선택) 업데이트 */
    private void updateRateToggleUI() {
        btnTodayRate.setSelected(useToday);
        btnTransactionRate.setSelected(!useToday);
        // selector로 배경/텍스트 색 바꾸고 싶으면
        // state_selected 기반 drawable/color selector를 쓰면 됨.
    }

    /**
     * DB + Frankfurter를 이용해
     *  - 선택한 연/월 데이터만 뽑고
     *  - 선택한 기준 통화(currentBaseCurrency)로
     *  - 선택한 환율 기준(useToday: 오늘 / 아니면 지출 시점)
     * 으로 리스트와 총액을 다시 계산해서 그려준다.
     */
    private void recalcAndRender() {
        ioExecutor.execute(() -> {
            List<Expense> all = expenseDao.getAll(); // date_millis DESC

            List<RowItem> rows = new ArrayList<>();
            double totalBaseAmount = 0.0;

            String base = currentBaseCurrency;
            if (base == null || base.isEmpty()) {
                base = "KRW";
            }

            boolean basisToday = useToday;

            SimpleDateFormat rowDateFormat =
                    new SimpleDateFormat("MM. dd", Locale.KOREA);

            for (Expense e : all) {
                // 1) 선택한 연/월에 속하는지 체크
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(e.dateMillis);

                int y = c.get(Calendar.YEAR);
                int m = c.get(Calendar.MONTH) + 1;

                if (y != selectedYear || m != selectedMonth) {
                    continue;
                }

                // 2) 환율 계산
                String from = e.currency;
                String to   = base;

                if (from == null || from.isEmpty()) {
                    // 통화 정보 없으면 스킵
                    continue;
                }

                double rateToday;
                double rateAtSpend;
                try {
                    if (from.equals(to)) {
                        // 같은 통화면 환율 1
                        rateToday   = 1.0;
                        rateAtSpend = 1.0;
                    } else {
                        // "latest" vs e.dateStr("YYYY-MM-DD")
                        rateToday   = fxClient.getRateSync("latest", from, to);
                        rateAtSpend = fxClient.getRateSync(e.dateStr, from, to);
                    }
                } catch (IOException | JSONException ex) {
                    ex.printStackTrace();
                    // 이 항목은 건너뜀
                    continue;
                }

                double amountToday   = e.amount * rateToday;
                double amountAtSpend = e.amount * rateAtSpend;

                // 화면에 기준으로 보여줄 메인 금액
                double mainAmount = basisToday ? amountToday : amountAtSpend;
                totalBaseAmount += mainAmount;

                // 오늘 기준 - 지출 시점 기준 = 환차익/환차손
                double diff = amountToday - amountAtSpend;

                // 3) RowItem 만들기
                String dateLabel = rowDateFormat.format(new Date(e.dateMillis));
                String nameLabel =
                        (e.memo != null && !e.memo.isEmpty())
                                ? e.memo
                                : e.category;

                String mainLabel = formatAmount(mainAmount, to);
                String diffLabel = formatDiff(diff, to);

                rows.add(new RowItem(dateLabel, nameLabel, mainLabel, diffLabel));
            }

            double finalTotalBaseAmount = totalBaseAmount;
            String finalBase = base;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    tvTotalAmount.setText(formatAmount(finalTotalBaseAmount, finalBase));
                    rowAdapter.setItems(rows);
                });
            }
        });
    }

    /** 통화별 금액 포맷 (₩ 18,000 같이 표시) */
    private String formatAmount(double amount, String currencyCode) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.KOREA);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return symbolFor(currencyCode) + " " + nf.format(amount);
    }

    /** 환차익/환차손 텍스트 (+ ₩ 200 (환차익) / - ₩ 800 (환차손)) */
    private String formatDiff(double diff, String currencyCode) {
        // 거의 0이면 표시 안 함
        if (Math.abs(diff) < 0.005) {
            return "";
        }

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.KOREA);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);

        String symbol = symbolFor(currencyCode);
        String sign   = diff > 0 ? "+ " : "- ";
        double abs    = Math.abs(diff);

        String base = sign + symbol + " " + nf.format(abs);

        if (diff > 0) {
            return base + " (환차익)";
        } else {
            return base + " (환차손)";
        }
    }

    /** 통화 코드 → 심볼 */
    private String symbolFor(String code) {
        if (code == null) return "";
        switch (code) {
            case "KRW":
                return "₩";
            case "USD":
                return "$";
            case "EUR":
                return "€";
            case "JPY":
            case "CNY":
                return "¥";
            default:
                // 모르는 통화면 코드 그대로 앞에 붙여줌
                return code + " ";
        }
    }

    // =========================
    //   RecyclerView 내부 클래스
    // =========================

    /** 한 행에 표시할 데이터 구조 */
    private static class RowItem {
        final String date;
        final String name;
        final String amountPrimary;
        final String amountSecondary;

        RowItem(String date, String name,
                String amountPrimary, String amountSecondary) {
            this.date = date;
            this.name = name;
            this.amountPrimary = amountPrimary;
            this.amountSecondary = amountSecondary;
        }
    }

    /** 어댑터: 데이터 setItems로 갈아끼우는 형태로 변경 */
    private static class RowAdapter extends RecyclerView.Adapter<RowAdapter.RowVH> {

        private final List<RowItem> data = new ArrayList<>();

        RowAdapter() {
        }

        void setItems(List<RowItem> newItems) {
            data.clear();
            if (newItems != null) {
                data.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_expense_row, parent, false);
            return new RowVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RowVH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        /** 한 행(ViewHolder) */
        static class RowVH extends RecyclerView.ViewHolder {

            private final TextView tvDate;
            private final TextView tvName;
            private final TextView tvAmountPrimary;
            private final TextView tvAmountSecondary;

            RowVH(@NonNull View itemView) {
                super(itemView);
                tvDate            = itemView.findViewById(R.id.tv_date);
                tvName            = itemView.findViewById(R.id.tv_expense_detail_name);
                tvAmountPrimary   = itemView.findViewById(R.id.tv_expense_amount_primary);
                tvAmountSecondary = itemView.findViewById(R.id.tv_expense_amount_secondary);
            }

            void bind(RowItem item) {
                tvDate.setText(item.date);
                tvName.setText(item.name);
                tvAmountPrimary.setText(item.amountPrimary);
                tvAmountSecondary.setText(item.amountSecondary);
            }
        }
    }
}
