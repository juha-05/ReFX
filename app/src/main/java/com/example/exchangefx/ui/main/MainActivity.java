package com.example.exchangefx.ui.main;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.exchangefx.ui.home.HomeFragment;
import com.example.exchangefx.R;
import androidx.fragment.app.FragmentTransaction;
import com.example.exchangefx.data.db.AppDatabase;
import com.example.exchangefx.data.dao.ExpenseDao;
import com.example.exchangefx.data.entity.Expense;

public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        AppDatabase db = AppDatabase.getInstance(this);
        ExpenseDao expenseDao = db.expenseDao();

        new Thread(() -> {
            long now = System.currentTimeMillis();

            Expense e = new Expense("2025-11-17", 12.34, "USD", "식비");
            e.dateMillis = now;
            e.memo = "버거킹";
            e.createdAt = now;
            e.updatedAt = now;

            long newId = expenseDao.insert(e);
        }).start();
        if (b == null) replace(new HomeFragment(), false);
    }
    public void replace(Fragment f, boolean addToBackStack) {
        FragmentTransaction tx = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, f);
        if (addToBackStack) tx.addToBackStack(null);
        tx.commit();
    }
}
