package com.example.exchangefx.ui.expense;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;

import com.example.exchangefx.R;

public class ExpenseEditNav extends AppCompatActivity {

    public static final String EXTRA_OPEN_ADD = "open_add";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expense_edit_nav);

        Intent intent = getIntent();

        boolean openAdd = intent.getBooleanExtra(EXTRA_OPEN_ADD, false);
        int editId = intent.getIntExtra("edit_id", -1);

        // 우선순위: 수정 → 추가 → 목록
        if (editId != -1) {
            showExpenseAddWithId(editId);     // 수정 모드
        } else if (openAdd) {
            showExpenseAdd();                 // 추가 모드
        } else {
            showExpenseList();                // 기본 목록
        }
    }

    // ----------------------------------------------------
    // Fragment 1: 지출 목록 화면 (기본 첫 화면)
    // ----------------------------------------------------
    public void showExpenseList() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ExpenseListFragment())
                // ⚠ 목록은 절대 백스택에 넣지 말아야 한다!
                // .addToBackStack(null)  ← 제거
                .commit();
    }

    // ----------------------------------------------------
    // Fragment 2: 지출 추가 화면
    // ----------------------------------------------------
    public void showExpenseAdd() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ExpenseAddFragment())
                .addToBackStack(null)   // 뒤로가기 시 목록 복귀
                .commit();
    }

    // ----------------------------------------------------
    // Fragment 3: 지출 수정 화면
    // ----------------------------------------------------
    public void showExpenseAddWithId(int expenseId) {

        ExpenseAddFragment fragment = ExpenseAddFragment.newInstance(expenseId);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)   // 뒤로가기 시 목록 복귀
                .commit();
    }
}
