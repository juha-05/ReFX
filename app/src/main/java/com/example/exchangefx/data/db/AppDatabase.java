package com.example.exchangefx.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.exchangefx.data.dao.ExpenseDao;
import com.example.exchangefx.data.entity.Expense;

/**
 * Room 메인 DB 클래스.
 * 여기서 정의한 entities가 SQLite 테이블로 생성됨.
 */
@Database(
        entities = {Expense.class},   // 지금은 expense 테이블만
        version = 1,
        exportSchema = false          // schema export 에러 막기용
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    // 각 DAO를 이 메서드로 노출
    public abstract ExpenseDao expenseDao();

    // 싱글톤 인스턴스 가져오기
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "exchangefx.db"  // 실제 SQLite 파일 이름
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
