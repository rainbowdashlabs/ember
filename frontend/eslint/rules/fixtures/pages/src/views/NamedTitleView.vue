<script setup lang="ts">
const {t} = useI18n()
const catalog = ref<{name: string} | null>(null)
</script>

<template>
  <ViewContent :title="catalog?.name || t('pages.catalog.title')">
    <p>{{ t('pages.catalog.intro') }}</p>
  </ViewContent>
</template>
