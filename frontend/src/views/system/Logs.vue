<script setup lang="ts">
import {ref,onMounted} from 'vue'
import MainLayout from '@/components/MainLayout.vue'
import request from '@/utils/request'
const kind=ref('login'), userId=ref(''), start=ref(''), end=ref(''), operation=ref(''), module=ref('')
const rows=ref<any[]>([]), message=ref(''), loading=ref(false)
async function search(){
 loading.value=true; message.value=''
 try {
  const params:any={};if(userId.value)params.userId=Number(userId.value);if(start.value)params.startTime=start.value;if(end.value)params.endTime=end.value
  if(kind.value==='operation'){if(module.value)params.module=module.value;if(operation.value)params.operation=operation.value}
  rows.value=await request.get<any[]>(`/${kind.value}-logs`,{params})
 }catch(e:any){rows.value=[];message.value=e.message||'日志查询失败'}finally{loading.value=false}
}
onMounted(search)
</script>
<template><MainLayout><h1 class="text-2xl font-bold mb-6">系统日志</h1>
<div class="card p-5"><div class="grid grid-cols-3 gap-4">
<label class="field-label">日志类型<select v-model="kind" @change="search" class="input"><option value="login">登录日志</option><option value="operation">操作日志</option></select></label>
<label class="field-label">用户ID<input v-model="userId" type="number" class="input"></label>
<label class="field-label">开始日期<input v-model="start" type="date" class="input"></label>
<label class="field-label">结束日期<input v-model="end" type="date" class="input"></label>
<label v-if="kind==='operation'" class="field-label">模块<input v-model="module" class="input"></label>
<label v-if="kind==='operation'" class="field-label">操作类型<input v-model="operation" class="input"></label>
</div><button class="btn-primary mt-4" :disabled="loading" @click="search">查询日志</button>
<p v-if="message" role="alert" class="text-red-600">{{message}}</p><p class="my-4">查询结果：{{rows.length}} 条</p>
<table class="w-full"><thead><tr><th>ID</th><th>用户ID</th><th>用户名</th><th>模块/登录类型</th><th>操作/状态</th><th>时间</th></tr></thead><tbody><tr v-for="r in rows" :key="r.id"><td>{{r.id}}</td><td>{{r.userId}}</td><td>{{r.username}}</td><td>{{r.module||r.loginType}}</td><td>{{r.operation||r.status}}</td><td>{{r.loginTime||r.operateTime}}</td></tr></tbody></table>
</div></MainLayout></template>
