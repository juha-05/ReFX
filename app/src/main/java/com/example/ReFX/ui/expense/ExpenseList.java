package com.example.ReFX.ui.expense;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.ReFX.R;
import com.example.ReFX.data.dao.ExpenseDao2;
import com.example.ReFX.data.db.AppDatabase2;
import com.example.ReFX.data.entity.Expense2;
import com.example.ReFX.utils.FrankfurterCall;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Currency;

/**
 * ExpenseList (지출내역)
 * - TODAY: 캐시 기반 최신 환율로 baseAmount → KRW 계산
 * - AT_SPEND: DB에 저장된 targetAmount 사용
 * - 환차익/환차손: TODAY 기준 금액 - 지출 기준 금액
 */
public class ExpenseList extends Fragment {

    // true = 오늘 기준, false = 지출 시점 기준
    private boolean useToday = true;

    private int selectedYear;
    private int selectedMonth;

    // 현재 화면에 표시할 통화 (드롭다운에서 선택)
    private String displayCurrency = "KRW";
    private TextView tvCurrencyDropdown;

    private TextView tvTotalAmount;
    private TextView tvMonthYear;
    private Button btnTodayRate;
    private Button btnTransactionRate;
    private RecyclerView rvExpenseList;

    private ExpenseDao2 expenseDao;
    private FrankfurterCall fxClient;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private RowAdapter rowAdapter;

