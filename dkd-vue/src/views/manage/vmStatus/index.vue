<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="设备编号" prop="innerCode">
        <el-input
          v-model="queryParams.innerCode"
          placeholder="请输入设备编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

  

    <el-table v-loading="loading" :data="vmList" @selection-change="handleSelectionChange">
      <el-table-column label="序号" type="index" width="55" align="center" />
      <el-table-column label="设备编号" align="center" prop="innerCode" />
      <el-table-column label="设备型号" align="center" prop="vmTypeId" >
        <template #default="scope">
          <div v-for="item in vmTypeList" :key="item.id">
            <span v-if="item.id==scope.row.vmTypeId">{{ item.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="详细地址" align="left" prop="addr" show-overflow-tooltip="true"/>
      <el-table-column label="运营状态" align="center" prop="vmStatus">
        <template #default="scope">
          <dict-tag :options="vm_status" :value="scope.row.vmStatus"/>
        </template>
      </el-table-column>
      <el-table-column label="设备状态" align="center" prop="vmStatus">
        <template #default="scope">
         {{ scope.row.runningStatus!=null? JSON.parse(scope.row.runningStatus).status==true?'正常':'异常' :'异常'}}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" @click="getVmInfo(scope.row)" v-hasPermi="['manage:vm:query']">查看详情</el-button>
          <el-button link type="warning" @click="handleVmStatusChange(scope.row)" v-hasPermi="['manage:vm:edit']">运营状态</el-button>
          <el-button link type="danger" @click="handleRunningStatusChange(scope.row)" v-hasPermi="['manage:vm:edit']">设备状态</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 查看详情对话框，展示单个数据的详情 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="设备编号">{{ form.innerCode }}</el-descriptions-item>
        <el-descriptions-item label="货道最大容量">{{ form.channelMaxCapacity }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ form.addr }}</el-descriptions-item>

        <el-descriptions-item label="设备型号">
          <span v-for="item in vmTypeList" :key="item.id">
            {{ item.id === form.vmTypeId ? item.name : '' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="运营状态">
          <dict-tag :options="vm_status" :value="form.vmStatus"/>
        </el-descriptions-item>

        <el-descriptions-item label="运行状态" :span="2">
          {{ form.runningStatus ? JSON.parse(form.runningStatus).status ? '正常' : '异常' : '未知' }}
        </el-descriptions-item>

        <el-descriptions-item label="经纬度">
          {{ form.longitudes }}, {{ form.latitude }}
        </el-descriptions-item>
        <el-descriptions-item label="最后补货时间">
          {{ form.lastSupplyTime }}
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 运营状态修改对话框 -->
    <el-dialog title="修改运营状态" v-model="vmStatusOpen" width="500px" append-to-body>
      <el-form :model="vmStatusForm" ref="vmStatusRef" label-width="80px">
        <el-form-item label="新状态" prop="status" :rules="[{ required: true, message: '请选择状态', trigger: 'change' }]">
          <el-select v-model="vmStatusForm.status" placeholder="请选择状态">
            <el-option label="运营" :value="1" />
            <el-option label="未投放" :value="0" />
            <el-option label="撤机" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="vmStatusOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitVmStatus">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 运行状态修改对话框 -->
    <el-dialog title="修改运行状态" v-model="runningStatusOpen" width="500px" append-to-body>
      <el-form :model="runningStatusForm" ref="runningStatusRef" label-width="80px">
        <el-form-item label="新状态" prop="status" :rules="[{ required: true, message: '请选择状态', trigger: 'change' }]">
          <el-select v-model="runningStatusForm.status" placeholder="请选择状态">
            <el-option label="正常" :value="1" />
            <el-option label="异常" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="runningStatusOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitRunningStatus">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Vm">
import {addVm, delVm, getVm, listVm, updateVm} from "@/api/manage/vm";
import {listVmType} from "@/api/manage/vmType";
import {listPartner} from "@/api/manage/partner";
import {loadAllParams} from "@/api/page";
import {listNode} from "@/api/manage/node";
import {listRegion} from "@/api/manage/region";
import {ref} from "vue";

const vmStatusOpen = ref(false);
const runningStatusOpen = ref(false);
const vmStatusForm = ref({ id: null, status: null });
const runningStatusForm = ref({ id: null, status: null });
const { proxy } = getCurrentInstance();
const { vm_status } = proxy.useDict('vm_status');
const statusOpen = ref(false);
const statusForm = ref({
  id: null,
  status: null
});
const vmList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    innerCode: null,
    nodeId: null,
    businessType: null,
    regionId: null,
    partnerId: null,
    vmTypeId: null,
    vmStatus: null,
    runningStatus: null,
    policyId: null,
  },
  rules: {
    nodeId: [
      { required: true, message: "点位Id不能为空", trigger: "blur" }
    ],
    vmTypeId: [
      { required: true, message: "设备型号不能为空", trigger: "blur" }
    ],
  }
});

