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
body {
  margin: 0;
  font-family: 'Microsoft YaHei', Arial, sans-serif;
  background: #f7f7f9;
}
</style>
