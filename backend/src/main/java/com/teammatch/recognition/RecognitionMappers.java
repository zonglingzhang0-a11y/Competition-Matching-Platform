package com.teammatch.recognition;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public final class RecognitionMappers {

    private RecognitionMappers() {
    }

    @Mapper
    public interface RecognitionMapper extends BaseMapper<Recognition> {

        /** 改确认人数前先锁住这一条，两个人同时确认时人数和状态不会算错 */
        @Select("SELECT * FROM school_competition_recognition WHERE id = #{id} FOR UPDATE")
        Recognition lockById(@Param("id") long id);

        /**
         * 按确认记录重新数人数，够了就是 CONFIRMED，不够退回 PENDING；被驳回的保持驳回。
         * MySQL 的单表 UPDATE 从左往右赋值，后面的 status 用的是刚算好的 confirm_count。
         */
        @Update("""
                UPDATE school_competition_recognition
                SET confirm_count = (SELECT COUNT(*) FROM recognition_confirm WHERE recognition_id = #{id}),
                    status = IF(status = 'REJECTED', 'REJECTED', IF(confirm_count >= #{needed}, 'CONFIRMED', 'PENDING'))
                WHERE id = #{id}
                """)
        int recount(@Param("id") long id, @Param("needed") int needed);
    }

    /** recognition_confirm 是联合主键表，直接写 SQL */
    @Mapper
    public interface ConfirmMapper {

        @Insert("INSERT IGNORE INTO recognition_confirm (recognition_id, user_id) VALUES (#{rid}, #{userId})")
        int insert(@Param("rid") long recognitionId, @Param("userId") long userId);

        @Delete("DELETE FROM recognition_confirm WHERE recognition_id = #{rid} AND user_id = #{userId}")
        int delete(@Param("rid") long recognitionId, @Param("userId") long userId);

        @Delete("DELETE FROM recognition_confirm WHERE recognition_id = #{rid}")
        int deleteAll(@Param("rid") long recognitionId);

        /** 这个人在同一个学校、同一个比赛下确认过哪几条（正常只有一条） */
        @Select("""
                SELECT c.recognition_id FROM recognition_confirm c
                JOIN school_competition_recognition r ON r.id = c.recognition_id
                WHERE c.user_id = #{userId} AND r.school_id = #{schoolId} AND r.competition_id = #{competitionId}
                """)
        List<Long> confirmedInPair(@Param("userId") long userId, @Param("schoolId") long schoolId,
                                   @Param("competitionId") long competitionId);

        @Select("""
                <script>
                SELECT recognition_id FROM recognition_confirm
                WHERE user_id = #{userId} AND recognition_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                </script>
                """)
        List<Long> confirmedAmong(@Param("userId") long userId, @Param("ids") Collection<Long> recognitionIds);
    }
}
