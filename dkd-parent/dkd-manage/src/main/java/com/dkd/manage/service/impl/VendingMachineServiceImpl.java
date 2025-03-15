package com.dkd.manage.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.dkd.common.constant.DkdContants;
import com.dkd.common.exception.ServiceException;
import com.dkd.common.utils.DateUtils;
import com.dkd.common.utils.uuid.UUIDUtils;
import com.dkd.manage.domain.Channel;
import com.dkd.manage.domain.Node;
import com.dkd.manage.domain.VendingMachine;
import com.dkd.manage.domain.VmType;
import com.dkd.manage.mapper.VendingMachineMapper;
import com.dkd.manage.service.IChannelService;
import com.dkd.manage.service.INodeService;
import com.dkd.manage.service.IVendingMachineService;
import com.dkd.manage.service.IVmTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 设备管理Service业务层处理
 *
 * @author DuRuiChi
 */
@Service
public class VendingMachineServiceImpl implements IVendingMachineService
{
    @Autowired
    private VendingMachineMapper vendingMachineMapper;


    @Autowired
    private IVmTypeService vmTypeService;

    @Autowired
    private INodeService nodeService;

    @Autowired
    private IChannelService channelService;

    /**
     * 查询设备管理
     *
     * @param id 设备管理主键
     * @return 设备管理
     */
    @Override
    public VendingMachine selectVendingMachineById(Long id)
    {
        return vendingMachineMapper.selectVendingMachineById(id);
    }

    /**
     * 查询设备管理列表
     *
     * @param vendingMachine 设备管理
     * @return 设备管理
     */
    @Override
    public List<VendingMachine> selectVendingMachineList(VendingMachine vendingMachine)
    {
        return vendingMachineMapper.selectVendingMachineList(vendingMachine);
    }

    /**
     * 新增设备管理
     *
     * @param vendingMachine 设备管理
     * @return 结果
     */
    @Transactional
    @Override
    public int insertVendingMachine(VendingMachine vendingMachine)
    {
        if (vendingMachine.getVmTypeId() == null) {
            throw new ServiceException("设备类型不能为空");
        }
        if (vendingMachine.getNodeId() == null) {
            throw new ServiceException("所属点位不能为空");
        }
        // 新增设备
        // 货道编号
        String innerCode = UUIDUtils.getUUID();
        vendingMachine.setInnerCode(innerCode);
        // 通过售货机类，设定售货机容量
        VmType vmType = vmTypeService.selectVmTypeById(vendingMachine.getVmTypeId());
        if (vmType == null) {
            throw new ServiceException("无效的设备类型");
        }
        vendingMachine.setChannelMaxCapacity(vmType.getChannelMaxCapacity());
        // 新设备所属的区域信息、点位信息以及合作商信息
        Node node = nodeService.selectNodeById(vendingMachine.getNodeId());
        if (node == null) {
            throw new ServiceException("无效的所属点位");
        }
        BeanUtil.copyProperties(node,vendingMachine,"id");// 商圈类型、区域、合作商
        vendingMachine.setAddr(node.getAddress());// 设备地址
        // 设备状态
        vendingMachine.setVmStatus(DkdContants.VM_STATUS_NODEPLOY);// 0-表示未投放
        vendingMachine.setCreateTime(DateUtils.getNowDate());// 创建时间
        vendingMachine.setUpdateTime(DateUtils.getNowDate());// 更新时间
        int result = vendingMachineMapper.insertVendingMachine(vendingMachine);

        // 新增货道
        List<Channel> channelList = createChannels(vendingMachine, vmType);
        channelService.batchInsertChannels(channelList);
        return result;
    }

    /**
     * 货道创建方法
     * @param vm
     * @param vmType
     * @return java.util.List<com.dkd.manage.domain.Channel>
     * @author DuRuiChi
     **/
    private List<Channel> createChannels(VendingMachine vm, VmType vmType) {
        return IntStream.rangeClosed(1, vmType.getVmRow().intValue())
                .boxed()
                .flatMap(i -> IntStream.rangeClosed(1, vmType.getVmCol().intValue())
                        .mapToObj(j -> buildChannel(i, j, vm, vmType)))
                .collect(Collectors.toList());
    }

    /**
     * 货道构建方法
     * @param row
     * @param col
     * @param vm
     * @param vmType
     * @return com.dkd.manage.domain.Channel
     * @author DuRuiChi
     **/
    private Channel buildChannel(int row, int col, VendingMachine vm, VmType vmType) {
        Channel channel = new Channel();
        channel.setChannelCode(row + "-" + col);
        channel.setVmId(vm.getId());
        channel.setInnerCode(vm.getInnerCode());
        channel.setMaxCapacity(vmType.getChannelMaxCapacity());
        Date now = DateUtils.getNowDate();
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        return channel;
    }

    /**
     * 修改设备管理
     *
     * @param vendingMachine 设备管理
     * @return 结果
     */
    @Override
    public int updateVendingMachine(VendingMachine vendingMachine)
    {
        if(vendingMachine.getNodeId()!=null){
            // 查询点位表，补充：区域、点位、合作商等信息 
            Node node = nodeService.selectNodeById(vendingMachine.getNodeId());
            BeanUtil.copyProperties(node,vendingMachine,"id");// 商圈类型、区域、合作商
            vendingMachine.setAddr(node.getAddress());// 设备地址
        }
        vendingMachine.setUpdateTime(DateUtils.getNowDate());// 更新时间
        return vendingMachineMapper.updateVendingMachine(vendingMachine);
    }

    /**
     * 批量删除设备管理
     *
     * @param ids 需要删除的设备管理主键
     * @return 结果
     */
    @Override
    public int deleteVendingMachineByIds(Long[] ids)
    {
        return vendingMachineMapper.deleteVendingMachineByIds(ids);
    }

    /**
     * 删除设备管理信息
     *
     * @param id 设备管理主键
     * @return 结果
     */
    @Override
    public int deleteVendingMachineById(Long id)
    {
        return vendingMachineMapper.deleteVendingMachineById(id);
    }

    /**
     *  根据设备编号查询设备信息
     * @param innerCode
     * @return VendingMachine
     */
    @Override
    public VendingMachine selectVendingMachineByInnerCode(String innerCode) {
        return vendingMachineMapper.selectVendingMachineByInnerCode(innerCode);
    }
}