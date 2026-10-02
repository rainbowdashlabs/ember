<script setup lang="ts">
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.catalog.title')">
    <p>{{ t('pages.catalog.intro') }}</p>
  </ViewContent>
</template>
