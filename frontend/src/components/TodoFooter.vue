<template>
  <!-- 底部：剩余统计 + 筛选按钮 + 清除已完成 -->
  <div class="todo-footer">
    <span class="count">还剩 {{ remaining }} 项未完成</span>

    <!-- 筛选按钮：v-for 渲染，:class 高亮当前筛选项 -->
    <div class="filters">
      <button
        v-for="f in filters"
        :key="f.value"
        class="filter-btn"
        :class="{ active: f.value === filter }"
        @click="$emit('change-filter', f.value)"
      >{{ f.label }}</button>
    </div>

    <!-- 有已完成项时才显示清除按钮（v-if 条件渲染） -->
    <button v-if="hasCompleted" class="clear-btn" @click="$emit('clear-completed')">
      清除已完成
    </button>
  </div>
</template>

<script setup>
// ===== 底部组件也只"展示 + 报告" =====
// 剩余数量、当前筛选、有没有已完成项，都是父组件算好传进来的（props）。
// 用户点筛选/清除时，通过 emit 告诉父组件"我想干什么"，由父组件真正改数据。
defineProps({
  remaining: { type: Number, default: 0 },
  filter: { type: String, default: 'all' },
  hasCompleted: { type: Boolean, default: false }
})
defineEmits(['change-filter', 'clear-completed'])

// 筛选项配置：value 是传给父组件的值，label 是按钮上显示的文字
const filters = [
  { value: 'all', label: '全部' },
  { value: 'active', label: '未完成' },
  { value: 'completed', label: '已完成' }
]
</script>

<style scoped>
.todo-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #ececf1;
  font-size: 13px;
  color: #888;
}

.count {
  flex-shrink: 0;
}

.filters {
  display: flex;
  gap: 6px;
}

/* 筛选按钮：当前选中的粉色高亮 */
.filter-btn {
  border: none;
  background: transparent;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  color: #fb7299;
}

.filter-btn.active {
  background: #fb7299;
  color: #fff;
}

/* 清除已完成：红色文字按钮 */
.clear-btn {
  border: none;
  background: transparent;
  color: #e34d59;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}

.clear-btn:hover {
  text-decoration: underline;
}
</style>
