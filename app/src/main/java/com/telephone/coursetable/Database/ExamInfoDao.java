package com.telephone.coursetable.Database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * @clear
 */
@Dao
public interface ExamInfoDao {
    @Query("delete from ExamInfo")
    void deleteAll();

    @Query("select * from ExamInfo order by sts DESC")
    List<ExamInfo> selectAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ExamInfo tuple);

    @Query("select * from ExamInfo where examdate>=:today order by sts DESC")
    List<ExamInfo> selectFromToday(String today);
}
