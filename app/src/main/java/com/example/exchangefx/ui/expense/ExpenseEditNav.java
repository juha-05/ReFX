package com.example.exchangefx.ui.expense;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.example.exchangefx.R;

public class ExpenseEditNav extends AppCompatActivity {

    public static final String EXTRA_OPEN_ADD = "open_add";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_edit_nav);

        boolean openAdd = getIntent().getBooleanExtra(EXTRA_OPEN_ADD, false);

        if (openAdd) {
            showExpenseAdd();
        } else {
            showExpenseList();
        }
    }

    // Fragment 1: 지출 목록 화면
    public void showExpenseList() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ExpenseListFragment())
                .commit();
    }

    // Fragment 2: 지출 추가/수정 화면
    public void showExpenseAdd() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ExpenseAddFragment())
                .addToBackStack(null)  // 뒤로가기 가능
                .commit();
    }
}
