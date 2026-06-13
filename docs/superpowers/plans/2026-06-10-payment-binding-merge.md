# Payment Binding Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move project-purpose binding management from its standalone payment tab into the merchant account and secret detail workflow.

**Architecture:** Keep the existing backend binding model and `/api/payment/bindings` endpoints. The frontend loads merchant bindings with `merchantId`, renders them in the merchant detail and merchant edit dialogs, and opens the existing binding editor in a merchant-locked mode. New binding starts from merchant edit, not merchant detail.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Axios, Java 25, Spring Boot 4, MyBatis.

---

### Task 1: Frontend Type Check Baseline

**Files:**
- Read: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web/src/views/payment/PaymentConfigView.vue`
- Read: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web/src/api/payment.ts`
- Read: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web/src/types/payment.ts`

- [ ] **Step 1: Run frontend type/build baseline**

Run:

```bash
cd /Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web
npm run build
```

Expected: Either pass, or fail with pre-existing unrelated errors. Record the exact result before editing.

### Task 2: Merchant Detail Binding State

**Files:**
- Modify: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web/src/views/payment/PaymentConfigView.vue`

- [ ] **Step 1: Add detail-level binding state**

Add state near existing merchant detail state:

```ts
const selectedMerchantBindings = ref<PaymentProjectBinding[]>([])
const detailBindingsLoading = ref(false)
const bindingDialogLockedMerchantId = ref<number | null>(null)
```

- [ ] **Step 2: Load bindings when merchant detail opens**

Update `loadMerchantDetail(id)` so it fetches both merchant detail and the first 1000 bindings for that merchant:

```ts
async function loadMerchantDetail(id: number) {
  detailLoading.value = true
  detailBindingsLoading.value = true
  selectedMerchantId.value = id
  try {
    const [detail, bindingPage] = await Promise.all([
      fetchPaymentMerchantDetail(id),
      fetchPaymentBindings({ merchantId: id, page: 1, pageSize: 1000 }),
    ])
    selectedMerchantDetail.value = detail
    selectedMerchantBindings.value = bindingPage.items
  } catch (error) {
    ElMessage.error('加载商户详情失败')
    console.error(error)
  } finally {
    detailLoading.value = false
    detailBindingsLoading.value = false
  }
}
```

- [ ] **Step 3: Add a focused reload helper for bindings**

Add:

```ts
async function loadSelectedMerchantBindings() {
  if (!selectedMerchantId.value) {
    selectedMerchantBindings.value = []
    return
  }
  detailBindingsLoading.value = true
  try {
    const response = await fetchPaymentBindings({
      merchantId: selectedMerchantId.value,
      page: 1,
      pageSize: 1000,
    })
    selectedMerchantBindings.value = response.items
  } catch (error) {
    ElMessage.error('加载商户项目用途绑定失败')
    console.error(error)
  } finally {
    detailBindingsLoading.value = false
  }
}
```

### Task 3: Move Binding Table Into Merchant Detail

**Files:**
- Modify: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web/src/views/payment/PaymentConfigView.vue`

- [ ] **Step 1: Remove standalone binding tab markup**

Delete the `<el-tab-pane label="项目用途绑定" name="bindings">...</el-tab-pane>` block.

- [ ] **Step 2: Add binding action in merchant edit dialog**

Add an action button in the edit merchant dialog when `editingMerchantId` is set:

```vue
<el-button size="small" type="primary" plain @click="openBindingDialogForSelectedMerchant()">新增绑定</el-button>
```

- [ ] **Step 3: Add binding table in merchant detail**

After the merchant descriptions and before production parameters, add:

```vue
<div class="section-title">项目用途绑定</div>
<el-table v-loading="detailBindingsLoading" :data="selectedMerchantBindings" size="small">
  <el-table-column label="业务" width="130" show-overflow-tooltip>
    <template #default="{ row }">
      {{ row.projectGroup || row.businessLineName || row.businessLine || '-' }}
    </template>
  </el-table-column>
  <el-table-column label="项目" min-width="180" show-overflow-tooltip>
    <template #default="{ row }">
      <div class="binding-project-cell">
        <span class="binding-project-name">{{ row.projectName || '-' }}</span>
        <span class="binding-project-code">{{ row.projectCode || '-' }}</span>
      </div>
    </template>
  </el-table-column>
  <el-table-column label="用途" min-width="160" show-overflow-tooltip>
    <template #default="{ row }">
      {{ formatPurposeNames(row.purposeCodes, row.purposeCode) }}
    </template>
  </el-table-column>
  <el-table-column label="默认" width="70">
    <template #default="{ row }">
      {{ row.defaultBinding ? '是' : '否' }}
    </template>
  </el-table-column>
  <el-table-column label="状态" width="80">
    <template #default="{ row }">
      {{ formatStatus(row.status) }}
    </template>
  </el-table-column>
  <el-table-column label="关联商户" min-width="180" show-overflow-tooltip>
    <template #default="{ row }">
      {{ formatRelationNames(row.relations) }}
    </template>
  </el-table-column>
  <el-table-column label="操作" width="80">
    <template #default="{ row }">
      <el-button link type="primary" @click="openBindingDialogForSelectedMerchant(row)">编辑</el-button>
    </template>
  </el-table-column>
