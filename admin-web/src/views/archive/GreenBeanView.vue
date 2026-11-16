<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGreenBeans, createGreenBean, updateGreenBean, deleteGreenBean, listBrewMethods, listSuppliers, type GreenBean, type BrewMethod, type Supplier } from '../../api/archive'
import { useAuthStore } from '../../stores/auth'

const loading = ref(false), list = ref<GreenBean[]>([]), total = ref(0)
const suppliers = ref<Supplier[]>([]), brewMethods = ref<BrewMethod[]>([])
const query = reactive({ keyword: '', status: '', page: 1, size: 20 })
const visible = ref(false), editingId = ref<number | null>(null)
const ROAST_LEVELS = ['浅', '中浅', '中', '中深', '深']
const statusMap: Record<string, string> = { ACTIVE: '启用', DISABLED: '停用' }

const form = reactive<Partial<GreenBean>>({
  beanType: 'SINGLE', code: '', name: '', origin: '', region: '', process: '', variety: '',
  season: '', flavor: '', recipe: '', agtronColor: undefined, moisturePct: undefined,
  altitudeM: undefined, recommendedRoast: '', packageSpec: '', defaultSupplierId: undefined,
  status: 'ACTIVE', remark: '', brewMethodIds: []
})

function beanTag(row: GreenBean) {
  const isBlend = row.beanType === 'BLEND'
  return isBlend ? { text: '拼配', type: 'warning' as const } : { text: '单品', type: 'primary' as const }
}
function isSingle() { return form.beanType !== 'BLEND' }
const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

