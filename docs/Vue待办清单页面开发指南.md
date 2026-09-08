# Vue 待办清单页面开发指南

> 配套项目：practice2 前端（Vue3 + Vite + Vue Router）
> 本文档讲解一个**纯前端练习页面**（待办清单）如何从 0 到 1 写出来：
> **开发路线是什么、为什么这个顺序、每个核心知识点怎么用**。
> 本页面**不需要后端**，数据存在浏览器 `localStorage` 里。

---

## 0. 功能总览：这个页面练什么？

**页面功能**：输入文字添加待办 → 勾选完成 → 双击编辑 → 删除 → 按状态筛选 → 清除已完成 → 刷新页面数据不丢。

**为什么选"待办清单"当练习？** 它是前端界的"Hello World"进阶版：功能简单，但几乎用到了 Vue 所有核心知识点。先把这张"知识点地图"看完，写代码时就明白每一步在练什么：

| 练习到的知识点 | 在这个页面里的位置 | 一句话理解 |
|---|---|---|
| 组合式 API（`script setup`） | 每个组件的 `<script setup>` | 逻辑写在 setup 里，不用旧式 `data/methods` |
| `ref` 响应式 | `todos`、`filter`、`text` | 数据一变，页面自动更新 |
| `computed` 计算属性 | `filteredTodos`、`remainingCount` | 从已有数据"推导"新值，带缓存 |
| `watch` 侦听器 | 监听 `todos` → 存 localStorage | 数据变了，去做一件事（副作用） |
| 生命周期 `onMounted` | 页面挂载后读回本地数据 | 初始化时执行一次的钩子 |
| `v-model` 双向绑定 | 输入框、编辑框、勾选框 | 输入内容和数据自动同步 |
| 事件处理 + 修饰符 | `@click`、`@keyup.enter` | 用户操作触发方法 |
| `v-for` + `:key` 列表渲染 | 渲染每条待办 | 循环渲染，key 帮助 Vue 精确更新 |
| `v-if` / `v-else` 条件渲染 | 空状态提示、编辑态切换 | 条件成立才渲染元素 |
| `:class` 动态样式 | 已完成项划线变灰、筛选高亮 | 根据数据动态加类名 |
| `props` 父子传参 | 父组件把数据传进子组件 | 单向数据流，子组件只读 |
| `emit` 子传父事件 | 子组件把操作"报告"给父组件 | 数据修改统一交给父组件 |
| 状态提升（组件通信核心思想） | 所有数据集中在父组件 | 单一数据源，界面不乱 |

**一句话记住本页面的架构**：**数据在父组件，展示在子组件，子组件通过 props 拿数据、通过 emit 报告操作。**

---

## 1. 开发路线（先想结构，再写代码）

### 第 0 步：先画"组件树"和"数据流"，不要急着敲代码

写代码前，先想清楚两个问题——**谁负责数据、谁负责展示**。

**组件树**（页面由 4 个子组件 + 1 个主页面组成）：

```
TodoPractice.vue（主页面：唯一持有 todos 数据的地方）
 ├── TodoInput.vue    （输入框：把新待办文本抛给父组件）
 ├── TodoList.vue     （列表容器：循环渲染 + 事件转发）
 │    └── TodoItem.vue（单条待办：勾选/编辑/删除）
 └── TodoFooter.vue   （底部：统计 + 筛选 + 清除已完成）
```

**数据流**（最重要的一个图，理解它 Vue 组件通信就通了）：

```
父组件 TodoPractice.vue
    │  ① 用 props 把数据传下去（单向：父 → 子）
    ▼
 TodoInput / TodoList / TodoFooter
    │  ② 子组件改不了数据，只能 emit 抛事件（子 → 父）
    ▼
 父组件收到事件 → 修改 todos → 数据一变，所有用到它的组件自动刷新
```

> **为什么要数据集中在父组件？** 想一想：如果"待办列表"和"底部统计"各自持有一份数据，勾选一条后列表更新了，统计却不知道——界面就"打架"了。**数据只有一份**，全页面自然永远一致。这就是"状态提升"（State Lifting）。