</el-table>
```

- [ ] **Step 4: Add relation formatter**

Add:

```ts
function formatRelationNames(relations: PaymentProjectBinding['relations']) {
  if (!relations?.length) {
    return '-'
  }
  return relations
    .map((relation) => `${formatRelationRole(relation.relationRole)}：${relation.merchantName || relation.merchantCode}`)
    .join('；')
}

function formatRelationRole(role: string) {
  return relationRoleOptions.find((item) => item.code === role)?.name || role
}
```

### Task 4: Merchant-Locked Binding Editor

**Files:**
- Modify: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web/src/views/payment/PaymentConfigView.vue`

- [ ] **Step 1: Add merchant-locked opener**

Add:

```ts
function openBindingDialogForSelectedMerchant(binding?: PaymentProjectBinding) {
  if (!selectedMerchantId.value) {
    ElMessage.warning('请先选择商户')
    return
  }
  openBindingDialog(binding, selectedMerchantId.value)
}
```

- [ ] **Step 2: Update `resetBindingForm` to accept locked merchant**

Change signature and merchant selection:

```ts
function resetBindingForm(lockedMerchantId?: number) {
  editingBindingId.value = null
  bindingDialogLockedMerchantId.value = lockedMerchantId || null
  const firstBusiness = bindingBusinessOptions.value[0]
  const firstMerchant = lockedMerchantId
    ? merchantOptions.value.find((merchant) => merchant.id === lockedMerchantId)
    : merchantOptions.value.find((merchant) => merchant.purposeCodes?.length) || merchantOptions.value[0]
  bindingForm.projectId = firstBusiness?.projectId || 0
  bindingForm.projectGroup = firstBusiness?.group || ''
  bindingForm.merchantId = firstMerchant?.id || 0
  bindingForm.purposeCodes = firstMerchant?.purposeCodes?.[0] ? [firstMerchant.purposeCodes[0]] : []
  bindingForm.priority = 1
  bindingForm.defaultBinding = true
  bindingForm.status = 'ACTIVE'
  bindingForm.remark = ''
  bindingForm.relations = []
}
```

- [ ] **Step 3: Update `openBindingDialog` signature**

Change:

```ts
function openBindingDialog(binding?: PaymentProjectBinding, lockedMerchantId?: number) {
  resetBindingForm(lockedMerchantId)
  if (binding) {
    editingBindingId.value = binding.id
    bindingForm.projectId = binding.projectId
    bindingForm.projectGroup = projects.value.find((project) => project.id === binding.projectId)?.group || ''
    bindingForm.merchantId = lockedMerchantId || binding.merchantId
    bindingForm.purposeCodes = binding.purposeCodes?.length ? [...binding.purposeCodes] : [binding.purposeCode]
    bindingForm.priority = binding.priority
    bindingForm.defaultBinding = binding.defaultBinding
    bindingForm.status = binding.status
    bindingForm.remark = binding.remark || ''
    bindingForm.relations = (binding.relations || []).map((relation) => ({
      merchantId: relation.merchantId,
      relationRole: relation.relationRole,
      relationName: relation.relationName || '',
      priority: relation.priority || 1,
      remark: relation.remark || '',
    }))
    normalizeBindingPurposeCodesForMerchant()
  }
  bindingDialogVisible.value = true
}
```

- [ ] **Step 4: Disable merchant select when locked**

Update the binding dialog merchant select:

```vue
<el-select
  v-model="bindingForm.merchantId"
  filterable
  style="width: 100%"
  :disabled="Boolean(bindingDialogLockedMerchantId)"
  @change="handleBindingMerchantChange"
>
```

- [ ] **Step 5: Ensure save payload uses locked merchant**

Before payload creation in `submitBinding`, add:

```ts
const merchantId = bindingDialogLockedMerchantId.value || bindingForm.merchantId
```

Then set `merchantId` in the payload to that local value.

- [ ] **Step 6: Refresh the correct lists after saving**

After saving a binding:

```ts
await Promise.all([
  loadBindings(),
  loadSelectedMerchantBindings(),
  loadMerchants(),
])
```

### Task 5: Documentation

**Files:**
- Modify: `/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/controller-api.md`

- [ ] **Step 1: Update payment configuration docs**

Change the payment section so it states that project-purpose binding is maintained from the merchant account and secret detail workflow. Keep endpoint descriptions for `/api/payment/bindings` as compatibility and implementation APIs.

### Task 6: Verification

**Files:**
- Verify frontend repo and backend repo.

- [ ] **Step 1: Run frontend build**

```bash
cd /Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web
npm run build
```

Expected: exit 0, unless unrelated pre-existing errors were present in Task 1.

- [ ] **Step 2: Run backend compile**

```bash
cd /Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server
mvn -q -DskipTests compile
```

Expected: exit 0, unless unrelated pre-existing errors are present.

- [ ] **Step 3: Review diffs**

```bash
git -C /Users/aslight/Documents/workspace/IDEAWorkspace/workhub-web diff -- src/views/payment/PaymentConfigView.vue src/api/payment.ts src/types/payment.ts
git -C /Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server diff -- docs/controller-api.md docs/superpowers/specs/2026-06-10-payment-binding-merge-design.md docs/superpowers/plans/2026-06-10-payment-binding-merge.md
```

Expected: only the intended payment binding merge and documentation changes appear in these files.