async function load() {
  loading.value = true
  try {
    const d = await listGreenBeans({ ...query, keyword: query.keyword || undefined, status: query.status || undefined, includeMethods: true })
    list.value = d.list; total.value = d.total
  } finally { loading.value = false }
}
async function loadOptions() {
  const [s, b] = await Promise.all([
    listSuppliers({ page: 1, size: 200 }),
    listBrewMethods({ page: 1, size: 200 })
  ])
  suppliers.value = s.list; brewMethods.value = b.list
}
function search() { query.page = 1; load() }
function openCreate() {
  editingId.value = null
  Object.assign(form, {
    beanType: 'SINGLE', code: '', name: '', origin: '', region: '', process: '', variety: '',
    season: '', flavor: '', recipe: '', agtronColor: undefined, moisturePct: undefined,
    altitudeM: undefined, recommendedRoast: '', packageSpec: '', defaultSupplierId: undefined,
    status: 'ACTIVE', remark: '', brewMethodIds: []
  })
  visible.value = true
}
function openEdit(row: GreenBean) {
  editingId.value = row.id
  Object.assign(form, row, { beanType: row.beanType || 'SINGLE', brewMethodIds: row.brewMethodIds ? [...row.brewMethodIds] : [] })
  visible.value = true
}
async function submit() {
  if (!form.name?.trim()) return ElMessage.warning('请输入生豆名称')
  const payload: Partial<GreenBean> = { ...form, brewMethodIds: form.brewMethodIds || [] }
  if (editingId.value == null) await createGreenBean(payload); else await updateGreenBean(editingId.value, payload)
  ElMessage.success('已保存'); visible.value = false; load()
}
async function onDelete(row: GreenBean) {
  try {
    await ElMessageBox.confirm(`确认删除生豆「${row.name}」？还有可用批次则不可删，请先在生豆批次库存中作废批次。`, '删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteGreenBean(row.id)
    ElMessage.success('已删除'); load()
  } catch (err) {
    const data = (err as { response?: { data?: { message?: string } } })?.response?.data
    ElMessage.error(data?.message || '删除失败')
  }
}
onMounted(async () => { await loadOptions(); await load() })
</script>
<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="名称/编号/产地/产区/风味" clearable @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="状态" clearable @change="search">
        <el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button><el-button type="success" @click="openCreate">新增生豆</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="类型" width="80"><template #default="{ row }"><el-tag :type="beanTag(row).type">{{ beanTag(row).text }}</el-tag></template></el-table-column>
      <el-table-column label="编号" width="110"><template #default="{ row }">{{ row.code || '-' }}</template></el-table-column>
      <el-table-column prop="name" label="名称" min-width="170" show-overflow-tooltip />
      <el-table-column label="产地(国家)" width="120"><template #default="{ row }">{{ row.origin || '' }}</template></el-table-column>
      <el-table-column label="产区" width="110"><template #default="{ row }">{{ row.region || '' }}</template></el-table-column>
      <el-table-column label="风味/描述" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.flavor || '' }}</template></el-table-column>
      <el-table-column label="配方/色值" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.beanType === 'BLEND' ? (row.recipe || '') + (row.agtronColor != null ? `（色值${row.agtronColor}）` : '') : '' }}</template></el-table-column>
      <el-table-column label="推荐烘焙度" width="100"><template #default="{ row }">{{ row.recommendedRoast || '-' }}</template></el-table-column>
      <el-table-column label="推荐方向" min-width="150"><template #default="{ row }"><template v-for="n in row.brewMethodNames || []" :key="n"><el-tag size="small" class="mtag">{{ n }}</el-tag></template><span v-if="!(row.brewMethodNames || []).length">-</span></template></el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ statusMap[row.status] }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button v-if="isAdmin" size="small" type="danger" @click="onDelete(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="(p: number) => { query.page = p; load() }" />
    <el-dialog v-model="visible" :title="editingId == null ? '新增生豆' : '编辑生豆'" width="820px">
      <el-form :model="form" label-width="96px" class="bf">
        <el-row>
          <el-col :span="24">
            <el-form-item label="类型">
              <el-radio-group v-model="form.beanType">
                <el-radio-button value="SINGLE">单品</el-radio-button>
                <el-radio-button value="BLEND">拼配</el-radio-button>
              </el-radio-group>
              <span class="tip">{{ isSingle() ? '单品：产地/产区/豆种等产地属性' : '拼配：配方/色值等混配属性' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="编号"><el-input v-model="form.code" placeholder="可空·手填不查重" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称"><el-input v-model="form.name" placeholder="必填" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="推荐烘焙度"><el-select v-model="form.recommendedRoast" clearable placeholder="浅/中浅/中/中深/深"><el-option v-for="r in ROAST_LEVELS" :key="r" :label="r" :value="r" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="包装规格"><el-input v-model="form.packageSpec" placeholder="如 60kg/袋" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="推荐方向"><el-select v-model="form.brewMethodIds" multiple filterable collapse-tags placeholder="从冲煮方式选择，如 手冲/意式" style="width: 100%"><el-option v-for="m in brewMethods" :key="m.id" :label="m.name + (m.status === 'ACTIVE' ? '' : '（停用）')" :value="m.id" /></el-select></el-form-item></el-col>

          <template v-if="isSingle()">
            <el-col :span="12"><el-form-item label="产地(国家)"><el-input v-model="form.origin" placeholder="如 埃塞俄比亚" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="产区"><el-input v-model="form.region" placeholder="如 耶加雪菲" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="处理方式"><el-input v-model="form.process" placeholder="如 水洗/日晒" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="豆种"><el-input v-model="form.variety" placeholder="如 Heirloom" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="产季"><el-input v-model="form.season" placeholder="如 2025/2026" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="含水率(%)"><el-input-number v-model="form.moisturePct" :min="0" :max="100" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="海拔(m)"><el-input-number v-model="form.altitudeM" :min="0" :precision="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="风味"><el-input v-model="form.flavor" placeholder="杯测风味描述" /></el-form-item></el-col>
          </template>
          <template v-else>
            <el-col :span="24"><el-form-item label="配方"><el-input v-model="form.recipe" type="textarea" :rows="2" placeholder="如 巴西日晒60% + 耶加水洗40%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="推荐色值"><el-input-number v-model="form.agtronColor" :min="0" :max="100" :precision="0" :controls="false" style="width: 100%" /><span class="tip">Agtron 0-100（中烘≈58）</span></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="风味"><el-input v-model="form.flavor" placeholder="整体风味描述" /></el-form-item></el-col>
          </template>

          <el-col :span="12"><el-form-item label="默认供货商"><el-select v-model="form.defaultSupplierId" filterable clearable placeholder="选填"><el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-select v-model="form.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </el-card>
</template>
<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px }
.toolbar .el-input { width: 240px }.toolbar .el-select { width: 120px }
.pager { margin-top: 12px; justify-content: flex-end }
.bf .el-select, .bf .el-input-number { width: 100% }
.tip { margin-left: 10px; color: #a3958a; font-size: 12px }
.mtag { margin-right: 6px }
</style>
