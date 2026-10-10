package com.teammatch.profile;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public final class ProfileMappers {

    private ProfileMappers() {
    }

    @Mapper
    public interface SkillMapper extends BaseMapper<Skill> {
    }

    @Mapper
    public interface ExperienceMapper extends BaseMapper<UserExperience> {
    }

    /** user_skill 是联合主键表，直接写 SQL */
    @Mapper
    public interface UserSkillMapper {

        @Select("""
                SELECT s.id AS skillId, s.name, s.category, us.level
                FROM user_skill us JOIN skill s ON s.id = us.skill_id
                WHERE us.user_id = #{userId}
                ORDER BY us.level DESC, s.sort
                """)
        List<SkillLevelRow> listByUser(@Param("userId") long userId);

        @Delete("DELETE FROM user_skill WHERE user_id = #{userId}")
        int deleteByUser(@Param("userId") long userId);

        @Insert("""
                <script>
                INSERT INTO user_skill (user_id, skill_id, level) VALUES
                <foreach collection="skills" item="s" separator=",">(#{userId}, #{s.skillId}, #{s.level})</foreach>
                </script>
                """)
        int insertAll(@Param("userId") long userId, @Param("skills") List<ProfileDtos.SkillLevelInput> skills);

        record SkillLevelRow(Long skillId, String name, String category, Integer level) {
        }
    }
}
