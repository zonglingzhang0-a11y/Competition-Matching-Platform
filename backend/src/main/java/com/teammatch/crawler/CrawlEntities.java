package com.teammatch.crawler;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 爬虫的抓取来源和抓到的通知。
 */
public final class CrawlEntities {

    private CrawlEntities() {
    }

    @Data
    @TableName("crawl_source")
    public static class CrawlSource {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long competitionId;
        private String name;
        private String listUrl;
        private String linkSelector;
        /** 逗号分隔，标题至少包含一个才抓；空表示都抓 */
        private String titleKeywords;
        /** 逗号分隔，标题包含任何一个就跳过 */
        private String excludeKeywords;
        /** 空表示取整页文字 */
        private String contentSelector;
        private Boolean allowLlm;
        private Boolean enabled;
        private String note;
        private LocalDateTime lastRunAt;
        private String lastResult;
        private LocalDateTime createdAt;
    }

    @Data
    @TableName("crawl_record")
    public static class CrawlRecord {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long sourceId;
        private String sourceUrl;
        private String title;
        private String rawText;
        private String contentHash;
        /** {@link Extracted} 的 JSON */
        private String extracted;
        private RecordStatus status;
        private String note;
        private Long editionId;
        private LocalDateTime fetchedAt;
        private Long reviewedBy;
        private LocalDateTime reviewedAt;
    }

    /** NEW 没抽出字段 / EXTRACTED 抽出了字段，这两种等管理员确认；APPROVED 已采用 / IGNORED 已忽略 */
    public enum RecordStatus { NEW, EXTRACTED, APPROVED, IGNORED }

    @Mapper
    public interface CrawlSourceMapper extends BaseMapper<CrawlSource> {

        @Update("UPDATE crawl_source SET last_run_at = #{at}, last_result = #{result} WHERE id = #{id}")
        int finished(@Param("id") long id, @Param("at") LocalDateTime at, @Param("result") String result);

        /** 网站在 robots.txt 里拒绝了 AI 爬虫，以后只用规则抽取 */
        @Update("UPDATE crawl_source SET allow_llm = 0, note = #{note} WHERE id = #{id} AND allow_llm = 1")
        int disallowLlm(@Param("id") long id, @Param("note") String note);
    }

    @Mapper
    public interface CrawlRecordMapper extends BaseMapper<CrawlRecord> {

        /** 这个链接抓过了就不再打开（通知发出后一般不改） */
        @Select("SELECT COUNT(*) > 0 FROM crawl_record WHERE source_url = #{url}")
        boolean urlSeen(@Param("url") String url);

        @Select("SELECT COUNT(*) > 0 FROM crawl_record WHERE content_hash = #{hash}")
        boolean hashSeen(@Param("hash") String hash);

        /** 采用：除了已经采用的，忽略了的也能改成采用（自动忽略可能误判） */
        @Update("UPDATE crawl_record SET status = 'APPROVED', reviewed_by = #{by}, reviewed_at = #{at} "
                + "WHERE id = #{id} AND status <> 'APPROVED'")
        int approve(@Param("id") long id, @Param("by") long by, @Param("at") LocalDateTime at);

        @Update("UPDATE crawl_record SET edition_id = #{editionId} WHERE id = #{id}")
        int linkEdition(@Param("id") long id, @Param("editionId") long editionId);

        @Update("UPDATE crawl_record SET status = 'IGNORED', note = NULL, reviewed_by = #{by}, reviewed_at = #{at} "
                + "WHERE id = #{id} AND status IN ('NEW', 'EXTRACTED')")
        int ignore(@Param("id") long id, @Param("by") long by, @Param("at") LocalDateTime at);

        @Select("SELECT COUNT(*) FROM crawl_record WHERE status IN ('NEW', 'EXTRACTED')")
        long pendingCount();

        @Select("<script>SELECT source_id AS sourceId, "
                + "SUM(status IN ('NEW', 'EXTRACTED')) AS pending, COUNT(*) AS total "
                + "FROM crawl_record WHERE source_id IN "
                + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
                + "GROUP BY source_id</script>")
        List<SourceCount> countsBySource(@Param("ids") List<Long> ids);

        record SourceCount(Long sourceId, Long pending, Long total) {
        }
    }
}
