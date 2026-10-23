package com.teammatch.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ai_analysis 表的读写。只有一行一条（kind + subjectId 唯一），重新分析时更新。
 */
@Mapper
public interface AiAnalysisMapper extends BaseMapper<AiAnalysis> {
}
