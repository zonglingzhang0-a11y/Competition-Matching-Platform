package com.teammatch.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionAdminService;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.crawler.CrawlDtos.ApproveForm;
import com.teammatch.crawler.CrawlDtos.EditionBrief;
import com.teammatch.crawler.CrawlDtos.RecordDetail;
import com.teammatch.crawler.CrawlDtos.RecordRow;
import com.teammatch.crawler.CrawlDtos.SourceForm;
import com.teammatch.crawler.CrawlDtos.SourceRow;
import com.teammatch.crawler.CrawlEntities.CrawlRecord;
import com.teammatch.crawler.CrawlEntities.CrawlRecordMapper;
import com.teammatch.crawler.CrawlEntities.CrawlSource;
import com.teammatch.crawler.CrawlEntities.CrawlSourceMapper;
import com.teammatch.crawler.CrawlEntities.RecordStatus;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知抓取的管理端：待确认的通知（采用 / 忽略）和抓取来源的维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlAdminService {

    /** 列表里看哪些：待确认 / 已处理 */
    public enum Tab { PENDING, DONE }

    private final CrawlRecordMapper recordMapper;
    private final CrawlSourceMapper sourceMapper;
    private final CompetitionMapper competitionMapper;
    private final EditionMapper editionMapper;
    private final SysUserMapper userMapper;
    private final CompetitionAdminService competitionAdminService;
    private final JsonMapper json;
    private final Clock clock;

    public PageResult<RecordRow> records(Tab tab, int page, int size) {
        LambdaQueryWrapper<CrawlRecord> where = new LambdaQueryWrapper<CrawlRecord>()
                .select(CrawlRecord.class, f -> !f.getColumn().equals("raw_text"));
        if (tab == Tab.PENDING) {
            where.in(CrawlRecord::getStatus, RecordStatus.NEW, RecordStatus.EXTRACTED)
                    .orderByDesc(CrawlRecord::getFetchedAt);
        } else {
            where.in(CrawlRecord::getStatus, RecordStatus.APPROVED, RecordStatus.IGNORED);
        }
        where.orderByDesc(CrawlRecord::getId);
        Page<CrawlRecord> p = recordMapper.selectPage(Page.of(page, size), where);
        Function<CrawlRecord, RecordRow> toRow = rowMapper(p.getRecords());
        return PageResult.of(p, toRow);
    }

    public long pendingCount() {
        return recordMapper.pendingCount();
    }

    public RecordDetail detail(long id) {
        CrawlRecord r = requireRecord(id);
        RecordRow row = rowMapper(List.of(r)).apply(r);
        List<EditionBrief> editions = row.competitionId() == null ? List.of()
                : editionMapper.selectList(new LambdaQueryWrapper<CompetitionEdition>()
                        .eq(CompetitionEdition::getCompetitionId, row.competitionId())
                        .orderByDesc(CompetitionEdition::getYear)
                        .orderByDesc(CompetitionEdition::getId))
                .stream().map(EditionBrief::of).toList();
        return new RecordDetail(row, r.getRawText(), editions);
    }

    /** 采用：新建一届或更新已有的一届，连同时间节点。返回届次 id */
    @Transactional
    public long approve(long adminId, long id, ApproveForm form) {
        CrawlRecord r = requireRecord(id);
        CrawlSource s = r.getSourceId() == null ? null : sourceMapper.selectById(r.getSourceId());
        if (s == null) {
            throw new BizException("找不到这条通知的抓取来源，没法确定是哪个比赛");
        }
        if (recordMapper.approve(id, adminId, LocalDateTime.now(clock)) == 0) {
            throw new BizException(409, "这条通知已经采用过了");
        }
        long editionId;
        if (form.editionId() == null) {
            editionId = competitionAdminService.createEdition(s.getCompetitionId(), form.edition(),
                    CompetitionEdition.Source.CRAWLER);
        } else {
            CompetitionEdition e = editionMapper.selectById(form.editionId());
            if (e == null || !Objects.equals(e.getCompetitionId(), s.getCompetitionId())) {
                throw new BizException("只能更新这个比赛自己的届次");
            }
            competitionAdminService.updateEdition(e.getId(), form.edition());
            editionId = e.getId();
        }
        competitionAdminService.replaceNodes(editionId, form.nodes());
        recordMapper.linkEdition(id, editionId);
        return editionId;
    }

    public void ignore(long adminId, long id) {
        requireRecord(id);
        if (recordMapper.ignore(id, adminId, LocalDateTime.now(clock)) == 0) {
            throw new BizException(409, "这条通知已经处理过了");
        }
    }

    // ---------- 抓取来源 ----------

    public List<SourceRow> sources() {
        List<CrawlSource> list = sourceMapper.selectList(new LambdaQueryWrapper<CrawlSource>().orderByAsc(CrawlSource::getId));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, String> competitions = competitionNames(list.stream().map(CrawlSource::getCompetitionId).toList());
        Map<Long, CrawlRecordMapper.SourceCount> counts = recordMapper.countsBySource(list.stream().map(CrawlSource::getId).toList())
                .stream().collect(Collectors.toMap(CrawlRecordMapper.SourceCount::sourceId, Function.identity()));
        return list.stream().map(s -> {
            CrawlRecordMapper.SourceCount c = counts.get(s.getId());
            return new SourceRow(s.getId(), s.getCompetitionId(), competitions.get(s.getCompetitionId()), s.getName(),
                    s.getListUrl(), s.getLinkSelector(), s.getTitleKeywords(), s.getExcludeKeywords(),
                    s.getContentSelector(), Boolean.TRUE.equals(s.getAllowLlm()), Boolean.TRUE.equals(s.getEnabled()),
                    s.getNote(), s.getLastRunAt(), s.getLastResult(),
                    c == null ? 0 : c.pending(), c == null ? 0 : c.total());
        }).toList();
    }

    public long createSource(SourceForm form) {
        CrawlSource s = new CrawlSource();
        apply(s, form);
        sourceMapper.insert(s);
        return s.getId();
    }

    public void updateSource(long id, SourceForm form) {
        CrawlSource s = requireSource(id);
        apply(s, form);
        sourceMapper.updateById(s);
    }

    /** 抓到过通知的来源不能删（通知要留着），只能停用 */
    public void deleteSource(long id) {
        requireSource(id);
        long records = recordMapper.selectCount(new LambdaQueryWrapper<CrawlRecord>().eq(CrawlRecord::getSourceId, id));
        if (records > 0) {
            throw new BizException(409, "这个来源已经抓到过 " + records + " 条通知，不能删除。不想再抓的话可以停用");
        }
        sourceMapper.deleteById(id);
    }

    private void apply(CrawlSource s, SourceForm f) {
        if (competitionMapper.selectById(f.competitionId()) == null) {
            throw new BizException("这个比赛不存在");
        }
        try {
            CrawlHttp.parse(f.listUrl().trim());
        } catch (IOException e) {
            throw new BizException(e.getMessage());
        }
        CrawlService.checkSelector(f.linkSelector(), "通知链接");
        CrawlService.checkSelector(f.contentSelector(), "正文");
        s.setCompetitionId(f.competitionId());
        s.setName(f.name().trim());
        s.setListUrl(f.listUrl().trim());
        s.setLinkSelector(f.linkSelector().trim());
        s.setTitleKeywords(blankToNull(f.titleKeywords()));
        s.setExcludeKeywords(blankToNull(f.excludeKeywords()));
        s.setContentSelector(blankToNull(f.contentSelector()));
        s.setAllowLlm(f.allowLlm());
        s.setEnabled(f.enabled());
        s.setNote(blankToNull(f.note()));
    }

    // ---------- 拼列表行 ----------

    /** 一批通知用到的来源、比赛、届次、审核人一次查出来 */
    private Function<CrawlRecord, RecordRow> rowMapper(List<CrawlRecord> records) {
        Map<Long, CrawlSource> sources = byId(records.stream().map(CrawlRecord::getSourceId).toList(),
                sourceMapper::selectByIds, CrawlSource::getId);
        Map<Long, String> competitions = competitionNames(sources.values().stream().map(CrawlSource::getCompetitionId).toList());
        Map<Long, CompetitionEdition> editions = byId(records.stream().map(CrawlRecord::getEditionId).toList(),
                editionMapper::selectByIds, CompetitionEdition::getId);
        Map<Long, SysUser> reviewers = byId(records.stream().map(CrawlRecord::getReviewedBy).toList(),
                userMapper::selectByIds, SysUser::getId);
        return r -> {
            CrawlSource s = sources.get(r.getSourceId());
            CompetitionEdition e = editions.get(r.getEditionId());
            SysUser reviewer = reviewers.get(r.getReviewedBy());
            return new RecordRow(r.getId(), r.getSourceId(), s == null ? null : s.getName(),
                    s == null ? null : s.getCompetitionId(), s == null ? null : competitions.get(s.getCompetitionId()),
                    r.getTitle(), r.getSourceUrl(), r.getStatus(), r.getNote(), parse(r.getExtracted()),
                    r.getFetchedAt(), r.getEditionId(), e == null ? null : e.getTitle(),
                    reviewer == null ? null : reviewer.getNickname(), r.getReviewedAt());
        };
    }

    private Extracted parse(String extracted) {
        if (extracted == null) {
            return null;
        }
        try {
            return json.readValue(extracted, Extracted.class);
        } catch (JacksonException e) {
            log.warn("抽取结果解析不了：{}", e.getMessage());
            return null;
        }
    }

    private Map<Long, String> competitionNames(List<Long> ids) {
        return byId(ids, competitionMapper::selectByIds, Competition::getId).values().stream()
                .collect(Collectors.toMap(Competition::getId, Competition::getName));
    }

    /** 返回 HashMap：通知的届次、审核人可能为空，要能拿 null 去 get */
    private static <T> Map<Long, T> byId(List<Long> ids, Function<Collection<Long>, List<T>> query, Function<T, Long> id) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return new HashMap<>();
        }
        return query.apply(distinct).stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private CrawlRecord requireRecord(long id) {
        CrawlRecord r = recordMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "这条通知不存在");
        }
        return r;
    }

    private CrawlSource requireSource(long id) {
        CrawlSource s = sourceMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "这个抓取来源不存在");
        }
        return s;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