### 第 1 步：写主页面骨架（数据 + 增删改 + 派生状态）

先在 `TodoPractice.vue` 里把**数据层**写完：`todos`（数据）、增删改函数、`computed`（筛选/统计）、`watch`（持久化）。此时页面上只有数据，还没有界面，可以先在脑子里过一遍逻辑。

**为什么主页面先写？** 它是一切数据的"源头"。子组件要展示什么、要抛什么事件，都取决于父组件提供了哪些能力。**先定源头，再写支流。**

### 第 2 步：写子组件（从下往上，从简单到复杂）

按 **TodoInput → TodoItem → TodoList → TodoFooter** 的顺序写：

1. **TodoInput** 最简单：一个 `ref` + 一个 `emit`，先建立"子组件怎么和父组件通信"的手感
2. **TodoItem** 稍复杂：`props` 收数据、`emit` 抛事件，还带编辑态（`v-if/v-else` + `v-model`）
3. **TodoList** 是"中间层"：纯转发，练 `v-for` + 事件二次抛出
4. **TodoFooter** 收尾：练 `props` 展示 + `:class` 动态高亮

**为什么从下往上？** 每个子组件只依赖"父组件传给它的 props 和它要抛的事件"，而这些在画组件树时已经定好了。先写叶子（最小组件），每写完一个就能独立检查，最后主页面把它们"拼"起来。

### 第 3 步：把组件"拼"进主页面模板

数据和方法都齐了，最后在 `TodoPractice.vue` 的 `<template>` 里把三个子组件拼起来，接好事件。此时页面已经能完整运行。

### 第 4 步：接入路由和导航（让页面能被访问）

在 `router/index.js` 加一条 `/todo` 路由，在 `NavBar.vue` 加一个"待办练习"入口。**路由不设置 `requiresAuth`**——纯前端练习页不需要登录。

### 第 5 步：浏览器验证

添加 → 勾选 → 筛选 → 清除 → 刷新，按文末"验证清单"逐项测一遍。

---

## 2. 核心知识点详解

下面按"在代码里出现的顺序"逐个讲透。每个知识点都给出：**是什么 → 代码在哪 → 为什么这样写**。

### 知识点 1：组合式 API 与 `script setup`

**是什么**：Vue3 推荐的写组件方式。组件逻辑（数据、方法、计算属性）直接写在 `<script setup>` 里，不用再像 Vue2 那样分 `data()` / `methods` / `computed` 几块。

```vue
<script setup>
import { ref, computed, watch, onMounted } from 'vue'
// 这里直接写逻辑：定义响应式数据、函数、计算属性
const todos = ref([])
</script>
```

**为什么**：同一条数据的"声明 + 使用 + 修改"能写在一起，逻辑按功能组织而不是按类型硬分块，代码好读好维护。你现有的项目（NavBar、Recharge）全是这种写法，保持一致。

### 知识点 2：`ref` —— 让数据"活"起来

**是什么**：`ref()` 把普通数据包装成"响应式数据"。读取要写 `todos.value`，修改也写 `todos.value = ...`；在 `<template>` 里会自动解包，直接写 `todos` 就行。

```js
const todos = ref([])      // 声明一个响应式数组
const text = ref('')       // 声明一个响应式字符串
```

**为什么需要它？** 这是 Vue 和原生 JS 最大的区别：

- 原生 JS：`let todos = []`，改了数组，页面不会自动变，得自己手动 `render()`
- Vue：改了 `todos.value`，**任何用到 todos 的模板和 computed 自动更新**

**类比**：原生 JS 的变量像"写在黑板上的字"，改了黑板页面不会变；`ref` 像"和投影仪连着的幻灯片"，你改内容，屏幕立刻跟着刷新。

### 知识点 3：`computed` —— 从已有数据"推导"新值

**是什么**：基于别的响应式数据计算出一个新值。用在三个地方：

