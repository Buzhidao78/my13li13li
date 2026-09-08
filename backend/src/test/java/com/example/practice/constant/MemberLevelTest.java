package com.example.practice.constant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员等级常量逻辑单元测试（不依赖 Spring 容器，纯逻辑验证）
 * 运行方式：mvn test
 */
class MemberLevelTest {

    @Test
    void 可购买等级不包含普通会员() {
        List<MemberLevel.Info> buyable = MemberLevel.buyable();
        assertEquals(3, buyable.size(), "可购买等级应为白银/黄金/钻石 3 个");
        assertTrue(buyable.stream().noneMatch(i -> i.level == MemberLevel.NORMAL),
                "普通会员不应出现在可购买列表");
    }

    @Test
    void 只有白银黄金钻石可购买() {
        assertTrue(MemberLevel.isValid(MemberLevel.SILVER));
        assertTrue(MemberLevel.isValid(MemberLevel.GOLD));
        assertTrue(MemberLevel.isValid(MemberLevel.DIAMOND));
        assertFalse(MemberLevel.isValid(MemberLevel.NORMAL), "普通会员不可购买");
        assertFalse(MemberLevel.isValid(99), "非法等级不可购买");
    }

    @Test
    void 各等级配置完整() {
        for (int level : new int[]{MemberLevel.SILVER, MemberLevel.GOLD, MemberLevel.DIAMOND}) {
            MemberLevel.Info info = MemberLevel.of(level);
            assertNotNull(info, "等级 " + level + " 应有配置");
            assertNotNull(info.name);
            assertTrue(info.amount.signum() > 0, "价格应为正数");
            assertTrue(info.days > 0, "时长应为正数");
        }
        assertNull(MemberLevel.of(-1), "非法等级应返回 null");
    }
}
