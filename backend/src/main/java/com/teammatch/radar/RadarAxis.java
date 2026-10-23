package com.teammatch.radar;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 雷达图的六个轴：把技能字典里的技能归到"竞赛里常见的六类能力"上，每个轴下面再列出具体技能。
 * 一个人画出来的形状就是他的能力特点；一支队伍的招募需求画出来，就是"这支队要什么样的人"。
 *
 * <p>归类顺序：先看技能名（同一个类别里也有偏数据、偏硬件的技能），再看技能类别。
 * 几个名字里带"设计"但其实是硬件的技能单独列出来（{@link #BY_NAME}）。
 * 技能字典是管理员维护的，以后加了新技能，只要名字或类别能认出来就会自动归到某个轴，
 * 认不出来按编程开发算。
 */
public enum RadarAxis {

    CODE("CODE", "编程开发", "写代码、搭系统"),
    MODEL("MODEL", "建模与算法", "数学建模、算法设计"),
    DATA("DATA", "数据与调研", "找数据、做分析"),
    DESIGN("DESIGN", "设计表达", "界面、视觉、演示制作"),
    WRITE("WRITE", "写作答辩", "论文、计划书、路演"),
    HARDWARE("HARDWARE", "硬件工程", "电路、PCB、嵌入式");

    /** 名字里带这些词的技能归到对应轴，优先于技能类别 */
    private static final List<String> CODE_WORDS = List.of("数据库");
    private static final List<String> DATA_WORDS = List.of("数据", "统计", "调研", "调查", "竞品");
    private static final List<String> MODEL_WORDS = List.of("建模", "算法", "机器学习", "深度学习", "运筹", "matlab");
    private static final List<String> DESIGN_WORDS = List.of("设计", "视频", "3d", "ppt", "视觉", "剪辑");
    private static final List<String> WRITE_WORDS = List.of("论文", "写作", "答辩", "计划书", "排版", "latex", "文案");
    private static final List<String> HARDWARE_WORDS = List.of("电路", "硬件", "pcb", "嵌入式", "单片机", "fpga", "焊接");

    /** 名字里的词会归错轴、或者两个轴都说得通的技能，直接指定 */
    private static final Map<String, RadarAxis> BY_NAME = Map.of(
            "数据库/SQL", CODE,
            "机械结构设计", HARDWARE,
            "电源设计", HARDWARE,
            "3D 打印", HARDWARE,
            "3D 建模", DESIGN,
            "PPT 制作", DESIGN,
            "计算机视觉", MODEL,
            "数据分析", DATA,
            "统计分析", DATA);

    private final String key;
    private final String label;
    private final String hint;

    RadarAxis(String key, String label, String hint) {
        this.key = key;
        this.label = label;
        this.hint = hint;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    /** 按名字和类别判断一项技能属于哪个轴 */
    public static RadarAxis of(String skillName, String category) {
        String name = lower(skillName);
        RadarAxis pinned = BY_NAME.get(skillName == null ? "" : skillName.trim());
        if (pinned != null) {
            return pinned;
        }
        if (containsAny(name, CODE_WORDS)) {
            return CODE;
        }
        if (containsAny(name, DATA_WORDS)) {
            return DATA;
        }
        // 硬件排在设计前面："电路设计""机械结构设计"是硬件，不是设计
        if (containsAny(name, HARDWARE_WORDS)) {
            return HARDWARE;
        }
        // 设计排在建模前面："3D 建模"是设计，不是数学建模
        if (containsAny(name, DESIGN_WORDS)) {
            return DESIGN;
        }
        if (containsAny(name, WRITE_WORDS)) {
            return WRITE;
        }
        if (containsAny(name, MODEL_WORDS)) {
            return MODEL;
        }
        RadarAxis byCategory = switch (lower(category)) {
            case "编程开发" -> CODE;
            case "算法与数据" -> MODEL;
            case "设计" -> DESIGN;
            case "写作表达" -> WRITE;
            case "硬件" -> HARDWARE;
            default -> null;
        };
        return byCategory == null ? CODE : byCategory;
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }
}
