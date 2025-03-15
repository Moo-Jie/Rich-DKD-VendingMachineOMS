package com.dkd.manage.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.dkd.common.constant.DkdContants;
import com.dkd.common.exception.ServiceException;
import com.dkd.common.utils.DateUtils;
import com.dkd.manage.domain.Emp;
import com.dkd.manage.domain.Task;
import com.dkd.manage.domain.TaskDetails;
import com.dkd.manage.domain.VendingMachine;
import com.dkd.manage.domain.dto.TaskDetailsDto;
import com.dkd.manage.domain.dto.TaskDto;
import com.dkd.manage.domain.vo.TaskVo;
import com.dkd.manage.mapper.TaskDetailsMapper;
import com.dkd.manage.mapper.TaskMapper;
import com.dkd.manage.service.IEmpService;
import com.dkd.manage.service.ITaskDetailsService;
import com.dkd.manage.service.ITaskService;
import com.dkd.manage.service.IVendingMachineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工单Service业务层处理
 *
 * @author DuRuiChi
 */
@Service
public class TaskServiceImpl implements ITaskService {
    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskDetailsMapper taskDetailsMapper;

    @Autowired
    private IVendingMachineService vendingMachineService;

    @Autowired
    private IEmpService empService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ITaskDetailsService taskDetailsService;
    /**
     * 查询工单
     *
     * @param taskId 工单主键
     * @return 工单
     */
    @Override
    public Task selectTaskByTaskId(Long taskId) {
        return taskMapper.selectTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     *
     * @param task 工单
     * @return 工单
     */
    @Override
    public List<Task> selectTaskList(Task task) {
        return taskMapper.selectTaskList(task);
    }

    /**
     * 新增工单
     *
     * @param task 工单
     * @return 结果
     */
    @Override
    public int insertTask(Task task) {
        task.setCreateTime(DateUtils.getNowDate());
        return taskMapper.insertTask(task);
    }

    /**
     * 修改工单
     *
     * @param task 工单
     * @return 结果
     */
    @Override
    public int updateTask(Task task) {
        task.setUpdateTime(DateUtils.getNowDate());
        return taskMapper.updateTask(task);
    }

    /**
     * 批量删除工单
     *
     * @param taskIds 需要删除的工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskIds(Long[] taskIds) {
        // 同步删除工单详情
        taskDetailsMapper.deleteTaskDetailsByTaskIds(taskIds);
        return taskMapper.deleteTaskByTaskIds(taskIds);
    }

    /**
     * 删除工单信息
     *
     * @param taskId 工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskId(Long taskId) {
        // 删除对应的工单详情
        return taskMapper.deleteTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     *
     * @param task
     * @return TaskVo集合
     */
    @Override
    public List<TaskVo> selectTaskVoList(Task task) {
        return taskMapper.selectTaskVoList(task);
    }
    /**
     * 新增运营、运维工单
     *
     * @param taskDto
     * @return 结果
     */
    @Transactional
    @Override
    public int insertTaskDto(TaskDto taskDto) {
        if (taskDto.getProductTypeId() == null || taskDto.getInnerCode() == null) {
            throw new ServiceException("工单参数不完整");
        }
        // 确保设备存在
        VendingMachine vm = vendingMachineService.selectVendingMachineByInnerCode(taskDto.getInnerCode());
        if (vm == null) {
            throw new ServiceException("设备不存在，请输入正确的设备编号！");
        }
        // 空指针预防
        checkVMStatusAndTaskType(vm.getVmStatus(), taskDto.getProductTypeId());
        hasTask(taskDto);
        // 工单类型的业务是否适用于当前设备状态
        checkVMStatusAndTaskType(vm.getVmStatus(), taskDto.getProductTypeId());
        // 检查当前设备是否有未完成的同类型的工单存在
        hasTask(taskDto);
        // 获取设备所属区域的员工
        Emp emp = empService.selectEmpById(taskDto.getUserId());
        if (!Objects.equals(emp.getRegionId(), vm.getRegionId())) {
            throw new ServiceException("设备区域与员工区域不一致，请重新配置！");
        }
        // 设备区域和员工区域必须匹配
        if (!emp.getRegionId().equals(vm.getRegionId())) {
            throw new ServiceException("设备区域与员工区域不一致，请重新配置！");
        }
        // dto转po并新增（不可变状态）
        Task task = BeanUtil.copyProperties(taskDto, Task.class);// 属性复制
        task.setTaskStatus(DkdContants.TASK_STATUS_CREATE);
        task.setUserName(emp.getUserName() != null ? emp.getUserName() : "未知人员"); // 空值处理
        task.setRegionId(vm.getRegionId());
        task.setAddr(vm.getAddr());
        task.setCreateTime(DateUtils.getNowDate());
        task.setTaskCode(getTaskCode());

        int taskResult = taskMapper.insertTask(task);
        // 事务结果验证
        if (taskResult <= 0) {
            throw new ServiceException("工单创建失败");
        }
        // 补货工单的额外业务
        if (DkdContants.TASK_TYPE_SUPPLY == taskDto.getProductTypeId()) {
            List<TaskDetailsDto> detailsList = taskDto.getDetails();
            if (CollUtil.isEmpty(detailsList)) {
                throw new ServiceException("请先进行补货！");
            }

            // 优化stream处理（修改）
            List<TaskDetails> taskDetailsList = detailsList.stream()
                    .map(dto -> BeanUtil.copyProperties(dto, TaskDetails.class))
                    .peek(details -> details.setTaskId(task.getTaskId()))
                    .collect(Collectors.toList());

            taskDetailsService.batchInsertTaskDetails(taskDetailsList);
        }
        return taskResult;
    }

    /**
     * 取消工单
     * @param task
     * @return 结果
     */
    @Override
    public int cancelTask(Task task) {
        if (task.getTaskId() == null) {
            throw new ServiceException("工单ID不能为空");
        }
        // 判断工单状态必须为取消或完成（前端已经进行约束，此处冗余判断求稳）
        Task taskDb = taskMapper.selectTaskByTaskId(task.getTaskId());
        if (taskDb == null) {
            throw new ServiceException("工单不存在");
        }
        if (Objects.equals(taskDb.getTaskStatus(), DkdContants.TASK_STATUS_CANCEL) ||
                Objects.equals(taskDb.getTaskStatus(), DkdContants.TASK_STATUS_FINISH)) {
            throw new ServiceException("工单["+taskDb.getTaskCode()+"]已经结束，不能取消！");
        }
        // 其他属性
        Task updateTask = new Task();
        updateTask.setTaskId(task.getTaskId());
        updateTask.setTaskStatus(DkdContants.TASK_STATUS_CANCEL);
        updateTask.setUpdateTime(DateUtils.getNowDate());
        return taskMapper.updateTask(updateTask);
    }

    /**
     * 生成并获取当天工单编号
     * @return java.lang.String
     * @author DuRuiChi
     **/
    private String getTaskCode() {
        // 设置redis键值对，格式：日期+四位十进制数
        String dateStr = DateUtils.getDate().replaceAll("-", "");
        String key = "dkd.task.code." + dateStr;

        // 使用原子操作初始化序号
        Long sequenceId = redisTemplate.opsForValue().increment(key);
        if (sequenceId == null) {
            throw new ServiceException("工单序号生成失败");
        }

        if (sequenceId == 1L) { // 首次生成需要设置过期时间
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        return dateStr + StrUtil.padPre(sequenceId.toString(), 4, '0');
    }

    /**
     * 检查设备是否有未完成的同类型工单
     * @param taskDto
     * @return void
     * @author DuRuiChi
     **/
    private void hasTask(TaskDto taskDto) {
        // 创建task条件对象，并设置设备编号和工单类型，以及工单状态为进行中
        Task taskParam = new Task();
        taskParam.setInnerCode(taskDto.getInnerCode());
        taskParam.setProductTypeId(taskDto.getProductTypeId());
        taskParam.setTaskStatus(DkdContants.TASK_STATUS_PROGRESS);
        // 调用taskMapper查询数据库查看是否有符合条件的工单列表
        List<Task> taskList = taskMapper.selectTaskList(taskParam);
        // 如果存在未完成的同类型工单，抛出异常
        if (taskList != null && taskList.size() > 0) {
            throw new ServiceException("该设备已有未完成的工单，不能重复创建");
        }
    }

    /**
     * 校验售货机状态与工单类型是否相符（已确保数据非空）
     * @param vmStatus
     * @param productTypeId
     * @return void
     * @author DuRuiChi
     * @create 2025/3/1
     **/
    private void checkVMStatusAndTaskType(Long vmStatus, Long productTypeId) {
        if (vmStatus == null || productTypeId == null) {
            throw new ServiceException("参数不能为空");
        }
        // 冲突1：如果是撤机工单，设备不在运行中
        if (productTypeId == DkdContants.TASK_TYPE_REVOKE && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不为运行中，无法进行撤机");
        }
        // 冲突2：如果是维修工单，设备不在运行中，抛出异常
        if (productTypeId == DkdContants.TASK_TYPE_REPAIR && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不为运行中，无法进行维修");
        }
        // 冲突3：如果是投放工单，设备在运行中，抛出异常
        if (productTypeId == DkdContants.TASK_TYPE_DEPLOY && vmStatus == DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态为运行中，无法进行投放");
        }
        // 冲突4：如果是补货工单，设备不在运行中，抛出异常
        if (productTypeId == DkdContants.TASK_TYPE_SUPPLY && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不为运行中，无法进行补货");
        }
    }
}
