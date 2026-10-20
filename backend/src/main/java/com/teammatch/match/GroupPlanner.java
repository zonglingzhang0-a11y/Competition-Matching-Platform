package com.teammatch.match;

import com.teammatch.match.MatchInputs.Person;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 批量撮合（只给建议）：给一个还没组队的人，从同样还没组队的同学里挑出几种合适的组合。
 * <p>
 * 系统不替人分组，只列出建议，组不组由学生自己决定。做法：
 * <ol>
 *   <li>先算每个候选人和"我"两两合不合适（双方互相打分取平均），任何一方被一票否决的直接排除；</li>
 *   <li>取最合适的几个人，把所有凑够人数的组合都试一遍：组合里每个人都和"其余的人"打一次分，取平均；</li>
 *   <li>组合里有任何一对一票否决（撞期、目标差太远、被暂停推荐）就不要；</li>
 *   <li>按平均分从高到低取前几种。</li>
 * </ol>
 * 打分沿用匹配引擎（把"其余的人"当成一支队伍），所以规则、权重、一票否决和推荐理由都和推荐队伍一致。
 * 不查数据库、不依赖 Spring，方便单元测试。
 */
public final class GroupPlanner {

    /** 先挑出两两最合适的这么多人，再在他们里面凑组合（8 人凑 3 人组是 28 种，凑 5 人组是 70 种） */
    static final int PARTNERS = 8;
    /** 最多给几种组合 */
    static final int SUGGESTIONS = 3;

    private GroupPlanner() {
    }

    /** 给 who 打分：others 是组合里的其他人 */
    @FunctionalInterface
    public interface Scorer {
        MatchResult score(Person who, List<Person> others);
    }

    /**
     * 一种建议的组合。
     *
     * @param others 除了"我"以外的人
     * @param score  组合里每个人和其余人打分的平均
     * @param forMe  "我"和其余人的打分，推荐理由、要先聊聊的点从这里取
     */
    public record Group(List<Person> others, double score, MatchResult forMe) {
    }

    /**
     * @param size 组合的总人数（含"我"），至少 2
     */
    public static List<Group> plan(Person me, List<Person> candidates, int size, Scorer scorer) {
        if (size < 2 || candidates.size() < size - 1) {
            return List.of();
        }
        record Pair(Person person, double score) {
        }
        List<Person> partners = candidates.stream()
                .filter(c -> c.userId() != me.userId())
                .map(c -> {
                    MatchResult a = scorer.score(me, List.of(c));
                    MatchResult b = scorer.score(c, List.of(me));
                    return a.veto() != null || b.veto() != null ? null : new Pair(c, (a.score() + b.score()) / 2);
                })
                .filter(p -> p != null)
                .sorted(Comparator.comparingDouble(Pair::score).reversed()
                        .thenComparingLong(p -> p.person().userId()))
                .limit(PARTNERS)
                .map(Pair::person)
                .toList();

        List<Group> groups = new ArrayList<>();
        for (List<Person> others : combinations(partners, size - 1)) {
            List<Person> all = new ArrayList<>(others);
            all.add(me);
            double sum = 0;
            MatchResult forMe = null;
            boolean vetoed = false;
            for (Person p : all) {
                List<Person> rest = all.stream().filter(x -> x.userId() != p.userId()).toList();
                MatchResult r = scorer.score(p, rest);
                if (r.veto() != null) {
                    vetoed = true;
                    break;
                }
                sum += r.score();
                if (p.userId() == me.userId()) {
                    forMe = r;
                }
            }
            if (!vetoed) {
                groups.add(new Group(others, Math.round(sum / all.size() * 10) / 10.0, forMe));
            }
        }
        return groups.stream()
                .sorted(Comparator.comparingDouble(Group::score).reversed()
                        .thenComparing(g -> g.others().stream().map(p -> String.valueOf(p.userId()))
                                .collect(Collectors.joining(","))))
                .limit(SUGGESTIONS)
                .toList();
    }

    /** 从 list 里取 k 个的所有组合，保持原来的先后顺序 */
    static <T> List<List<T>> combinations(List<T> list, int k) {
        List<List<T>> out = new ArrayList<>();
        if (k <= 0 || k > list.size()) {
            return out;
        }
        int[] idx = IntStream.range(0, k).toArray();
        while (true) {
            List<T> combo = new ArrayList<>(k);
            for (int i : idx) {
                combo.add(list.get(i));
            }
            out.add(combo);
            int i = k - 1;
            while (i >= 0 && idx[i] == list.size() - k + i) {
                i--;
            }
            if (i < 0) {
                return out;
            }
            idx[i]++;
            for (int j = i + 1; j < k; j++) {
                idx[j] = idx[j - 1] + 1;
            }
        }
    }
}
