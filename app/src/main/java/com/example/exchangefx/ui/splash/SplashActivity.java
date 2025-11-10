package com.example.exchangefx.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.example.exchangefx.ui.main.MainActivity;
import com.example.exchangefx.R;
public class SplashActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 1200);
    }
}
