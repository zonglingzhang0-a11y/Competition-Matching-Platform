package com.teammatch.team;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public final class TeamMappers {

    private TeamMappers() {
    }

    @Mapper
    public interface TeamMapper extends BaseMapper<Team> {

        /** 加行锁：接受申请时防止两个人同时被同意、把队伍挤爆 */
        @Select("SELECT * FROM team WHERE id = #{id} AND deleted = 0 FOR UPDATE")
        Team selectForUpdate(@Param("id") long id);
    }

    @Mapper
    public interface MemberMapper extends BaseMapper<TeamMember> {

        @Select("""
                <script>
                SELECT team_id AS teamId, COUNT(*) AS members FROM team_member
                WHERE status = 'ACTIVE' AND team_id IN
                <foreach collection="teamIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY team_id
                </script>
                """)
        List<MemberCount> countActive(@Param("teamIds") Collection<Long> teamIds);

        record MemberCount(Long teamId, Long members) {
        }
    }

    @Mapper
    public interface NeedMapper extends BaseMapper<TeamNeed> {
    }

    @Mapper
    public interface ApplicationMapper extends BaseMapper<TeamApplication> {
    }

    /** team_need_skill 是联合主键表，直接写 SQL */
    @Mapper
    public interface NeedSkillMapper {

        @Select("""
                <script>
                SELECT ns.need_id AS needId, s.id AS skillId, s.name, s.category, ns.min_level AS minLevel
                FROM team_need_skill ns JOIN skill s ON s.id = ns.skill_id
                WHERE ns.need_id IN
                <foreach collection="needIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                ORDER BY ns.min_level DESC, s.sort
                </script>
                """)
        List<NeedSkillRow> listByNeeds(@Param("needIds") Collection<Long> needIds);

        @Delete("DELETE FROM team_need_skill WHERE need_id = #{needId}")
        int deleteByNeed(@Param("needId") long needId);

        @Insert("""
                <script>
                INSERT INTO team_need_skill (need_id, skill_id, min_level) VALUES
                <foreach collection="skills" item="s" separator=",">(#{needId}, #{s.skillId}, #{s.minLevel})</foreach>
                </script>
                """)
        int insertAll(@Param("needId") long needId, @Param("skills") List<TeamDtos.NeedSkillInput> skills);

        record NeedSkillRow(Long needId, Long skillId, String name, String category, Integer minLevel) {
        }
    }
}
