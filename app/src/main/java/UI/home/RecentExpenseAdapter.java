package UI.home;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.exchangefx.R;
import java.util.*;

public class RecentExpenseAdapter extends RecyclerView.Adapter<RecentExpenseAdapter.VH> {
    private final List<String> items;
    public RecentExpenseAdapter(List<String> items) { this.items = items; }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, sub; VH(View v){
            super(v);
            title = v.findViewById(R.id.tv_title);
            sub   = v.findViewById(R.id.tv_sub);
        }
    }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vType) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_expense_compact, p, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int i) {
        h.title.setText(items.get(i)); h.sub.setText("₩ 16,000  ·  $12");
    }
    @Override public int getItemCount() { return items.size(); }

    // 더미 데이터
    public static class Dummy {
        public static List<String> data() { return Arrays.asList("파스타", "운동화", "커피"); }
    }
}
