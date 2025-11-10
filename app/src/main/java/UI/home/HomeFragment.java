package UI.home;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.exchangefx.R;
import UI.expense.ExpenseList;
import UI.home.dummy.HomeDummy;
import UI.main.MainActivity;

public class HomeFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup parent, Bundle b) {
        View v = inf.inflate(R.layout.fragment_home, parent, false);

        // 최근 지출 목록 더미 연결
        RecyclerView rv = v.findViewById(R.id.recycler_recent);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new RecentExpenseAdapter(HomeDummy.recentNames())); // 아래 어댑터 사용

        // 총 지출 카드 → 지출내역 화면으로
        v.findViewById(R.id.card_month_total).setOnClickListener(view -> {
            ((MainActivity) requireActivity()).replace(new ExpenseList(), true);
        });

        // 날짜 표시는 더미
        ((TextView) v.findViewById(R.id.tv_section_date)).setText("2025. 11. 04");
        return v;
    }
}