const { queryParams, form, rules } = toRefs(data);

// 状态修改处理函数
function handleStatusChange(row) {
  statusForm.value = {
    id: row.id,
    status: row.vmStatus
  };
  statusOpen.value = true;
}

// 提交状态修改
function submitStatus() {
  proxy.$refs.statusRef.validate(valid => {
    if (valid) {
      updateVm(statusForm.value).then(response => {
        proxy.$modal.msgSuccess("状态修改成功");
        statusOpen.value = false;
        getList(); // 刷新列表
      }).catch(() => {
        proxy.$modal.msgError("状态修改失败");
      });
    }
  });
}

/** 查询设备管理列表 */
function getList() {
  loading.value = true;
  listVm(queryParams.value).then(response => {
    vmList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    innerCode: null,
    channelMaxCapacity: null,
    nodeId: null,
    addr: null,
    lastSupplyTime: null,
    businessType: null,
    regionId: null,
    partnerId: null,
    vmTypeId: null,
    vmStatus: null,
    runningStatus: null,
    longitudes: null,
    latitude: null,
    clientId: null,
    policyId: null,
    createTime: null,
    updateTime: null
  };
  proxy.resetForm("vmRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加设备管理";
}

/** 修改按钮操作 */
function getVmInfo(row) {
  reset();
  const _id = row.id || ids.value
  getVm(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "设备详情";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["vmRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateVm(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addVm(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('是否确认删除设备管理编号为"' + _ids + '"的数据项？').then(function() {
    return delVm(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('manage/vm/export', {
    ...queryParams.value
  }, `vm_${new Date().getTime()}.xlsx`)
}

/* 查询设备类型列表 */
const vmTypeList=ref([]);
function getVmTypeList() {
  listVmType(loadAllParams).then(response => {
    vmTypeList.value = response.rows;
  });
}

/* 查询合作商列表 */
const partnerList=ref([]);
function getPartnerList() {
  listPartner(loadAllParams).then(response => {
    partnerList.value = response.rows;
  });
}

/* 查询点位列表 */
const nodeList=ref([]);
function getNodeList() {
  listNode(loadAllParams).then(response => {
    nodeList.value = response.rows;
  });
}

/* 查询区域列表 */
const regionList=ref([]);
function getRegionList() {
  listRegion(loadAllParams).then(response => {
    regionList.value = response.rows;
  });
}

function handleVmStatusChange(row) {
  vmStatusForm.value = { id: row.id, status: row.vmStatus };
  vmStatusOpen.value = true;
}

function handleRunningStatusChange(row) {
  const currentStatus = row.runningStatus ? JSON.parse(row.runningStatus).status : false;
  runningStatusForm.value = { id: row.id, status: currentStatus };
  runningStatusOpen.value = true;
}

function submitVmStatus() {
  proxy.$refs.vmStatusRef.validate(valid => {
    if (valid) {
      updateVm({ id: vmStatusForm.value.id, vmStatus: vmStatusForm.value.status })
          .then(() => {
            proxy.$modal.msgSuccess("运营状态更新成功");
            vmStatusOpen.value = false;
            getList();
          })
          .catch(() => proxy.$modal.msgError("更新失败"));
    }
  });
}

function submitRunningStatus() {
  proxy.$refs.runningStatusRef.validate(valid => {
    if (valid) {
      const statusData = { status: runningStatusForm.value.status };
      updateVm({
        id: runningStatusForm.value.id,
        runningStatus: JSON.stringify(statusData)
      }).then(() => {
        proxy.$modal.msgSuccess("运行状态更新成功");
        runningStatusOpen.value = false;
        getList();
      }).catch(() => proxy.$modal.msgError("更新失败"));
    }
  });
}

getRegionList();
getNodeList();
getPartnerList();
getVmTypeList();
getList();
</script>
