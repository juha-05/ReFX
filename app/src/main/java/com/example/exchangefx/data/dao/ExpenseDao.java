package com.example.exchangefx.data.dao;

import androidx.room.*;
import com.example.exchangefx.data.entity.Expense;

import java.util.List;

@Dao
public interface ExpenseDao {

    // INSERT: 새 지출 추가
    @Insert
    long insert(Expense expense);

    // UPDATE: 지출 수정
    @Update
    int update(Expense expense);

    // DELETE: 지출 삭제
    @Delete
    int delete(Expense expense);

    // 모든 지출 내역 (최근 날짜순)
    @Query("SELECT * FROM expense ORDER BY date_millis DESC")
    List<Expense> getAll();

    // 특정 id로 한 건 찾기
    @Query("SELECT * FROM expense WHERE id = :id LIMIT 1")
    Expense findById(long id);
}
