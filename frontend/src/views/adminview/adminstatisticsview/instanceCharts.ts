/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useThemePaint} from '@/composables/useThemePaint'
import {bottomLegend, cartesianGrid, chartTitle, DONUT_CENTER, DONUT_RADIUS} from '@/util/chartLayout'

/** The colours a chart writes its text, axes and grid in for the current theme. */
export interface ChartPaint {
    text: string
    muted: string
    split: string
}

/** One named series of a chart, with the colour that stays with it. */
export interface ChartSeries {
    name: string
    data: number[]
    color: string
}

/** One slice of a ring chart. */
export interface ChartSlice {
    name: string
    value: number
    color: string
}

/**
 * The categorical order the statistics pages give their series, the same one the station statistics
 * use. A series keeps its place in it whatever else is drawn beside it.
 */
export const CATEGORY_COLORS = ['#FF6421', '#73CEFF', '#00C507', '#ffdd1b', '#3694FF', '#C71100'] as const

/**
 * The colour of the series at a place in the categorical order.
 *
 * @param index the series' place, counted from zero
 */
export function categoryColor(index: number): string {
    return CATEGORY_COLORS[index % CATEGORY_COLORS.length] ?? CATEGORY_COLORS[0]
}

/** Present, absent and declined, the colours every attendance chart uses. */
export const ATTENDANCE_COLORS = {present: '#00C507', absent: '#ec2929', declined: '#3694FF'} as const

/**
 * The chart colours of the current theme, following it as it changes.
 */
export function useChartPaint() {
    const {dark} = useThemePaint()
    return computed<ChartPaint>(() => ({
        text: dark.value ? '#e0e0e0' : '#333333',
        muted: dark.value ? '#9ca3af' : '#666666',
        split: dark.value ? '#333' : '#e0e0e0',
    }))
}

/**
 * A line or bar chart over categories, one y axis, with a legend below once it carries two series.
 *
 * <p>Stacked lines are filled, so the share of each series reads as a band; a single line keeps a
 * faint fill under it. Many points drop their markers and keep them only under the pointer.
 *
 * @param kind       lines or bars
 * @param title      the heading
 * @param categories the labels along the x axis
 * @param series     the series, in their fixed colour order
 * @param paint      the theme's colours
 * @param stacked    whether the series add up
 */
export function seriesChart(
    kind: 'line' | 'bar',
    title: string,
    categories: string[],
    series: ChartSeries[],
    paint: ChartPaint,
    stacked = false,
) {
    const legend = series.length > 1
    const rotated = categories.length > 12
    return {
        aria: {enabled: true},
        title: chartTitle(title, paint.text),
        tooltip: {trigger: 'axis'},
        ...(legend ? {legend: bottomLegend(paint.muted)} : {}),
        grid: cartesianGrid({title: true, legend, rotatedLabels: rotated, left: 50}),
        xAxis: {
            type: 'category',
            data: categories,
            axisLabel: {color: paint.muted, fontSize: 10, rotate: rotated ? 45 : 0},
            axisLine: {lineStyle: {color: paint.muted}},
        },
        yAxis: {
            type: 'value',
            minInterval: 1,
            axisLabel: {color: paint.muted},
            axisLine: {lineStyle: {color: paint.muted}},
            splitLine: {lineStyle: {color: paint.split}},
        },
        series: series.map(s => ({
            name: s.name,
            type: kind,
            data: s.data,
            color: s.color,
            ...(stacked ? {stack: 'total'} : {}),
            ...(kind === 'line' ? {
                showSymbol: categories.length <= 31,
                symbolSize: 6,
                lineStyle: {width: 2},
                areaStyle: {opacity: stacked ? 0.55 : 0.15},
            } : {}),
        })),
    }
}

/**
 * A ring chart with its slices named on leader lines.
 *
 * <p>Like every chart here it carries an ECharts description for screen readers, which names the
 * series and their values, since the canvas alone says nothing to one.
 *
 * @param title  the heading
 * @param slices the slices with their colours
 * @param paint  the theme's colours
 */
export function donutChart(title: string, slices: ChartSlice[], paint: ChartPaint) {
    return {
        aria: {enabled: true},
        title: chartTitle(title, paint.text),
        tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
        series: [{
            type: 'pie',
            radius: DONUT_RADIUS,
            center: DONUT_CENTER,
            label: {color: paint.muted},
            data: slices.map(s => ({name: s.name, value: s.value, itemStyle: {color: s.color}})),
        }],
    }
}
