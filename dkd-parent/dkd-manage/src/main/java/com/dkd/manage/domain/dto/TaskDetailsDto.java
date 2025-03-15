package com.dkd.manage.domain.dto;

import lombok.Data;

/**
 * 工单详情DTO类
 * @return
 * @author DuRuiChi
 * @create 2024/11/25
 **/
@Data
public class TaskDetailsDto {

    private String channelCode;// 货道编号
    private Long expectCapacity;// 补货数量
    private Long skuId;// 商品id
    private String skuName;// 商品名称
    private String skuImage;// 商品图片
}