```js
// ① 按筛选条件过滤列表（展示给 TodoList）
const filteredTodos = computed(() => {
  if (filter.value === 'active') return todos.value.filter((t) => !t.done)
  if (filter.value === 'completed') return todos.value.filter((t) => t.done)
  return todos.value
})

// ② 还剩几项未完成
const remainingCount = computed(() => todos.value.filter((t) => !t.done).length)

// ③ 是否存在已完成项（决定"清除已完成"按钮显不显示）
const hasCompleted = computed(() => todos.value.some((t) => t.done))
```

**和普通函数的区别（重点）**：
1. **自动追踪依赖**：Vue 会自动记住"它读了哪些响应式数据"，这些数据一变，它自动重算
2. **带缓存**：同一个 computed 一帧内多次读取，只算一次。普通函数每次调用都重新算

**为什么用 computed 而不是写个函数？** 模板里要"展示"这些派生值。computed 的缓存特性在数据量大时性能更好，且语义清晰——一看名字就知道"这是一个从数据推导出来的状态"。**记住口诀：要展示的派生值，用 computed；要做的事，用函数或 watch。**

### 知识点 4：`watch` —— 数据变了，去做一件事

**是什么**：监听响应式数据，变化时执行回调。这里用来做"自动保存"：

```js
watch(
  todos,                            // 监听谁
  (newTodos) => {                   // 变化后做什么
    localStorage.setItem('todo-list', JSON.stringify(newTodos))
  },
  { deep: true }                    // 深度监听
)
```

**三个细节必须懂**：
- **为什么是 watch 而不是手动保存？** 添加、勾选、删除、清除四条修改路径，如果每条都手写"保存"，容易漏；用 watch 集中监听，**任何方式改了 todos 都会自动保存**，不会漏。
- **为什么 `{ deep: true }`？** 默认 watch 只监听"引用是否变了"。勾选时改的是**数组里某个对象的 `done` 属性**（`t.done = !t.done`），数组引用没变，浅监听感知不到。`deep: true` 才会深入监听对象内部的变化。
- **localStorage 是什么？** 浏览器的本地存储，存字符串，刷新/关浏览器都在。`JSON.stringify` 把数组转成字符串存入，读时再用 `JSON.parse` 还原。

> **computed 和 watch 怎么分工？** computed 是"数据变 → 算出新值"（纯、可缓存）；watch 是"数据变 → 去执行一个副作用"（存库、发请求、操作 DOM）。**要展示用 computed，要做事用 watch。**

### 知识点 5：生命周期 `onMounted`

**是什么**：组件挂载（第一次渲染到页面上）完成后执行的回调。

```js
onMounted(() => {
  const saved = localStorage.getItem('todo-list')   // 读上次保存的
  if (saved) {
    try {
      todos.value = JSON.parse(saved)               // 还原成数组
    } catch { /* 本地数据损坏时忽略，用空列表开始 */ }
  }
})
```

**为什么在这里初始化？** 页面刚打开时，内存里还没有数据，需要从 localStorage 读回。`onMounted` 保证执行时机是"页面已经渲染出来"之后，此时做初始化不会干扰首屏渲染。

**生命周期顺序（记住这张表）**：

```
创建组件 → setup() 里同步代码执行 → 挂载到页面 → onMounted 回调执行 → ...（用户交互）→ 销毁
```

### 知识点 6：`v-model` 双向绑定

**是什么**：把"输入框的值"和"响应式数据"绑在一起，双向同步：数据变 → 输入框变；用户输入 → 数据变。

```html
<!-- TodoInput.vue：输入框内容和 text 双向绑定 -->
<input v-model="text" placeholder="想做点什么？" />

<!-- TodoItem.vue：编辑框内容和 editText 双向绑定 -->
<input v-model="editText" class="edit-input" @keyup.enter="finishEdit" />

<!-- TodoItem.vue：勾选框和 todo.done 双向绑定 -->
<input type="checkbox" :checked="todo.done" @change="$emit('toggle', todo.id)" />
```

