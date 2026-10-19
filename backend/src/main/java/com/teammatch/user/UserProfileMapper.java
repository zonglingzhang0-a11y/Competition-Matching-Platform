package com.teammatch.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.dao.DuplicateKeyException;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    /**
     * 把档案这一行当作"每个用户一把锁"，让同一个人的入队操作排队执行。
     * 不锁 sys_user：队员表、申请表改状态时，外键检查会对 sys_user 加共享锁，锁它会和外键检查互相等待而死锁；
     * user_profile 没有被任何外键引用，不会碰到这个问题。返回 null 表示这个用户还没有档案行。
     */
    @Select("SELECT user_id FROM user_profile WHERE user_id = #{userId} FOR UPDATE")
    Long lockByUserId(@Param("userId") long userId);

    /** 锁住这个人（同一个人的几个请求排队执行）；老账号可能没有档案行，补一行再锁 */
    default void lockUser(long userId) {
        if (lockByUserId(userId) == null) {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            try {
                insert(p);
            } catch (DuplicateKeyException ignored) {
                // 别的请求刚好补上了
            }
            lockByUserId(userId);
        }
    }
}
