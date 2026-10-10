package com.teammatch.user;

import com.teammatch.profile.ProfileDtos.SkillLevel;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批量查用户的简要信息（昵称、学校、专业、年级、技能），给队伍、申请、推荐这些列表用，避免一行一查。
 */
@Component
@RequiredArgsConstructor
public class UserBriefs {

    private final BriefMapper mapper;

    /** 给别人看的简要信息，不含联系方式 */
    public record UserBrief(Long id, String nickname, Long schoolId, String schoolName, String major,
                            Integer enrollYear, List<SkillLevel> skills) {
    }

    /** 联系方式，只给队友看 */
    public record Contact(String realName, String wechat, String qq) {
    }

    public Map<Long, UserBrief> load(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SkillLevel>> skills = mapper.skills(userIds).stream().collect(Collectors.groupingBy(
                SkillRow::userId,
                Collectors.mapping(r -> new SkillLevel(r.skillId(), r.name(), r.category(), r.level()), Collectors.toList())));
        return mapper.basics(userIds).stream()
                .map(b -> new UserBrief(b.id(), b.nickname(), b.schoolId(), b.schoolName(), b.major(), b.enrollYear(),
                        skills.getOrDefault(b.id(), List.of())))
                .collect(Collectors.toMap(UserBrief::id, Function.identity()));
    }

    public UserBrief loadOne(long userId) {
        return load(List.of(userId)).get(userId);
    }

    public Map<Long, Contact> contacts(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return mapper.contacts(userIds).stream().collect(Collectors.toMap(ContactRow::userId,
                c -> new Contact(c.realName(), c.contactWechat(), c.contactQq())));
    }

    public record BasicRow(Long id, String nickname, Long schoolId, String schoolName, String major, Integer enrollYear) {
    }

    public record SkillRow(Long userId, Long skillId, String name, String category, Integer level) {
    }

    public record ContactRow(Long userId, String realName, String contactWechat, String contactQq) {
    }

    @Mapper
    public interface BriefMapper {

        @Select("""
                <script>
                SELECT u.id, u.nickname, u.school_id AS schoolId, s.name AS schoolName, p.major, p.enroll_year AS enrollYear
                FROM sys_user u
                LEFT JOIN school s ON s.id = u.school_id
                LEFT JOIN user_profile p ON p.user_id = u.id
                WHERE u.id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                </script>
                """)
        List<BasicRow> basics(@Param("ids") Collection<Long> ids);

        @Select("""
                <script>
                SELECT us.user_id AS userId, s.id AS skillId, s.name, s.category, us.level
                FROM user_skill us JOIN skill s ON s.id = us.skill_id
                WHERE us.user_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                ORDER BY us.level DESC, s.sort
                </script>
                """)
        List<SkillRow> skills(@Param("ids") Collection<Long> ids);

        @Select("""
                <script>
                SELECT user_id AS userId, real_name AS realName, contact_wechat AS contactWechat, contact_qq AS contactQq
                FROM user_profile WHERE user_id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                </script>
                """)
        List<ContactRow> contacts(@Param("ids") Collection<Long> ids);
    }
}
