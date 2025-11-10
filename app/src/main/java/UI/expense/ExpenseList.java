package UI.expense;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.exchangefx.R;
import java.util.*;

public class ExpenseList extends Fragment {

    private boolean useToday = true;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup parent, Bundle b) {
        View v = inf.inflate(R.layout.fragment_expense_list, parent, false);

        // 툴바 뒤로가기
        v.findViewById(R.id.toolbar).setOnClickListener(x -> requireActivity().onBackPressed());

        // 스피너 샘플
        Spinner spCurrency = v.findViewById(R.id.sp_currency);
        spCurrency.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("원화", "USD", "EUR", "JPY")));

        Spinner spMonth = v.findViewById(R.id.sp_month);
        spMonth.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("2025. 11", "2025. 10", "2025. 09")));

        // 환율 기준 버튼
        Button btnToday = v.findViewById(R.id.btn_today);
        Button btnAtTime = v.findViewById(R.id.btn_at_time);
        btnToday.setOnClickListener(x -> { useToday = true;  loadList(v); });
        btnAtTime.setOnClickListener(x -> { useToday = false; loadList(v); });

        // 리스트
        RecyclerView rv = v.findViewById(R.id.recycler_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new RowAdapter(dummy()));

        loadList(v);
        return v;
    }

    private void loadList(View v){
        // TODO 실제 DB+환율로 계산. 지금은 더미 총액만 표시.
        TextView tv = v.findViewById(R.id.tv_month_total);
        tv.setText(useToday ? "₩ 1,234,000" : "₩ 1,210,000");
    }

    // ----- 간단한 어댑터/아이템 -----
    private static class RowAdapter extends RecyclerView.Adapter<RowVH>{
        List<String> data; RowAdapter(List<String> d){ data=d; }
        @NonNull @Override public RowVH onCreateViewHolder(@NonNull ViewGroup p,int t){
            View v=LayoutInflater.from(p.getContext()).inflate(R.layout.item_expense_row,p,false);
            return new RowVH(v);
        }
        @Override public void onBindViewHolder(@NonNull RowVH h,int i){ h.bind(data.get(i)); }
        @Override public int getItemCount(){ return data.size(); }
    }
    private static class RowVH extends RecyclerView.ViewHolder{
        TextView left, right, sub; RowVH(View v){
            super(v); left=v.findViewById(R.id.tv_left); right=v.findViewById(R.id.tv_right); sub=v.findViewById(R.id.tv_sub);
        }
        void bind(String name){ left.setText(name); right.setText("+ ₩100"); sub.setText("₩20,000"); }
    }
    private static List<String> dummy(){ return Arrays.asList("파스타 (₩→€)","딸기우유 ($→₩)","신발 (₩→€)","피자 ($→₩)"); }
}
