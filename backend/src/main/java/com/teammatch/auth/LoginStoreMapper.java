package com.teammatch.auth;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 登录状态表 login_store，用法见 PersistentTokenDao */
@Mapper
public interface LoginStoreMapper {

    @Select("""
            SELECT store_key AS storeKey, value_type AS valueType, store_value AS storeValue, expire_at AS expireAt
            FROM login_store
            """)
    List<Row> all();

    @Insert("""
            INSERT INTO login_store (store_key, value_type, store_value, expire_at)
            VALUES (#{key}, #{type}, #{value}, #{expireAt})
            ON DUPLICATE KEY UPDATE value_type = #{type}, store_value = #{value}, expire_at = #{expireAt}
            """)
    void save(@Param("key") String key, @Param("type") String type, @Param("value") String value,
              @Param("expireAt") LocalDateTime expireAt);

    @Delete("DELETE FROM login_store WHERE store_key = #{key}")
    void delete(@Param("key") String key);

    @Delete("DELETE FROM login_store WHERE expire_at <= #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);

    /** expireAt 为空表示永不过期 */
    record Row(String storeKey, String valueType, String storeValue, LocalDateTime expireAt) {
    }
}