**本质拆解**：`v-model` 是 `:value` + `@input` 的语法糖——`v-model="text"` 等价于 `:value="text" @input="text = $event.target.value"`。理解这一点后，你就能看懂"自定义组件的 v-model"（见进阶方向）。

> **注意勾选框为什么不用 `v-model="todo.done"`？** 因为 `todo` 是 props 传进来的**只读数据**，子组件不能直接改。所以这里用 `:checked` 只读展示 + `@change` 抛事件，让父组件去改——**这就是"单向数据流"的体现**。

### 知识点 7：事件处理与修饰符

**是什么**：`@click="方法"` 监听点击；`@keyup.enter="方法"` 监听"按回车键抬起"这个动作。

```html
<button class="add-btn" @click="submit">添加</button>
<input v-model="text" @keyup.enter="submit" />   <!-- 回车也能添加 -->
```

**修饰符 `.enter`**：`@keyup` 本来任何按键都会触发，加上 `.enter` 只在按回车时触发。Vue 提供一堆常用修饰符（`.enter`、`.trim`、`.stop` 阻止冒泡、`.prevent` 阻止默认行为），**不用自己写 `if (event.key === 'Enter')` 这种判断**。

### 知识点 8：`v-for` 列表渲染 + `:key`

**是什么**：遍历数组渲染列表。每条待办渲染成一个 `TodoItem` 组件：

```html
<TodoItem
  v-for="item in todos"
  :key="item.id"          <!-- key 必须唯一 -->
  :todo="item"
  @toggle="$emit('toggle', item.id)"
/>
```

**`:key` 为什么必须有？** 它给每条数据一个"身份证"。Vue 靠 key 判断"这一条是新增、删除还是只改了内容"，从而**只精确更新变化的那一条**，而不是整列推倒重建。没有 key（或用索引当下标），增删时会出现状态错乱（比如勾选一条，其他条的位置跟着变）。

**为什么用 `item.id` 而不用数组下标 `index`？** id 是数据的"真实身份"，删除中间一条后下标会变，但 id 不会变。**用不会变的唯一值做 key，永远正确。**

### 知识点 9：`v-if` / `v-else` 条件渲染

**是什么**：条件成立才渲染元素。用在两处：

```html
<!-- ① TodoList：列表为空时显示提示 -->
<li v-if="todos.length === 0" class="empty">暂无待办，先添加一条吧</li>

<!-- ② TodoItem：非编辑态显示文本，编辑态（v-else）显示输入框 -->
<span v-if="!editing" class="text" @dblclick="startEdit">{{ todo.text }}</span>
<input v-else v-model="editText" class="edit-input" @keyup.enter="finishEdit" />
```

**`v-if` 和 `v-show` 的区别（常考面试题）**：
- `v-if`：不成立就**不渲染**这个元素（销毁/重建），适合"不常切换"的场景（如编辑态）
- `v-show`：元素**一直渲染**，只是 CSS `display:none` 隐藏，适合"频繁切换"的场景（如选项卡）

### 知识点 10：`:class` 动态样式

**是什么**：根据数据动态绑定类名。对象写法：`类名: 条件`，条件为真就加上这个类。

```html
<!-- TodoItem：完成的待办加 .done 类（整条变灰）+ 文本加 .text-done（划线） -->
<li class="todo-item" :class="{ done: todo.done }">
  <span class="text" :class="{ 'text-done': todo.done }">{{ todo.text }}</span>
</li>

<!-- TodoFooter：当前选中的筛选按钮高亮 -->
<button class="filter-btn" :class="{ active: f.value === filter }">{{ f.label }}</button>
```

```css
.text-done { color: #bbb; text-decoration: line-through; }  /* 划线变灰 */
```

**为什么不用三元表达式？** 对象语法一眼能看清"哪些条件下加哪些类"，多个类时比三元写法清爽得多。**记住：样式跟着数据走，数据一变化样式自动切换。**

### 知识点 11：`props` —— 父组件传值给子组件（单向数据流）

