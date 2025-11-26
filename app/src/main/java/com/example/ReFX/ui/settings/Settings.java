package com.example.ReFX.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.example.ReFX.R;
import com.example.ReFX.data.db.AppDatabase2;
import com.example.ReFX.ui.chart.ChartsNav;
import com.example.ReFX.ui.expense.ExpenseEditNav;
import com.example.ReFX.ui.main.MainActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class Settings extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // ------------------------------
        // 캐시 삭제 버튼
        // ------------------------------
        TextView tvClearCache = findViewById(R.id.tvClearCache);
        tvClearCache.setOnClickListener(v -> {

            // ★ 확인창(AlertDialog) 추가 ★
            new AlertDialog.Builder(this)
                    .setTitle("캐시 삭제")
                    .setMessage("환율 캐시를 삭제하시겠습니까?")
                    .setPositiveButton("예", (dialog, which) -> {

                        // --- 실제 캐시 삭제 ---
                        new Thread(() -> {
                            AppDatabase2.getInstance(getApplicationContext())
                                    .fxRateCacheDao()
                                    .clearCache();

                            runOnUiThread(() ->
                                    Toast.makeText(this, "환율 캐시가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            );
                        }).start();
                    })
                    .setNegativeButton("아니오", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        // ------------------------------
        // 하단 BottomNavigation 설정
        // ------------------------------
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return true;
            }

            if (id == R.id.nav_add) {
                startActivity(new Intent(this, ExpenseEditNav.class));
                return true;
            }

            if (id == R.id.nav_charts) {
                startActivity(new Intent(this, ChartsNav.class));
                return true;
            }

            if (id == R.id.nav_settings) {
                return true;
            }

            return false;
        });

        bottomNav.setSelectedItemId(R.id.nav_settings);
    }
}