    public ExpenseList() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_expense_list, container, false);

        v.findViewById(R.id.btn_back).setOnClickListener(view ->
                requireActivity().onBackPressed()
        );

        tvCurrencyDropdown  = v.findViewById(R.id.tv_currency_dropdown);
        tvTotalAmount      = v.findViewById(R.id.tv_total_expense_list_amount);
        tvMonthYear        = v.findViewById(R.id.tv_month_year_selector);
        btnTodayRate       = v.findViewById(R.id.btn_today_rate_list);
        btnTransactionRate = v.findViewById(R.id.btn_transaction_rate_list);
        rvExpenseList      = v.findViewById(R.id.rv_expense_list);

        expenseDao = AppDatabase2.getInstance(requireContext()).expenseDao2();
        fxClient   = new FrankfurterCall();

        tvCurrencyDropdown.setOnClickListener(view -> showCurrencyMenu(view));

        // 오늘 날짜 기준 월 선택
        Calendar cal = Calendar.getInstance();
        selectedYear  = cal.get(Calendar.YEAR);
        selectedMonth = cal.get(Calendar.MONTH) + 1;

        tvMonthYear.setText(String.format(Locale.KOREA,"%04d. %02d ▾", selectedYear, selectedMonth));

        // 월 선택 클릭
        tvMonthYear.setOnClickListener(view -> {
            DatePickerDialog dialog = new DatePickerDialog(
                    requireContext(),
                    (picker, year, month, day) -> {
                        selectedYear  = year;
                        selectedMonth = month + 1;
                        tvMonthYear.setText(String.format(Locale.KOREA,"%04d. %02d ▾", year, month + 1));
                        recalcAndRender();
                    },
                    selectedYear,
                    selectedMonth - 1,
                    1
            );
            dialog.show();
        });

        // 환율 기준 버튼
        btnTodayRate.setOnClickListener(view -> {
            useToday = true;
            updateToggleUI();
            recalcAndRender();
        });

        btnTransactionRate.setOnClickListener(view -> {
            useToday = false;
            updateToggleUI();
            recalcAndRender();
        });

        // 리스트 세팅
        rowAdapter = new RowAdapter();
        rvExpenseList.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvExpenseList.setAdapter(rowAdapter);

        updateToggleUI();
        recalcAndRender();

        return v;
    }

    // KRW ▾ 클릭했을 때 통화 선택 팝업
    private void showCurrencyMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("KRW");
        menu.getMenu().add("USD");
        menu.getMenu().add("JPY");
        menu.getMenu().add("EUR");
        menu.getMenu().add("CNY");

        menu.setOnMenuItemClickListener(item -> {
            String selected = item.getTitle().toString();
            displayCurrency = selected;
            tvCurrencyDropdown.setText(selected + " ▾");
            recalcAndRender();   // 통화 바뀌면 전체 다시 계산
            return true;
        });

        menu.show();
    }

    private void updateToggleUI() {
        btnTodayRate.setSelected(useToday);
        btnTransactionRate.setSelected(!useToday);
    }

    /**
     * 핵심 계산 로직:
     * - DB 전체 읽기
     * - 선택된 연/월에 해당하는 지출만 필터링
     * - TODAY / AT_SPEND 기준에 따른 금액 계산
     * - 환차익/환차손 계산 (TODAY 기준 - AT_SPEND 기준)
     */
    private void recalcAndRender() {
        ioExecutor.execute(() -> {

            List<Expense2> all = expenseDao.getAllExpenses();
            List<RowItem> rows = new ArrayList<>();
            double total = 0.0;

            for (Expense2 e : all) {

                // 날짜 파싱(YYYY-MM-DD 혹은 YYYY. MM. DD)
                if (e.spendDate == null || e.spendDate.length() < 7) continue;

                int year;
                int month;
                try {
                    year = Integer.parseInt(e.spendDate.substring(0, 4));

                    if (e.spendDate.charAt(4) == '-') {
                        // 예: 2025-11-23
                        month = Integer.parseInt(e.spendDate.substring(5, 7));
                    } else {
                        // 예: 2025. 11. 23
                        month = Integer.parseInt(e.spendDate.substring(6, 8));
                    }
                } catch (Exception ex) {
                    continue;
                }

                if (year != selectedYear || month != selectedMonth) continue;

                // 환산 금액 계산 (선택된 통화 기준)
                double todayAmount;
                double atSpendAmount;
                double mainAmount;

                try {
                    // 오늘 환율 기준 (latest)
                    todayAmount = convertAmount(e.baseAmount, e.baseCurrency, "latest");

                    // 지출 시점 환율 기준
                    String spendDate = (e.fxDate == null || e.fxDate.isEmpty())
                            ? "latest"
                            : e.fxDate;
                    atSpendAmount = convertAmount(e.baseAmount, e.baseCurrency, spendDate);

                } catch (Exception ex) {
                    ex.printStackTrace();
                    continue;
                }

                // 오늘/지출시점 토글에 따라 표시 금액 선택
                mainAmount = useToday ? todayAmount : atSpendAmount;
                total += mainAmount;

                // 두 기준 차이 (같은 통화 기준에서 계산)
                double diff = todayAmount - atSpendAmount;
                String diffLabel = formatDiff(diff);

                rows.add(new RowItem(
                        e.spendDate,
                        (e.name != null && !e.name.isEmpty())
                                ? e.name
                                : ((e.memo != null && !e.memo.isEmpty()) ? e.memo : e.category),
                        formatAmount(mainAmount),   // 아래에서 통화 기호까지 포맷
                        diffLabel
                ));
            }


            double finalTotal = total;
            List<RowItem> finalRows = rows;

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    tvTotalAmount.setText(formatAmount(finalTotal));
                    rowAdapter.setItems(finalRows);
                });
            }
        });
    }


    /**
     * baseAmount / baseCurrency 를 현재 선택된 displayCurrency 기준으로 환산
     * @param date "latest" 또는 "yyyy-MM-dd"
     */
    private double convertAmount(double baseAmount, String baseCurrency, String date) throws Exception {
        if (baseCurrency == null || baseCurrency.isEmpty()) return 0.0;
        if (displayCurrency == null || displayCurrency.isEmpty()) {
            displayCurrency = "KRW";
        }

        // 이미 같은 통화면 그대로 사용
        if (baseCurrency.equalsIgnoreCase(displayCurrency)) {
            return baseAmount;
        }

        String dateParam = (date == null || date.isEmpty()) ? "latest" : date;

        double rate = fxClient.getRateWithCache(
                requireContext(),
                dateParam,
                baseCurrency,
                displayCurrency
        );
        return baseAmount * rate;
    }

    // 선택된 통화(displayCurrency)에 맞춰 금액 포맷
    private String formatAmount(double amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
        try {
            nf.setCurrency(Currency.getInstance(displayCurrency));
        } catch (Exception e) {
            // 통화 코드가 이상하면 기본 통화 사용
        }
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    private String formatDiff(double diff) {
        if (Math.abs(diff) < 0.005) return "";

        double abs = Math.abs(diff);
        String formatted = formatAmount(abs); // 위에서 선택 통화 기준으로 포맷

        if (diff > 0) {
            return "+ " + formatted + " (환차익)";
        } else {
            return "- " + formatted + " (환차손)";
        }
    }


    // ============================
    // RecyclerView 내부 클래스
    // ============================

    private static class RowItem {
        final String date;
        final String name;
        final String amount;
        final String diff;

        RowItem(String date, String name, String amount, String diff) {
            this.date = date;
            this.name = name;
            this.amount = amount;
            this.diff = diff;
        }
    }

    private static class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

        private final List<RowItem> data = new ArrayList<>();

        void setItems(List<RowItem> newItems) {
            data.clear();
            if (newItems != null) data.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_expense_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvName, tvAmount, tvDiff;
            VH(View v) {
                super(v);
                tvDate   = v.findViewById(R.id.tv_date);
                tvName   = v.findViewById(R.id.tv_expense_detail_name);
                tvAmount = v.findViewById(R.id.tv_expense_amount_primary);
                tvDiff   = v.findViewById(R.id.tv_expense_amount_secondary);
            }
            void bind(RowItem item) {
                tvDate.setText(item.date);
                tvName.setText(item.name);
                tvAmount.setText(item.amount);
                tvDiff.setText(item.diff);
            }
        }
    }
}
