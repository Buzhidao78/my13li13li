<template>
  <!-- 待办清单练习页：纯前端，无后端依赖，数据存浏览器 localStorage -->
  <div class="todo-page">
    <div class="todo-card">
      <h1 class="todo-title">待办清单</h1>

      <!-- ① 输入组件：@add 事件接收新待办内容 -->
      <TodoInput @add="addTodo" />

      <!-- ② 列表组件：传入"过滤后的列表"，接收勾选/删除/编辑事件 -->
      <TodoList
        :todos="filteredTodos"
        @toggle="toggleTodo"
        @remove="removeTodo"
        @edit="editTodo"
      />

      <!-- ③ 底部组件：统计 + 筛选 + 清除已完成 -->
      <TodoFooter
        :remaining="remainingCount"
        :filter="filter"
        :has-completed="hasCompleted"
        @change-filter="filter = $event"
        @clear-completed="clearCompleted"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import TodoInput from '../components/TodoInput.vue'
import TodoList from '../components/TodoList.vue'
import TodoFooter from '../components/TodoFooter.vue'

// ===== 核心思想：状态提升（State Lifting）=====
// 所有待办数据都放在"父组件"这里统一管理（单一数据源），
// 子组件只负责"展示"和"报告操作"。为什么要这样？
// 因为勾选、删除、筛选、统计全都基于同一份数据，
// 数据只有一份，界面才不会出现"两个地方显示不一致"的 bug。

// ===== 知识点 1：ref —— 让普通数据变成"响应式" =====
// ref 是 Vue3 组合式 API 的"响应式基础"。
// 普通 JS 数组：改了变量，页面不会重绘。
// ref 包装的数组：任何地方改了 todos.value，用到它的模板/计算属性自动更新。
const todos = ref([])

// 当前筛选：'all' 全部 / 'active' 未完成 / 'completed' 已完成
const filter = ref('all')

// ===== 知识点 2：computed —— 从已有数据"推导"新值 =====
// 和普通函数最大的区别：带"缓存 + 自动追踪依赖"。
// 只有它依赖的响应式数据变了才重新计算；多次读取不会重复算。
// 下面三个都是"基于 todos / filter 推导出来的派生状态"。

// 按筛选条件过滤出的列表（展示给 TodoList 的数据）
const filteredTodos = computed(() => {
  if (filter.value === 'active') return todos.value.filter((t) => !t.done)
  if (filter.value === 'completed') return todos.value.filter((t) => t.done)
  return todos.value
})

// 还剩几项未完成（底部"还剩 n 项"）
const remainingCount = computed(() => todos.value.filter((t) => !t.done).length)

// 是否存在已完成项（决定"清除已完成"按钮显不显示）
const hasCompleted = computed(() => todos.value.some((t) => t.done))

// ===== 增删改：全部由父组件执行 =====
// 子组件只负责"报告"（emit），真正的数据修改集中在这里。
// 这样不管从哪个入口触发的修改，都走同一套逻辑，不会改乱。

/** 新增：text 来自 TodoInput 的 @add 事件 */
function addTodo(text) {
  // id 用时间戳保证唯一（同一毫秒添加两条的概率可忽略，练习够用）
  todos.value.push({ id: Date.now(), text, done: false })
}

/** 勾选/取消：找到那条，翻转 done */
function toggleTodo(id) {
  const t = todos.value.find((t) => t.id === id)
  if (t) t.done = !t.done
}

/** 删除：用 filter 生成"去掉目标项"的新数组 */
function removeTodo(id) {
  todos.value = todos.value.filter((t) => t.id !== id)
}

/** 编辑：修改文本 */
function editTodo(id, newText) {
  const t = todos.value.find((t) => t.id === id)
  if (t) t.text = newText
}

/** 清除已完成：保留未完成的 */
function clearCompleted() {
  todos.value = todos.value.filter((t) => !t.done)
}

// ===== 知识点 3：watch —— 监听数据变化，做"副作用" =====
// computed 是"算出新值"；watch 是"数据变了，去干一件事"（这里：存 localStorage）。
// 页面刷新后浏览器会清空内存，所以每次列表变化都要同步到 localStorage，
// 这样刷新/关掉浏览器再打开，待办还在——纯前端就能实现"持久化"。
watch(
  todos,                              // 监听谁
  (newTodos) => {                     // 变化后做什么
    localStorage.setItem('todo-list', JSON.stringify(newTodos))
  },
  { deep: true }                      // 深度监听：数组里对象的属性变了也算变化
)

// ===== 知识点 4：生命周期 onMounted —— 页面挂载后初始化 =====
// 页面第一次渲染完成时执行一次。这里从 localStorage 读回上次保存的数据。
onMounted(() => {
  const saved = localStorage.getItem('todo-list')
  if (saved) {
    try {
      todos.value = JSON.parse(saved)   // 把 JSON 字符串还原成数组
    } catch {
      // 本地数据损坏时忽略，用空列表开始（练习代码不打断页面）
    }
  }
})
</script>

<style scoped>
/* 页面容器：顶部留出导航栏高度，卡片居中 */
.todo-page {
  min-height: 100vh;
  padding: 90px 16px 40px;
  box-sizing: border-box;
}

/* 卡片：白底圆角，最大宽度 560px 居中 */
.todo-card {
  max-width: 560px;
  margin: 0 auto;
  padding: 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.todo-title {
  margin: 0 0 20px;
  font-size: 24px;
  color: #333;
  text-align: center;
}
</style>