**是什么**：父组件通过 `:名字="值"` 传数据，子组件用 `defineProps` 声明接收。

```js
// 父组件（TodoPractice.vue）：把过滤后的列表传下去
<TodoList :todos="filteredTodos" @toggle="toggleTodo" />

// 子组件（TodoList.vue）：声明接收
defineProps({
  todos: { type: Array, default: () => [] }   // 类型 + 默认值
})

// 子组件（TodoItem.vue）：带校验的声明
const props = defineProps({
  todo: { type: Object, required: true }      // required: 必须传
})
```

**铁律（单向数据流）**：**props 是只读的，子组件绝对不能改 props。** 想改数据，只能抛事件让父组件改。为什么？如果子组件能随便改父组件的数据，多个子组件同时改一份数据，bug 会非常难查。**数据修改权集中在父组件，是 Vue（也是 React）共同的设计哲学。**

### 知识点 12：`emit` —— 子组件"报告"给父组件

**是什么**：子组件定义一个事件名，用 `emit('事件名', 参数)` 抛出去，父组件用 `@事件名="方法"` 接住。

```js
// 子组件定义能抛哪些事件
const emit = defineEmits(['add'])
// 抛出：把输入内容带给父组件
function submit() {
  const value = text.value.trim()
  if (!value) return
  emit('add', value)     // 第 1 个参数：事件名；后面：携带的数据
  text.value = ''
}
```

```html
<!-- 父组件接住事件，参数自动传给方法 -->
<TodoInput @add="addTodo" />
```

```js
function addTodo(text) {   // 这里的 text 就是子组件 emit 传来的 value
  todos.value.push({ id: Date.now(), text, done: false })
}
```

**类比**：子组件像"前台服务员"，自己不做决定（不改数据），客人要什么就拿起电话（emit）报告给后台（父组件），后台决定做不做。这样整个餐厅的决策权统一，账目不会乱。

### 知识点 13：事件转发（中间层组件的特殊写法）

TodoList 是"中间层"：它收到 TodoItem 的事件后，**原样再抛给父组件**：

```html
<TodoItem
  @toggle="$emit('toggle', item.id)"        <!-- 接住下面抛的，再抛给上面 -->
  @edit="$emit('edit', item.id, $event)"    <!-- $event 就是子组件传的参数 -->
/>
```

**为什么需要这层"传话筒"？** 父组件只关心"哪条待办被勾选了"，不关心事件是 TodoItem 直接抛的还是绕了一圈。TodoList 负责**组装列表**（循环、空状态），TodoItem 负责**单条交互**——各管一层，职责清晰。`$event` 是 Vue 内置的"事件参数"变量，直接转发时用它最省事。

---

## 3. 完整数据流回顾（所有知识点串起来）

以"用户勾选一条待办"为例，走一遍完整链路：

```
① 用户点击 TodoItem 的勾选框
② TodoItem 检测到 @change，执行 $emit('toggle', item.id)   ← 知识点 12
③ TodoList 接住事件，转发 $emit('toggle', item.id)          ← 知识点 13
④ 父组件执行 toggleTodo(id)，修改 todos 里那条的 done        ← 状态提升
⑤ todos 变了，三个地方自动更新：
   - TodoList 重新渲染（勾选样式变化）                      ← 响应式
   - remainingCount 重新计算（"还剩 1 项"→"还剩 0 项"）      ← 知识点 3
   - hasCompleted 变化（"清除已完成"按钮出现）               ← 知识点 3
⑥ watch 检测到 todos 变化（deep），自动保存到 localStorage   ← 知识点 4
⑦ 刷新页面 → onMounted 从 localStorage 读回，数据不丢        ← 知识点 5
```

**发现规律了吗？** 用户操作永远只发生在最底层组件，数据修改永远发生在最顶层，中间靠 emit 逐层上报、靠 props 逐层下发。**理解了这条链路，就等于理解了 Vue 组件化开发的全部核心。**

---

## 4. 易错点（写这个页面最容易踩的坑）

