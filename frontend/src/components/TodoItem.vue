<template>
  <!-- 一条待办：勾选框 + 文本 + 删除按钮。双击文本可进入编辑态 -->
  <li class="todo-item" :class="{ done: todo.done }">
    <!-- 勾选框：@change 事件里直接调用 $emit，这是"父组件语法糖"的简化写法 -->
    <input
      type="checkbox"
      :checked="todo.done"
      class="check"
      @change="$emit('toggle', todo.id)"
    />

    <!-- 非编辑态：显示文本。双击进入编辑 -->
    <span
      v-if="!editing"
      class="text"
      :class="{ 'text-done': todo.done }"
      @dblclick="startEdit"
    >{{ todo.text }}</span>

    <!-- 编辑态：v-else 显示输入框。回车或失焦保存 -->
    <input
      v-else
      v-model="editText"
      class="edit-input"
      @keyup.enter="finishEdit"
      @blur="finishEdit"
    />

    <!-- 删除按钮：同样直接抛事件，父组件负责真正删除 -->
    <button class="del-btn" @click="$emit('remove', todo.id)">删除</button>
  </li>
</template>

<script setup>
import { ref } from 'vue'

// ===== 知识点：defineProps（父组件"单向传值"进来）=====
// 父组件通过 <TodoItem :todo="item" /> 把一条待办数据传进来。
// 重点：props 是"只读"的！子组件不能直接改 props，
// 想修改只能抛事件让父组件改（这就是"单向数据流"）。
const props = defineProps({
  todo: { type: Object, required: true }   // 一条待办：{ id, text, done }
})
const emit = defineEmits(['toggle', 'remove', 'edit'])

// ===== 编辑态：组件的私有状态 =====
const editing = ref(false)
const editText = ref('')

/** 双击文本：把当前内容放进输入框，切换成编辑态 */
function startEdit() {
  editText.value = props.todo.text
  editing.value = true
}

/** 保存编辑：内容非空才抛给父组件，然后退出编辑态 */
function finishEdit() {
  const value = editText.value.trim()
  if (value) emit('edit', props.todo.id, value)   // 报告"第几条 + 新文本"
  editing.value = false
}
</script>

<style scoped>
/* 单条待办：白底卡片，悬停浮现阴影 */
.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #fff;
  border-radius: 10px;
  margin-bottom: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s;
}

.todo-item:hover {
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.12);
}

/* 勾选框放大一点，好点 */
.check {
  width: 18px;
  height: 18px;
  cursor: pointer;
  flex-shrink: 0;
}

/* 待办文本：可伸长，超长省略 */
.text {
  flex: 1;
  font-size: 15px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: text;
}

/* 已完成：划线 + 变灰（:class 动态绑定生效） */
.text-done {
  color: #bbb;
  text-decoration: line-through;
}

/* 编辑输入框 */
.edit-input {
  flex: 1;
  padding: 6px 10px;
  border: 2px solid #fb7299;
  border-radius: 6px;
  font-size: 15px;
  outline: none;
}

/* 删除按钮：默认灰色，悬停变红 */
.del-btn {
  border: none;
  background: transparent;
  color: #999;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}

.del-btn:hover {
  color: #e34d59;
}
</style>
