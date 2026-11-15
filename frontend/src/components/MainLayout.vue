<script setup lang="ts">
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <Sidebar />
    <div class="ml-64 min-h-screen">
      <Header />
      <main class="p-6">
        <slot />
      </main>
    </div>
  </div>
</template>
