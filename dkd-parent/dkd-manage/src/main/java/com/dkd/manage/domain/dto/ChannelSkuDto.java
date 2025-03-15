package com.dkd.manage.domain.dto;

import lombok.Data;

/**
 * 货道商品DTO类
 * @return
 * @author DuRuiChi
 * @create 2024/11/25
 **/
@Data
public class ChannelSkuDto {

    private String innerCode;// 售货机编号
    private String channelCode;// 货道编号
    private Long skuId;// 商品ID
}
