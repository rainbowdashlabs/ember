<script setup lang="ts">
const {t, tm} = useI18n()
const page = 'one'
const subtree = tm('subtree.group')
const key = `pages.${page}.title`
</script>

<template>
  <StorageBackendForm i18n-prefix="storage"/>
  <i18n-t keypath="keypath.text"/>
  <p>{{ t('common.save') }} {{ t('helpCenter.title') }} {{ key }} {{ subtree }}</p>
</template>
