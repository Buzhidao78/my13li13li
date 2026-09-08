<template>
  <div id="app">
    <!-- 顶部导航栏（全局展示） -->
    <NavBar />
    <!-- 页面内容 -->
    <router-view />
    <!-- 登录弹窗（全局控制，登录状态由 userStore 管理） -->
    <LoginModal v-if="userStore.loginVisible" />
  </div>
</template>

<script setup>
import NavBar from './components/NavBar.vue'
import LoginModal from './components/LoginModal.vue'
import { userStore } from './store/user'
</script>

<style>
/* 全局：让页面滚动条始终存在（即使内容不足一屏也会保留滚动条轨道）。
   这样在"可滚动页面"与"内容不足一屏的页面"之间跳转时，滚动条不会忽隐忽现，
   避免整页因滚动条宽度让出/收回而左右"抖动" */
html {
  overflow-y: scroll;
}

body {
  margin: 0;
  font-family: 'Microsoft YaHei', Arial, sans-serif;
  background: #f7f7f9;
}
</style>
