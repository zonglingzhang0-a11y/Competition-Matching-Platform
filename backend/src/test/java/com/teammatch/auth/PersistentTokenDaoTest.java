package com.teammatch.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.json.SaJsonTemplateForJackson3;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentTokenDaoTest {

    private static final String TOKEN_KEY = "Authorization:login:token:abc";
    private static final String SESSION_KEY = "Authorization:login:session:7";

    private final FakeTable table = new FakeTable();

    @BeforeAll
    static void useJackson() {
        // 和运行时一样用 Jackson 把会话转成 JSON（Spring 启动时由 sa-token-jackson3 插件装上）
        SaManager.setSaJsonTemplate(new SaJsonTemplateForJackson3());
    }

    /** 模拟一次后端启动：内存是空的，库还是那个库 */
    private PersistentTokenDao start() {
        PersistentTokenDao dao = new PersistentTokenDao(table, TransactionOperations.withoutTransaction());
        dao.afterSingletonsInstantiated();
        return dao;
    }

    @Test
    void restartKeepsTokensAndSessions() {
        PersistentTokenDao before = start();
        before.set(TOKEN_KEY, "7", 3600);
        before.set("forever", "x", SaTokenDao.NEVER_EXPIRE);
        SaSession session = new SaSession(SESSION_KEY);
        session.setType("Account-Session");
        session.setLoginId(7L);
        session.getDataMap().put("nickname", "陈一舟");
        session.addTerminal(new SaTerminalInfo(1, "abc", "PC", null));
        before.setSession(session, 3600);

        PersistentTokenDao after = start();
        assertThat(after.get(TOKEN_KEY)).isEqualTo("7");
        assertThat(after.getTimeout(TOKEN_KEY)).isBetween(3590L, 3600L);
        assertThat(after.getTimeout("forever")).isEqualTo(SaTokenDao.NEVER_EXPIRE);

        SaSession restored = after.getSession(SESSION_KEY);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isEqualTo(SESSION_KEY);
        assertThat(restored.getType()).isEqualTo("Account-Session");
        assertThat(String.valueOf(restored.getLoginId())).isEqualTo("7");
        assertThat(restored.getCreateTime()).isEqualTo(session.getCreateTime());
        assertThat(restored.getDataMap()).containsEntry("nickname", "陈一舟");
        // 停用账号时按这个列表踢掉所有登录，重启后也得在
        assertThat(restored.getTerminalList()).extracting(SaTerminalInfo::getTokenValue).containsExactly("abc");
    }

    @Test
    void changesAndDeletesReachTheTable() {
        PersistentTokenDao dao = start();
        dao.set("a", "1", 3600);
        dao.update("a", "2");
        assertThat(table.rows.get("a").storeValue()).isEqualTo("2");
        assertThat(secondsLeft("a")).as("改值不改有效期").isBetween(3590L, 3600L);

        dao.updateTimeout("a", 60);
        assertThat(secondsLeft("a")).isBetween(55L, 60L);

        dao.updateTimeout("a", SaTokenDao.NEVER_EXPIRE);
        assertThat(table.rows.get("a").expireAt()).isNull();

        dao.delete("a");
        assertThat(table.rows).doesNotContainKey("a");

        dao.set("zero", "x", 0);
        assertThat(table.rows).as("有效期 0 按 Sa-Token 的约定不存").doesNotContainKey("zero");
    }

    @Test
    void expiredAndUnreadableRowsAreSkippedOnStart() {
        table.rows.put("old", new LoginStoreMapper.Row("old", PersistentTokenDao.STRING, "x", LocalDateTime.now().minusMinutes(1)));
        table.rows.put("bad", new LoginStoreMapper.Row("bad", PersistentTokenDao.OBJECT, "{不是 JSON", null));
        table.rows.put("ok", new LoginStoreMapper.Row("ok", PersistentTokenDao.STRING, "y", LocalDateTime.now().plusHours(1)));

        PersistentTokenDao dao = start();
        assertThat(dao.get("old")).isNull();
        assertThat(table.rows).as("启动时顺手清掉过期的").doesNotContainKey("old");
        assertThat(dao.getObject("bad")).isNull();
        assertThat(dao.get("ok")).isEqualTo("y");
    }

    @Test
    void withoutTheTableItWorksInMemoryOnly() {
        table.missing = true;
        PersistentTokenDao dao = start();
        dao.set("a", "1", 3600);
        assertThat(dao.get("a")).isEqualTo("1");
        assertThat(table.writes).isEmpty();
    }

    private long secondsLeft(String key) {
        return Duration.between(LocalDateTime.now(), table.rows.get(key).expireAt()).toSeconds();
    }

    /** 内存里的一张 login_store 表 */
    private static class FakeTable implements LoginStoreMapper {

        final Map<String, Row> rows = new LinkedHashMap<>();
        final List<String> writes = new ArrayList<>();
        boolean missing;

        private void check() {
            if (missing) {
                throw new BadSqlGrammarException("login_store", "SELECT ...",
                        new SQLException("Table 'team_match.login_store' doesn't exist"));
            }
        }

        @Override
        public List<Row> all() {
            check();
            return new ArrayList<>(rows.values());
        }

        @Override
        public void save(String key, String type, String value, LocalDateTime expireAt) {
            check();
            writes.add(key);
            rows.put(key, new Row(key, type, value, expireAt));
        }

        @Override
        public void delete(String key) {
            check();
            writes.add(key);
            rows.remove(key);
        }

        @Override
        public int deleteExpired(LocalDateTime now) {
            check();
            int before = rows.size();
            rows.values().removeIf(r -> r.expireAt() != null && !r.expireAt().isAfter(now));
            return before - rows.size();
        }
    }
}
