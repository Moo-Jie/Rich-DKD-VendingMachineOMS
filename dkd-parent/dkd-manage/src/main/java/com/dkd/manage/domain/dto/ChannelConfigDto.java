package com.dkd.manage.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 售货机货道DTO类
 * @return
 * @author DuRuiChi
 * @create 2024/11/25
 **/
@Data
public class ChannelConfigDto {

    private String innerCode;// 售货机编号
    private List<ChannelSkuDto> channelList;// 货道Dto集合
}
