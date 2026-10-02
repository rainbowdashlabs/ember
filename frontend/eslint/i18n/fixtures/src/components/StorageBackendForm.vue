<script setup lang="ts">
const props = defineProps<{i18nPrefix: string}>()
const {t} = useI18n()
const types = ['S3']
</script>

<template>
  <h2>{{ t(`${props.i18nPrefix}.form.title`) }}</h2>
  <p v-for="type in types" :key="type">{{ t(`${props.i18nPrefix}.form.types.${type.toLowerCase()}`) }}</p>
</template>
