package com.teammatch.competition;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 比赛库的几个 Mapper。单表增删改查交给 MyBatis-Plus，联合主键和统计这类写 SQL。
 */
public final class CompetitionMappers {

    private CompetitionMappers() {
    }

    @Mapper
    public interface CompetitionMapper extends BaseMapper<Competition> {
    }

    @Mapper
    public interface EditionMapper extends BaseMapper<CompetitionEdition> {
    }

    @Mapper
    public interface NodeMapper extends BaseMapper<CompetitionNode> {
    }

    @Mapper
    public interface RoleTemplateItemMapper extends BaseMapper<RoleTemplateItem> {
    }

    /** competition_follow 是联合主键表，直接写 SQL */
    @Mapper
    public interface FollowMapper {

        @Insert("INSERT IGNORE INTO competition_follow (user_id, edition_id) VALUES (#{userId}, #{editionId})")
        int follow(@Param("userId") long userId, @Param("editionId") long editionId);

        @Delete("DELETE FROM competition_follow WHERE user_id = #{userId} AND edition_id = #{editionId}")
        int unfollow(@Param("userId") long userId, @Param("editionId") long editionId);

        @Select("""
                <script>
                SELECT edition_id FROM competition_follow
                WHERE user_id = #{userId} AND edition_id IN
                <foreach collection="editionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                </script>
                """)
        List<Long> followedAmong(@Param("userId") long userId, @Param("editionIds") Collection<Long> editionIds);

        @Select("""
                <script>
                SELECT edition_id AS editionId, COUNT(*) AS followers FROM competition_follow
                WHERE edition_id IN
                <foreach collection="editionIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY edition_id
                </script>
                """)
        List<FollowerCount> countFollowers(@Param("editionIds") Collection<Long> editionIds);

        record FollowerCount(Long editionId, Long followers) {
        }
    }

    /** 管理端要的统计：每届几支队、几个节点；分工模板 */
    @Mapper
    public interface AdminStatMapper {

        @Select("""
                <script>
                SELECT edition_id AS editionId, COUNT(*) AS cnt FROM team
                WHERE deleted = 0 AND status &lt;&gt; 'DISBANDED' AND edition_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY edition_id
                </script>
                """)
        List<EditionCount> teamCounts(@Param("ids") Collection<Long> editionIds);

        @Select("""
                <script>
                SELECT edition_id AS editionId, COUNT(*) AS cnt FROM competition_node
                WHERE edition_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY edition_id
                </script>
                """)
        List<EditionCount> nodeCounts(@Param("ids") Collection<Long> editionIds);

        @Select("""
                SELECT t.id AS templateId, t.name AS templateName, i.name AS roleName
                FROM role_template t LEFT JOIN role_template_item i ON i.template_id = t.id
                ORDER BY t.id, i.sort, i.id
                """)
        List<RoleRow> roleTemplates();

        record EditionCount(Long editionId, Long cnt) {
        }

        record RoleRow(Long templateId, String templateName, String roleName) {
        }
    }
}
