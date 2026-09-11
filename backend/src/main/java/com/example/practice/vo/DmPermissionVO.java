package com.example.practice.vo;

import lombok.Data;

/**
 * 私信发送权限 VO：抖音式规则的前端预判结果（用于禁用输入框/提示文案）
 */
@Data
public class DmPermissionVO {

    /** 当前能否给对方发送私信 */
    private Boolean canSend;

    /** 不能发的原因（canSend=true 时为 null） */
    private String reason;

    /** 构造"可以发送" */
    public static DmPermissionVO ok() {
        DmPermissionVO vo = new DmPermissionVO();
        vo.setCanSend(true);
        return vo;
    }

    /** 构造"不可以发送"，带原因文案 */
    public static DmPermissionVO deny(String reason) {
        DmPermissionVO vo = new DmPermissionVO();
        vo.setCanSend(false);
        vo.setReason(reason);
        return vo;
    }
}
