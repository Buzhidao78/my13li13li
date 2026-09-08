<template>
  <!-- 列表区：负责"循环渲染"和"事件转发"，本身不持有任何待办数据 -->
  <ul class="todo-list">
    <!-- ===== 知识点：v-for 列表渲染 + :key =====
         遍历父组件传进来的 todos 数组，每个元素渲染一个 TodoItem。
         :key 必须唯一（用 id）：让 Vue 能"认出"每一条，
         增删时只精确操作变了的那条，而不是整列重建（性能 + 状态不丢）。 -->
    <!-- 关键：内联 handler 用解构 (id, value) 接住子组件 emit 的两个参数，
         避免误用 $event（Vue 中 $event 默认是 emit 的第一个参数，会把 id 当成 newText） -->
    <TodoItem
      v-for="item in todos"
      :key="item.id"
      :todo="item"
      @toggle="$emit('toggle', item.id)"
      @remove="$emit('remove', item.id)"
      @edit="(id, value) => $emit('edit', id, value)"
    />

    <!-- ===== 知识点：v-if 条件渲染 =====
         列表为空时才显示这句提示（不占空间）。
         v-if 是"真的创建/销毁元素"，和 v-show（只是隐藏）不同。 -->
    <li v-if="todos.length === 0" class="empty">暂无待办，先添加一条吧</li>
  </ul>
</template>

<script setup>
import TodoItem from './TodoItem.vue'

// ===== 中间层组件的作用 =====
// TodoList 是一个"纯转发"组件：
//  - 用 props 接收父组件过滤后的列表（只读展示，不改数据）
//  - 把 TodoItem 抛上来的事件原样转发给父组件（用 $emit 二次抛出）
// 这样父组件（数据源）和最小单元（TodoItem）之间隔一层，
// 列表的展示逻辑（循环、空状态）和单条逻辑（勾选、编辑）就解耦了。
defineProps({
  todos: { type: Array, default: () => [] }
})
defineEmits(['toggle', 'remove', 'edit'])
</script>

<style scoped>
.todo-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* 空状态提示 */
.empty {
  padding: 30px 0;
  text-align: center;
  color: #bbb;
  font-size: 14px;
}
</style>
