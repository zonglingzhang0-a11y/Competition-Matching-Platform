package com.teammatch.school;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 管理员维护学校和教育邮箱后缀。后缀收录以后，这所学校的学生用教育邮箱就能自动认证。
 */
@Service
@RequiredArgsConstructor
public class SchoolAdminService {

    /** 只收 .edu.cn 结尾的域名，比如 stu.xxx.edu.cn */
    private static final Pattern EDU_DOMAIN = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.edu\\.cn$");

    private final SchoolMapper schoolMapper;
    private final SchoolEmailDomainMapper domainMapper;
    private final SchoolAdminMapper adminMapper;

    public record SchoolRow(Long id, String name, String province, String city, List<DomainRow> domains,
                            long verifiedUsers) {
    }

    public record DomainRow(Long id, String domain) {
    }

    public record SchoolForm(
            @NotBlank(message = "请填写学校全称") @Size(max = 64, message = "学校名称最多 64 个字") String name,
            @Size(max = 16, message = "省份最多 16 个字") String province,
            @Size(max = 32, message = "城市最多 32 个字") String city) {
    }

    public record DomainForm(@NotBlank(message = "请填写邮箱后缀") @Size(max = 64, message = "后缀太长了") String domain) {
    }

    /** 按学校名或邮箱后缀搜 */
    public PageResult<SchoolRow> list(String keyword, int page, int size) {
        LambdaQueryWrapper<School> where = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            where.and(w -> w.like(School::getName, kw).or().exists(
                    "SELECT 1 FROM school_email_domain d WHERE d.school_id = school.id AND d.domain LIKE {0}",
                    "%" + kw.toLowerCase(Locale.ROOT) + "%"));
        }
        where.orderByAsc(School::getId);
        Page<School> p = schoolMapper.selectPage(Page.of(page, size), where);
        List<Long> ids = p.getRecords().stream().map(School::getId).toList();
        Map<Long, List<DomainRow>> domains = ids.isEmpty() ? Map.of()
                : domainMapper.selectList(new LambdaQueryWrapper<SchoolEmailDomain>()
                        .in(SchoolEmailDomain::getSchoolId, ids).orderByAsc(SchoolEmailDomain::getDomain))
                .stream().collect(Collectors.groupingBy(SchoolEmailDomain::getSchoolId,
                        Collectors.mapping(d -> new DomainRow(d.getId(), d.getDomain()), Collectors.toList())));
        Map<Long, Long> verified = ids.isEmpty() ? Map.of()
                : adminMapper.verifiedCounts(ids).stream()
                .collect(Collectors.toMap(SchoolAdminMapper.CountRow::schoolId, SchoolAdminMapper.CountRow::cnt));
        return PageResult.of(p, s -> new SchoolRow(s.getId(), s.getName(), s.getProvince(), s.getCity(),
                domains.getOrDefault(s.getId(), List.of()), verified.getOrDefault(s.getId(), 0L)));
    }

    public School create(SchoolForm form) {
        School s = new School();
        s.setName(form.name().trim());
        s.setProvince(blankToNull(form.province()));
        s.setCity(blankToNull(form.city()));
        try {
            schoolMapper.insert(s);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "已经有「" + s.getName() + "」了，直接选它就行");
        }
        return s;
    }

    public School require(long id) {
        School s = schoolMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "这所学校不存在");
        }
        return s;
    }

    /**
     * 给学校加一个邮箱后缀。已经属于这所学校就什么都不做，属于别的学校就报错。
     */
    public void ensureDomain(long schoolId, String domain) {
        String d = normalizeDomain(domain);
        SchoolEmailDomain existing = domainMapper.selectOne(new LambdaQueryWrapper<SchoolEmailDomain>()
                .eq(SchoolEmailDomain::getDomain, d));
        if (existing != null) {
            if (existing.getSchoolId() == schoolId) {
                return;
            }
            throw domainTaken(d, existing.getSchoolId());
        }
        SchoolEmailDomain row = new SchoolEmailDomain();
        row.setSchoolId(schoolId);
        row.setDomain(d);
        try {
            domainMapper.insert(row);
        } catch (DuplicateKeyException e) {
            SchoolEmailDomain other = domainMapper.selectOne(new LambdaQueryWrapper<SchoolEmailDomain>()
                    .eq(SchoolEmailDomain::getDomain, d));
            if (other == null || other.getSchoolId() != schoolId) {
                throw domainTaken(d, other == null ? null : other.getSchoolId());
            }
        }
    }

    public void removeDomain(long domainId) {
        if (domainMapper.deleteById(domainId) == 0) {
            throw new BizException(404, "这个后缀不存在");
        }
    }

    /** 去掉空格、转小写，并检查是不是 .edu.cn 域名 */
    public static String normalizeDomain(String domain) {
        String d = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("@")) {
            d = d.substring(1);
        }
        if (!EDU_DOMAIN.matcher(d).matches()) {
            throw new BizException("邮箱后缀要是 .edu.cn 结尾的域名，比如 stu.xxx.edu.cn");
        }
        return d;
    }

    private BizException domainTaken(String domain, Long ownerId) {
        School owner = ownerId == null ? null : schoolMapper.selectById(ownerId);
        return new BizException(409, "后缀 " + domain + " 已经属于「" + (owner == null ? "其他学校" : owner.getName()) + "」");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Mapper
    public interface SchoolAdminMapper {

        @Select("""
                <script>
                SELECT school_id AS schoolId, COUNT(*) AS cnt FROM sys_user
                WHERE verify_status = 'VERIFIED'
                  AND school_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                GROUP BY school_id
                </script>
                """)
        List<CountRow> verifiedCounts(@Param("ids") Collection<Long> ids);

        record CountRow(Long schoolId, Long cnt) {
        }
    }
}
