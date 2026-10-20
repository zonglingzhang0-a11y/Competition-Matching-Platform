package com.teammatch.match;

import com.teammatch.match.GroupPlanner.Group;
import com.teammatch.match.MatchInputs.Person;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批量撮合的组合挑选。打分用一个假的规则：每人有个"目标档位"，和其余人平均档位差得越多分越低；
 * 档位差 5 以上一票否决；另外 3 号和 4 号互相冲突不能同组。
 */
class GroupPlannerTest {

    private static Person person(long id, int level) {
        return new Person(id, "同学" + id, Map.of("lvl", level), Map.of(), Map.of(), 0, null);
    }

    private static int level(Person p) {
        return (Integer) p.general().get("lvl");
    }

    private static final GroupPlanner.Scorer SCORER = (who, others) -> {
        boolean clash = others.stream().anyMatch(o -> Set.of(who.userId(), o.userId()).equals(Set.of(3L, 4L)));
        double avg = others.stream().mapToInt(GroupPlannerTest::level).average().orElse(level(who));
        double gap = Math.abs(level(who) - avg);
        if (clash || gap >= 5) {
            return MatchResult.vetoed("不合适");
        }
        return new MatchResult(100 - gap * 10, 1, 1, List.of(), List.of("理由"), null, List.of(), null);
    };

    private static List<Long> ids(Group g) {
        return g.others().stream().map(Person::userId).sorted().toList();
    }

    @Test
    void 最合适的组合排第一() {
        Person me = person(1, 5);
        List<Group> groups = GroupPlanner.plan(me,
                List.of(person(2, 5), person(5, 6), person(6, 5), person(7, 2)), 3, SCORER);
        assertThat(ids(groups.getFirst())).containsExactly(2L, 6L);
        assertThat(groups.getFirst().score()).isEqualTo(100.0);
        assertThat(groups.getFirst().forMe().reasons()).containsExactly("理由");
        assertThat(groups).hasSizeLessThanOrEqualTo(3)
                .isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
    }

    @Test
    void 和我一票否决的人不会出现在任何组合里() {
        Person me = person(1, 5);
        List<Group> groups = GroupPlanner.plan(me,
                List.of(person(2, 5), person(5, 6), person(9, 0)), 3, SCORER);
        assertThat(groups).isNotEmpty().allSatisfy(g -> assertThat(ids(g)).doesNotContain(9L));
    }

    @Test
    void 互相冲突的两个人不会在同一组() {
        Person me = person(1, 5);
        List<Group> groups = GroupPlanner.plan(me,
                List.of(person(3, 5), person(4, 5), person(5, 5)), 3, SCORER);
        assertThat(groups).isNotEmpty().allSatisfy(g -> assertThat(ids(g)).hasSize(2))
                .noneMatch(g -> ids(g).containsAll(List.of(3L, 4L)));
    }

    @Test
    void 不会把自己算进候选人() {
        Person me = person(1, 5);
        List<Group> groups = GroupPlanner.plan(me, List.of(me, person(2, 5)), 2, SCORER);
        assertThat(groups).singleElement().satisfies(g -> assertThat(ids(g)).containsExactly(2L));
    }

    @Test
    void 人不够或者人数不对就不给建议() {
        Person me = person(1, 5);
        assertThat(GroupPlanner.plan(me, List.of(person(2, 5)), 3, SCORER)).isEmpty();
        assertThat(GroupPlanner.plan(me, List.of(person(2, 5)), 1, SCORER)).isEmpty();
        assertThat(GroupPlanner.plan(me, List.of(person(2, 5), person(9, 0)), 3, SCORER)).isEmpty();
    }

    @Test
    void 最多给三种() {
        Person me = person(1, 5);
        List<Person> many = List.of(person(2, 5), person(5, 5), person(6, 5), person(7, 5), person(8, 5));
        assertThat(GroupPlanner.plan(me, many, 3, SCORER)).hasSize(3);
    }

    @Test
    void 组合数() {
        assertThat(GroupPlanner.combinations(List.of(1, 2, 3, 4, 5), 2)).hasSize(10)
                .first().isEqualTo(List.of(1, 2));
        assertThat(GroupPlanner.combinations(List.of(1, 2, 3), 3)).containsExactly(List.of(1, 2, 3));
        assertThat(GroupPlanner.combinations(List.of(1, 2), 3)).isEmpty();
    }
}