### 坑 1：忘了 `props` 是只读的，直接在子组件改它
- **错**：`props.todo.done = true`（子组件直接改数据）
- **为什么不行**：违反单向数据流，多个组件改同一份数据会乱
- **对**：抛事件 `emit('toggle', id)`，让父组件改

### 坑 2：`v-for` 忘了写 `:key`，或拿 `index` 当 key
- **现象**：勾选/删除后，其他条目的状态"串位"
- **对**：用数据里不会变的唯一字段（id）做 key

### 坑 3：watch 改了对象属性但没加 `{ deep: true }`，不触发
- **现象**：勾选待办，localStorage 没更新，刷新后数据丢了
- **原因**：默认只监听"引用变化"，改对象内部属性感知不到
- **对**：`watch(todos, fn, { deep: true })`

### 坑 4：空字符串也添加进去
- **现象**：输入框只有空格，点添加也加了一条空白待办
- **对**：`text.value.trim()` 去掉首尾空格，为空就 `return`，按钮也加 `:disabled="!text.trim()"`

### 坑 5：在子组件里用 `todo.text` 直接赋值
- **现象**：报错 `todo is not defined` 或读不到值
- **原因**：`defineProps` 的返回值要接住：`const props = defineProps({...})`，脚本里用 `props.todo.text`（模板里可以省略 `props.` 前缀）

---

## 5. 验证清单（怎么知道自己写对了）

- [ ] 输入"学习 Vue"，点"添加"或按回车，列表出现一条待办，输入框自动清空
- [ ] 输入只有空格，添加按钮置灰，点不了
- [ ] 勾选一条待办 → 文字划线变灰，底部"还剩 n 项"自动减 1
- [ ] 双击待办文本 → 变成输入框，改内容回车 → 文本更新
- [ ] 点"已完成"筛选 → 只显示已完成的；点"全部" → 全部显示
- [ ] 点"清除已完成" → 已完成项消失，未完成保留；没有已完成项时该按钮不显示
- [ ] 删除一条 → 列表减少，统计同步更新
- [ ] 刷新页面（F5）→ 待办还在（localStorage 生效）
- [ ] 浏览器 Network 面板：没有任何 API 请求（纯前端页面）

---

## 6. 进阶方向（练完这个页面再往深处走）

1. **自定义组件的 `v-model`**：用 `defineModel` 让 TodoItem 支持 `v-model="todo.done"` 这样直接用，理解"v-model 是 :value + @input 语法糖"的延伸。Vue 3.4+ 已支持。
2. **插槽 slot**：给 TodoList 加一个"空状态"插槽，让父组件自定义空列表的提示内容，理解"组件的内容也可以由父组件注入"。
3. **状态管理 Pinia**：这个页面数据量小，集中在父组件就够了；当数据被多个页面共享（比如全局购物车）时，把状态提升到 Pinia store，理解"状态提升"的终极形态。
4. **过渡动画**：用 `<TransitionGroup>` 给增删的待办加淡入淡出/滑动动画，理解 Vue 内置的过渡机制。
5. **组件缓存 `computed` 复用**：把 `filteredTodos` 之类的纯函数抽到 `composables/useTodo.js`（组合式函数），实现跨组件复用，理解 Vue3 逻辑复用的精髓。
6. **数据校验**：给 todos 加"空内容提示""最多 100 条"等校验，练习完整的表单交互。

---

## 7. 一句话总结

**这个页面的开发路线 = 先画组件树和数据流 → 主页面写数据层（ref + 增删改 + computed + watch）→ 子组件从下往上写（props 拿数据 + emit 报操作）→ 模板拼装 → 接入路由 → 浏览器验证。**

**Vue 的核心就三句话：**
> - **数据驱动**：改 `ref` 的数据，界面自动跟着变
> - **派生状态用 computed，副作用用 watch**：要展示的算出来，要做的去执行
> - **props 下、emit 上，数据修改权集中在父组件**：单向数据流，界面永不自相矛盾
