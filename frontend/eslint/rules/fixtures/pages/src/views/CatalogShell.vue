<script setup lang="ts">
defineProps<{
  heading: string
}>()
</script>

<template>
  <ViewContent :title="heading">
    <slot/>
  </ViewContent>
</template>
