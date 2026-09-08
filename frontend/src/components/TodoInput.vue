<template>
  <!-- 输入区：一个输入框 + 一个添加按钮 -->
  <div class="todo-input">
    <input
      v-model="text"
      class="input"
      placeholder="想做点什么？回车或点添加"
      @keyup.enter="submit"
    />
    <button class="add-btn" :disabled="!text.trim()" @click="submit">添加</button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

// ===== 知识点：ref（组件的"私有状态"）=====
// 这个 ref 只存在于"输入组件内部"，父组件看不到它，属于组件的私有状态。
// ref 包装后：text.value 读值 / 赋值，页面上的输入框会自动跟随变化。
const text = ref('')

// ===== 知识点：defineEmits（子组件"向外报告"）=====
// 定义"这个组件可以向外面抛的事件"。父组件用 <TodoInput @add="..."> 接住。
// 待办数据存在父组件（TodoPractice.vue），子组件自己不改数据，
// 只把"用户想添加的内容"抛给父组件，由父组件真正写入列表。
const emit = defineEmits(['add'])

/** 提交添加：去掉首尾空格，空内容不添加，然后抛给父组件并清空输入框 */
function submit() {
  const value = text.value.trim()
  if (!value) return
  emit('add', value)   // 把输入内容"报告"给父组件
  text.value = ''      // 添加完清空输入框，方便连续添加
}
</script>

<style scoped>
.todo-input {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

/* 输入框：圆角白底，聚焦时出现粉色描边 */
.input {
  flex: 1;
  padding: 10px 14px;
  border: 2px solid #e3e3e8;
  border-radius: 10px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}

.input:focus {
  border-color: #fb7299;
}

/* 添加按钮：粉色主题，空内容时置灰（disabled） */
.add-btn {
  padding: 0 22px;
  border: none;
  border-radius: 10px;
  background: #fb7299;
  color: #fff;
  font-size: 15px;
  font-weight: bold;
  cursor: pointer;
  transition: opacity 0.2s;
}

.add-btn:hover {
  opacity: 0.85;
}

.add-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
