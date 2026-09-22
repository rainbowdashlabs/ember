/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {forms, memberGroups, profileFields, userTags} from '@/api'
import {ResultDimension, type FormAnalytics, type FormResultGroup, type ResultFilter, type ResultGrouping} from '@/api/forms'
import {FieldTypes, type ProfileField} from '@/api/profileFields'
import {StationUserType, StationUserTypeLabels, type MemberGroup, type UserTag} from '@/api/types'
import {darkThemeActive} from '@/util/themeState'
import {neutralSeriesColor, seriesColor, SERIES_LIMIT} from '@/util/seriesPalette'
import type {GroupSeries} from './groupedChart'
import {decodeView, encodeView, NO_VALUE_GROUP, toQuery} from './resultQuery'

/** The kinds of profile field results can be filtered and grouped by. */
const GROUPABLE_FIELD_TYPES: string[] = [FieldTypes.ENUM, FieldTypes.BOOLEAN, FieldTypes.NUMBER]

/**
 * The filtered and grouped view of a form's results.
 *
 * <p>Holds what the reader has chosen, keeps it in the address so the view can be bookmarked, asks
 * the server for the results whenever the choice changes, and names and colours the groups that
 * come back. A group keeps its colour while the filter changes, because its colour follows where it
 * stands among all groups of its kind rather than where it lands in the answer.
 *
 * @param formId  the form
 * @param enabled whether the form's results can be grouped at all, which only internal forms can
 */
export function useResultView(formId: Ref<number>, enabled: Ref<boolean>) {
    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    const initial = decodeView(route.query.view)
    const filter = ref<ResultFilter>(initial.filter)
    const grouping = ref<ResultGrouping | null>(initial.grouping)
    const groups = ref<MemberGroup[]>([])
    const tags = ref<UserTag[]>([])
    const fields = ref<ProfileField[]>([])
    const narrowed = ref<FormAnalytics | null>(null)
    const querying = ref(false)

    const groupableFields = computed(() => fields.value.filter(field => GROUPABLE_FIELD_TYPES.includes(field.fieldType ?? '')))
    const groupedField = computed(() => fields.value.find(field => field.id === grouping.value?.fieldId))

    async function loadChoices() {
        if (!enabled.value) return
        const [g, tg, f] = await Promise.all([memberGroups.listGroups(), userTags.listTags(), profileFields.listFields()])
        groups.value = g
        tags.value = tg
        fields.value = f
    }

    async function refresh() {
        const query = toQuery(filter.value, grouping.value)
        if (!enabled.value || !query) {
            narrowed.value = null
            return
        }
        querying.value = true
        try {
            narrowed.value = await forms.queryAnalytics(formId.value, query)
        } finally {
            querying.value = false
        }
    }

    watch([filter, grouping], () => {
        const view = encodeView(filter.value, grouping.value)
        void router.replace({query: {...route.query, view}})
        void refresh()
    }, {deep: true})

    /** Every key a group of the current grouping can have, in the order they are shown elsewhere. */
    const colorOrder = computed<string[]>(() => {
        switch (grouping.value?.by) {
            case ResultDimension.USER_TYPE: return Object.values(StationUserType)
            case ResultDimension.GROUP: return groups.value.map(g => String(g.id))
            case ResultDimension.TAG: return tags.value.map(tag => String(tag.id))
            case ResultDimension.FIELD: return groupedField.value?.fieldType === FieldTypes.BOOLEAN
                ? ['true', 'false']
                : ((groupedField.value?.config?.options as string[] | undefined) ?? [])
            default: return []
        }
    })

    function groupName(group: FormResultGroup): string {
        const by = grouping.value?.by
        if (group.key === NO_VALUE_GROUP) return t(`forms.analytics.grouping.none.${by ?? 'GROUP'}`)
        if (by === ResultDimension.USER_TYPE) return StationUserTypeLabels[group.key as keyof typeof StationUserTypeLabels] ?? group.key
        if (by === ResultDimension.FIELD && groupedField.value?.fieldType === FieldTypes.BOOLEAN) {
            return group.key === 'true' ? t('forms.analytics.grouping.yes') : t('forms.analytics.grouping.no')
        }
        return group.label || group.key
    }

    /** The groups as the charts draw them, or null when there are more than colours to tell them apart. */
    const series = computed<GroupSeries[] | null>(() => {
        const answer = narrowed.value
        if (!answer || !grouping.value) return null
        if (answer.groups.length > SERIES_LIMIT) return null
        const dark = darkThemeActive.value
        const slotOf = (key: string) => colorOrder.value.indexOf(key)
        const fixedSlots = answer.groups.every(group =>
            group.key === NO_VALUE_GROUP || (slotOf(group.key) >= 0 && slotOf(group.key) < SERIES_LIMIT))
        return answer.groups.map((group, index) => {
            const color = group.key === NO_VALUE_GROUP
                ? neutralSeriesColor(dark)
                : seriesColor(fixedSlots ? slotOf(group.key) : index, dark) ?? neutralSeriesColor(dark)
            return {key: group.key, name: groupName(group), color, responseCount: group.responseCount}
        })
    })

    function reset() {
        filter.value = decodeView(undefined).filter
        grouping.value = null
    }

    return {
        filter,
        grouping,
        groups,
        tags,
        groupableFields,
        narrowed,
        querying,
        series,
        groupName,
        loadChoices,
        refresh,
        reset,
    }
}
