package com.dkd.manage.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 工单DTO类
 * @return
 * @author DuRuiChi
 * @create 2024/11/25
 **/
@Data
public class TaskDto {

    private Long createType;// 创建类型
    private String innerCode;// 设备编号
    private Long userId;// 执行人id
    private Long assignorId;// 指派人id
    private Long productTypeId;// 工单类型
    private String desc;// 描述信息
    private List<TaskDetailsDto> details;// 工单详情（只有补货工单才涉及）
}
