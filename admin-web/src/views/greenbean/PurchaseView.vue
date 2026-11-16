<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGreenBeans, listSuppliers, type GreenBean, type Supplier } from '../../api/archive'
import { listLots, listMovements, purchase, voidLot, type GreenLot, type InventoryMovement, type PurchaseRequest } from '../../api/greenbean'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.role === 'ADMIN')
const BIZ_TYPE_LABELS: Record<string, string> = { PURCHASE_IN: '采购入库', PURCHASE_VOID: '采购作废' }
function bizTypeLabel(t?: string) { return t ? BIZ_TYPE_LABELS[t] ?? t : '' }
const suppliers = ref<Supplier[]>([]), beans = ref<GreenBean[]>([])
const lots = ref<GreenLot[]>([]), movements = ref<InventoryMovement[]>([])
const total = ref(0), movementTotal = ref(0), loading = ref(false), activeTab = ref('lots')
const query = reactive({ keyword: '', status: '', page: 1, size: 20 })
const movementQuery = reactive({ lotId: undefined as number | undefined, page: 1, size: 20 })
const form = reactive({ supplierId: undefined as number | undefined, greenBeanId: undefined as number | undefined, purchaseDate: new Date().toISOString().slice(0, 10), lotNo: '', quantityKg: undefined as number | undefined, unitPrice: undefined as number | undefined, amount: undefined as number | undefined, remark: '' })
const calculatedAmount = computed(() => form.quantityKg && form.unitPrice ? Number((form.quantityKg * form.unitPrice).toFixed(2)) : undefined)
watch(() => [form.quantityKg, form.unitPrice], () => { form.amount = calculatedAmount.value })
async function loadOptions() {
  const [s, b] = await Promise.all([listSuppliers({ page: 1, size: 100, status: 'ACTIVE' }), listGreenBeans({ page: 1, size: 100, status: 'ACTIVE' })])
  suppliers.value = s.list; beans.value = b.list
}
async function loadLots() { loading.value = true; try { const d = await listLots({ ...query, keyword: query.keyword || undefined, status: query.status || undefined }); lots.value = d.list; total.value = d.total } finally { loading.value = false } }
async function loadMovements() { const d = await listMovements({ lotId: movementQuery.lotId, page: movementQuery.page, size: movementQuery.size }); movements.value = d.list; movementTotal.value = d.total }
function search() { query.page = 1; loadLots() }
function resetForm() { Object.assign(form, { supplierId: undefined, greenBeanId: undefined, purchaseDate: new Date().toISOString().slice(0, 10), lotNo: '', quantityKg: undefined, unitPrice: undefined, amount: undefined, remark: '' }) }
async function submit() {
  if (!form.supplierId || !form.greenBeanId || !form.purchaseDate || !form.quantityKg || !form.unitPrice) return ElMessage.warning('请完整填写供货商、生豆、日期、数量和单价')
  const request: PurchaseRequest = {
    supplierId: form.supplierId,
    greenBeanId: form.greenBeanId,
    purchaseDate: form.purchaseDate,
    lotNo: form.lotNo || undefined,
    quantityKg: form.quantityKg,
    unitPrice: form.unitPrice,
    amount: form.amount || calculatedAmount.value,
    remark: form.remark || undefined
  }
  await purchase(request)
  ElMessage.success('采购入库成功'); resetForm(); loadLots()
}
function showMovements(row: GreenLot) { movementQuery.lotId = row.id; movementQuery.page = 1; activeTab.value = 'movements'; loadMovements() }
function rowClass({ row }: { row: GreenLot }) { return row.status === 'VOID' ? 'row-void' : '' }
async function onVoid(row: GreenLot) {
  const hint = Number(row.remainingKg) > 0
    ? `确认作废批次「${row.lotNo}」？剩余 ${row.remainingKg} kg 将写反向流水冲销，已领用部分保留历史。`
    : `确认作废批次「${row.lotNo}」？该批次已无剩余量，作废仅作留档标记。`
  try {
    const { value } = await ElMessageBox.prompt(hint, '作废批次', {
      type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消', inputPlaceholder: '作废原因（可选）'
    })
    await voidLot(row.id, value || undefined)
    ElMessage.success('批次已作废'); loadLots()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    const data = (err as { response?: { data?: { message?: string } } })?.response?.data
    ElMessage.error(data?.message || '作废失败')
  }
}
onMounted(async () => { await loadOptions(); await loadLots() })
</script>
<template>
  <el-card>
    <el-tabs v-model="activeTab"><el-tab-pane label="采购入库" name="purchase"><el-form :model="form" label-width="90px" class="purchase-form"><el-form-item label="供货商"><el-select v-model="form.supplierId" filterable placeholder="请选择"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item><el-form-item label="生豆"><el-select v-model="form.greenBeanId" filterable placeholder="请选择"><el-option v-for="b in beans" :key="b.id" :label="b.name" :value="b.id" /></el-select></el-form-item><el-form-item label="采购日期"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="批次号"><el-input v-model="form.lotNo" placeholder="留空自动生成" /></el-form-item><el-form-item label="数量(kg)"><el-input-number v-model="form.quantityKg" :min="0" :precision="3" /></el-form-item><el-form-item label="单价(元/kg)"><el-input-number v-model="form.unitPrice" :min="0" :precision="2" /></el-form-item><el-form-item label="金额"><el-input-number v-model="form.amount" :placeholder="calculatedAmount ? String(calculatedAmount) : '自动计算，可修改'" :min="0" :precision="2" /></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item><el-form-item><el-button type="primary" @click="submit">保存并入库</el-button><el-button @click="resetForm">重置</el-button></el-form-item></el-form></el-tab-pane>
      <el-tab-pane label="生豆批次库存" name="lots"><div class="toolbar"><el-input v-model="query.keyword" placeholder="批次号" clearable @keyup.enter="search" /><el-select v-model="query.status" placeholder="状态" clearable @change="search"><el-option label="可用" value="AVAILABLE" /><el-option label="已作废" value="VOID" /></el-select><el-button type="primary" @click="search">查询</el-button></div><el-table v-loading="loading" :data="lots" border :row-class-name="rowClass"><el-table-column prop="lotNo" label="批次号" /><el-table-column prop="greenBeanName" label="生豆" min-width="160" /><el-table-column prop="supplierName" label="供货商" /><el-table-column prop="purchaseDate" label="采购日期" /><el-table-column prop="totalKg" label="总量(kg)" /><el-table-column prop="remainingKg" label="剩余(kg)" /><el-table-column prop="costPrice" label="单价" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'VOID' ? 'info' : 'success'" size="small">{{ row.status === 'VOID' ? '已作废' : '可用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="150"><template #default="{ row }"><el-button size="small" @click="showMovements(row)">流水</el-button><el-button v-if="isAdmin && row.status === 'AVAILABLE'" size="small" type="danger" @click="onVoid(row)">作废</el-button></template></el-table-column></el-table><el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="(p: number) => { query.page = p; loadLots() }" /></el-tab-pane>
      <el-tab-pane label="库存流水" name="movements" @click="loadMovements"><el-table :data="movements" border><el-table-column prop="createdAt" label="时间" /><el-table-column label="类型"><template #default="{ row }">{{ bizTypeLabel(row.bizType) }}</template></el-table-column><el-table-column prop="lotNo" label="批次号" /><el-table-column prop="greenBeanName" label="生豆" /><el-table-column prop="changeKg" label="变动(kg)" /><el-table-column prop="amount" label="金额" /><el-table-column prop="remark" label="备注" /></el-table><el-pagination class="pager" layout="total, prev, pager, next" :total="movementTotal" :page-size="movementQuery.size" :current-page="movementQuery.page" @current-change="(p: number) => { movementQuery.page = p; loadMovements() }" /></el-tab-pane>
    </el-tabs>
  </el-card>
</template>
<style scoped>.purchase-form { max-width: 650px }.purchase-form .el-select, .purchase-form .el-date-editor { width: 260px }.toolbar { display: flex; gap: 8px; margin-bottom: 12px }.toolbar .el-input { width: 220px }.toolbar .el-select { width: 130px }.pager { margin-top: 12px; justify-content: flex-end }:deep(.row-void) { opacity: .55 }</style>
