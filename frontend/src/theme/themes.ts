/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export interface ThemeColors {
    primary: string
    primaryAccent: string
    secondary: string
    secondaryAccent: string
    info: string
    infoAccent: string
    success: string
    error: string
    bgLight: string
    bgLightAccent: string
    bgDark: string
    bgDarkAccent: string
}

export const THEMES: Record<string, { label: string; colors: ThemeColors }> = {
    ember: {
        label: 'Ember',
        colors: {
            primary: '#FF6421',
            primaryAccent: '#C71100',
            secondary: '#73CEFF',
            secondaryAccent: '#3694FF',
            info: '#c8ab03',
            infoAccent: '#af7501',
            success: '#00C507',
            error: '#ec2929',
            bgLight: '#eaeaea',
            bgLightAccent: '#CFCFCF',
            bgDark: '#212121',
            bgDarkAccent: '#191919',
        },
    },
    midnight: {
        label: 'Midnight',
        colors: {
            primary: '#3668aa',
            primaryAccent: '#0F2Add t440',
            secondary: '#C9A84C',
            secondaryAccent: '#A68932',
            info: '#5B8DB8',
            infoAccent: '#3D6F99',
            success: '#2E8B57',
            error: '#C0392B',
            bgLight: '#E8ECF1',
            bgLightAccent: '#CBD4DE',
            bgDark: '#0D1B2A',
            bgDarkAccent: '#071120',
        },
    },
    fire_red: {
        label: 'Fire Red',
        colors: {
            primary: '#B91C1C',
            primaryAccent: '#7F1D1D',
            secondary: '#D97706',
            secondaryAccent: '#B45309',
            info: '#E8A838',
            infoAccent: '#CA8A04',
            success: '#16A34A',
            error: '#DC2626',
            bgLight: '#F5EDED',
            bgLightAccent: '#E0D0D0',
            bgDark: '#1C1111',
            bgDarkAccent: '#140C0C',
        },
    },
    forest: {
        label: 'Forest',
        colors: {
            primary: '#2D6A4F',
            primaryAccent: '#1B4332',
            secondary: '#8B5E3C',
            secondaryAccent: '#6B4226',
            info: '#A3B18A',
            infoAccent: '#7F9468',
            success: '#40916C',
            error: '#C44536',
            bgLight: '#EDF2E8',
            bgLightAccent: '#D1DACA',
            bgDark: '#1A2418',
            bgDarkAccent: '#111A10',
        },
    },
    cherry_blossom: {
        label: 'Cherry Blossom',
        colors: {
            primary: '#DB7093',
            primaryAccent: '#C2185B',
            secondary: '#9B72CF',
            secondaryAccent: '#7B52AB',
            info: '#E8A0BF',
            infoAccent: '#D4789E',
            success: '#66BB6A',
            error: '#EF5350',
            bgLight: '#FBF0F4',
            bgLightAccent: '#F0D6E0',
            bgDark: '#2A1A22',
            bgDarkAccent: '#1E1018',
        },
    },
    simple: {
        label: 'Simple',
        colors: {
            primary: '#7C8FA6',
            primaryAccent: '#5E7189',
            secondary: '#A6B5C4',
            secondaryAccent: '#8A9DB0',
            info: '#B8C5D0',
            infoAccent: '#95A8B8',
            success: '#81B29A',
            error: '#C97C7C',
            bgLight: '#F0F0F0',
            bgLightAccent: '#DCDCDC',
            bgDark: '#2C2C2C',
            bgDarkAccent: '#222222',
        },
    },
    ocean: {
        label: 'Ocean',
        colors: {
            primary: '#0D7377',
            primaryAccent: '#065A5E',
            secondary: '#E07A5F',
            secondaryAccent: '#C4603F',
            info: '#32AEB1',
            infoAccent: '#1E8D90',
            success: '#2E8B57',
            error: '#D64545',
            bgLight: '#E8F4F4',
            bgLightAccent: '#C9E0E0',
            bgDark: '#0E1F20',
            bgDarkAccent: '#081516',
        },
    },
    sunset: {
        label: 'Sunset',
        colors: {
            primary: '#E07328',
            primaryAccent: '#C45A14',
            secondary: '#8E4585',
            secondaryAccent: '#6E2F68',
            info: '#E8A838',
            infoAccent: '#CA8A04',
            success: '#4CAF50',
            error: '#E53935',
            bgLight: '#F5EDE3',
            bgLightAccent: '#E0D0BE',
            bgDark: '#231A12',
            bgDarkAccent: '#18100A',
        },
    },
    lavender: {
        label: 'Lavender',
        colors: {
            primary: '#7E57C2',
            primaryAccent: '#5E35A1',
            secondary: '#5C9DCA',
            secondaryAccent: '#3F7FAD',
            info: '#B39DDB',
            infoAccent: '#9575CD',
            success: '#66BB6A',
            error: '#EF5350',
            bgLight: '#F3EFF8',
            bgLightAccent: '#DDD4EB',
            bgDark: '#1C162A',
            bgDarkAccent: '#120E1E',
        },
    },
}

export type ThemeKey = keyof typeof THEMES

export const DarkMode = { SYSTEM: 'system', DARK: 'dark', LIGHT: 'light' } as const
export type DarkModeValue = (typeof DarkMode)[keyof typeof DarkMode]
