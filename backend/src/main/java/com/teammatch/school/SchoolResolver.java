package com.teammatch.school;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 根据教育邮箱找学校。
 * <p>
 * 学校的邮箱常常有好几层子域名（mail.stu.xxx.edu.cn、stu.xxx.edu.cn、xxx.edu.cn），
 * 所以从完整域名开始，一层层去掉最左边的部分去查，取最长的那个匹配。
 */
@Component
@RequiredArgsConstructor
public class SchoolResolver {

    private static final String EDU_SUFFIX = ".edu.cn";

    private final SchoolEmailDomainMapper domainMapper;
    private final SchoolMapper schoolMapper;

    public static boolean isEduEmail(String email) {
        String domain = domainOf(email);
        return domain != null && domain.endsWith(EDU_SUFFIX) && domain.length() > EDU_SUFFIX.length();
    }

    /** a@mail.stu.xxx.edu.cn → [mail.stu.xxx.edu.cn, stu.xxx.edu.cn, xxx.edu.cn] */
    public static List<String> candidateDomains(String email) {
        List<String> result = new ArrayList<>();
        if (!isEduEmail(email)) {
            return result;
        }
        String domain = domainOf(email);
        while (domain.endsWith(EDU_SUFFIX) && domain.length() > EDU_SUFFIX.length()) {
            result.add(domain);
            int dot = domain.indexOf('.');
            domain = domain.substring(dot + 1);
        }
        return result;
    }

    public Optional<School> resolve(String email) {
        List<String> candidates = candidateDomains(email);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return domainMapper.selectList(new LambdaQueryWrapper<SchoolEmailDomain>()
                        .in(SchoolEmailDomain::getDomain, candidates))
                .stream()
                .max(Comparator.comparingInt(d -> d.getDomain().length()))
                .map(d -> schoolMapper.selectById(d.getSchoolId()));
    }

    private static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        return at < 0 ? null : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }
}
