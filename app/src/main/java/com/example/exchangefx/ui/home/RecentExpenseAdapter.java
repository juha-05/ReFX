package com.example.exchangefx.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.exchangefx.R;
import com.example.exchangefx.data.entity.Expense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentExpenseAdapter extends RecyclerView.Adapter<RecentExpenseAdapter.VH> {

    private final List<Expense> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        TextView sub;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            sub   = v.findViewById(R.id.tv_sub);
        }
    }

    public RecentExpenseAdapter() {
    }

    public void setItems(List<Expense> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense_compact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Expense e = items.get(position);

        // 제목: 메모가 있으면 메모, 없으면 통화 코드 정도로 표시
        String titleText;
        if (e.memo != null && !e.memo.isEmpty()) {
            titleText = e.memo;
        } else {
            titleText = e.currency + " 지출";
        }
        holder.title.setText(titleText);

        // 서브 텍스트: 날짜 + 원래 통화 금액 정도만 일단 표시
        String dateStr;
        if (e.dateMillis > 0) {
            dateStr = dateFormat.format(e.dateMillis);
        } else if (e.dateStr != null) {
            dateStr = e.dateStr;
        } else {
            dateStr = "";
        }

        // 예: "2025.11.04 · USD 12.34"
        String subText = String.format(
                Locale.getDefault(),
                "%s · %s %.2f",
                dateStr,
                e.currency,
                e.amount
        );
        holder.sub.setText(subText);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
