<template>
  <svg class="brand-logo" :viewBox="compact ? '218 16 680 680' : '238 110 1684 494'" role="img" aria-label="水慧云" xmlns="http://www.w3.org/2000/svg">
    <title>水慧云</title>
    <image href="/brand/shuihuiyun-original.png" width="2172" height="724" />
  </svg>
</template>

<script setup>
defineProps({ compact: { type: Boolean, default: false } })
</script>

<style scoped>
.brand-logo { display: block; width: 100%; height: auto; overflow: hidden; }
</style>
