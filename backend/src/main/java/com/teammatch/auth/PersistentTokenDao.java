package com.teammatch.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sa-Token 的数据（登录凭证、账号会话）存在哪：平时照旧放内存，每次改动同时存一份到 login_store 表，
 * 后端启动时再从表里读回内存。这样重启后端，大家不用重新登录。
 * <p>
 * 只有登录、退出、停用账号这些操作会写库；每个请求校验登录只读内存，不多查数据库。
 * 旧库还没建这张表时退回纯内存，和以前一样（重启后要重新登录），日志里会提示跑升级脚本。
 */
@Slf4j
@Component
public class PersistentTokenDao extends SaTokenDaoDefaultImpl implements SmartInitializingSingleton {

    static final String STRING = "S";
    static final String OBJECT = "O";

    private final LoginStoreMapper mapper;
    /** 写库不跟着外面的业务事务：内存已经改了，库里也马上改，业务回滚也不撤销，两边才对得上 */
    private final TransactionOperations outsideTransaction;
    private volatile boolean persistent = true;

    @Autowired
    public PersistentTokenDao(LoginStoreMapper mapper, PlatformTransactionManager transactionManager) {
        this(mapper, notSupported(transactionManager));
    }

    PersistentTokenDao(LoginStoreMapper mapper, TransactionOperations outsideTransaction) {
        this.mapper = mapper;
        this.outsideTransaction = outsideTransaction;
    }

    private static TransactionOperations notSupported(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return template;
    }

    /** 所有 Bean 建好、开始接请求之前，把库里还没过期的登录状态读回内存 */
    @Override
    public void afterSingletonsInstantiated() {
        LocalDateTime now = LocalDateTime.now();
        List<LoginStoreMapper.Row> rows;
        try {
            mapper.deleteExpired(now);
            rows = mapper.all();
        } catch (DataAccessException e) {
            persistent = false;
            log.warn("读不了 login_store 表，登录状态只存在内存里，重启后端后要重新登录。"
                    + "旧库请跑一次 sql/upgrade/2026-10-login-store.sql。原因：{}", e.getMostSpecificCause().getMessage());
            return;
        }
        int restored = 0;
        int broken = 0;
        for (LoginStoreMapper.Row row : rows) {
            long timeout = row.expireAt() == null ? NEVER_EXPIRE : Duration.between(now, row.expireAt()).toSeconds();
            if (timeout != NEVER_EXPIRE && timeout <= 0) {
                continue;
            }
            try {
                Object value = STRING.equals(row.valueType())
                        ? row.storeValue()
                        : SaManager.getSaSerializerTemplate().stringToObject(row.storeValue());
                super.setObject(row.storeKey(), value, timeout);
                restored++;
            } catch (RuntimeException e) {
                // 键里带着登录凭证，不往日志里写
                broken++;
            }
        }
        log.info("从数据库恢复了 {} 条登录状态{}", restored, broken > 0 ? "，" + broken + " 条读不回来已跳过" : "");
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        super.setObject(key, object, timeout);
        mirror(key);
    }

    @Override
    public void updateObject(String key, Object object) {
        super.updateObject(key, object);
        mirror(key);
    }

    @Override
    public void deleteObject(String key) {
        super.deleteObject(key);
        mirror(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        super.updateObjectTimeout(key, timeout);
        mirror(key);
    }

    /**
     * 把内存里这个键现在的样子写进库：还在就存（连同剩下的有效期），没了就删。
     * 每次都照内存的最新状态写、并且一次只写一个，两个请求同时改也不会把旧值写回去。
     */
    private synchronized void mirror(String key) {
        if (!persistent) {
            return;
        }
        Object value = super.getObject(key);
        long timeout = super.getObjectTimeout(key);
        try {
            outsideTransaction.executeWithoutResult(status -> {
                if (value == null || timeout == NOT_VALUE_EXPIRE) {
                    mapper.delete(key);
                    return;
                }
                LocalDateTime expireAt = timeout == NEVER_EXPIRE ? null : LocalDateTime.now().plusSeconds(timeout);
                if (value instanceof String s) {
                    mapper.save(key, STRING, s, expireAt);
                } else {
                    mapper.save(key, OBJECT, SaManager.getSaSerializerTemplate().objectToString(value), expireAt);
                }
            });
        } catch (RuntimeException e) {
            log.warn("登录状态没存进数据库（这次登录照常有效，只是重启后端后可能要重新登录）：{}",
                    NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        }
    }

    /** 过期的登录状态内存里会自己清掉，库里的每天清一次 */
    @Scheduled(cron = "0 20 4 * * *")
    public void purgeExpired() {
        if (!persistent) {
            return;
        }
        try {
            mapper.deleteExpired(LocalDateTime.now());
        } catch (DataAccessException e) {
            log.warn("清理过期登录状态失败：{}", e.getMostSpecificCause().getMessage());
        }
    }
}
