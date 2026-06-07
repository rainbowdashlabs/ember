/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export const Feel = { ROUNDED: 'ROUNDED', CORNERS: 'CORNERS' } as const
export type FeelValue = (typeof Feel)[keyof typeof Feel]

export const FEEL_RADIUS: Record<FeelValue, string> = {
    ROUNDED: '0.5rem',
    CORNERS: '0.125rem',
}

export interface ModeColors {
    primary: string
    primaryAccent: string
    secondary: string
    secondaryAccent: string
    info: string
    infoAccent: string
    success: string
    error: string
}

export interface ThemeColors {
    light: ModeColors
    dark: ModeColors
    bgLight: string
    bgLightAccent: string
    bgDark: string
    bgDarkAccent: string
}


export interface ThemeDefinition {
    label: string
    colors: ThemeColors
    supportedFeels: FeelValue[]
}

export const THEMES: Record<string, ThemeDefinition> = {
    ember: {
        label: 'Ember',
        colors: {
            light: {
                primary: '#fd4f00',
                primaryAccent: '#C71100',
                secondary: '#092e6e',
                secondaryAccent: '#3694FF',
                info: '#c8ab03',
                infoAccent: '#af7501',
                success: '#00C507',
                error: '#ec2929',
            },
            dark: {
                primary: '#ff7030',
                primaryAccent: '#e04400',
                secondary: '#1a4a90',
                secondaryAccent: '#60b4ff',
                info: '#e0c420',
                infoAccent: '#c89810',
                success: '#30e038',
                error: '#f05050',
            },
            bgLight: '#eaeaea',
            bgLightAccent: '#CFCFCF',
            bgDark: '#212121',
            bgDarkAccent: '#191919',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    midnight: {
        label: 'Midnight',
        colors: {
            light: {
                primary: '#3668aa',
                primaryAccent: '#0F2A99',
                secondary: '#C9A84C',
                secondaryAccent: '#A68932',
                info: '#5B8DB8',
                infoAccent: '#3D6F99',
                success: '#2E8B57',
                error: '#C0392B',
            },
            dark: {
                primary: '#5088cc',
                primaryAccent: '#3060aa',
                secondary: '#ddc060',
                secondaryAccent: '#c0a048',
                info: '#78aad0',
                infoAccent: '#5890b8',
                success: '#40a870',
                error: '#e05040',
            },
            bgLight: '#E8ECF1',
            bgLightAccent: '#CBD4DE',
            bgDark: '#0D1B2A',
            bgDarkAccent: '#071120',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    fire_red: {
        label: 'Fire Red',
        colors: {
            light: {
                primary: '#B91C1C',
                primaryAccent: '#7F1D1D',
                secondary: '#D97706',
                secondaryAccent: '#B45309',
                info: '#E8A838',
                infoAccent: '#CA8A04',
                success: '#16A34A',
                error: '#DC2626',
            },
            dark: {
                primary: '#d83838',
                primaryAccent: '#a02828',
                secondary: '#f09020',
                secondaryAccent: '#d07010',
                info: '#f0c050',
                infoAccent: '#dda420',
                success: '#30c060',
                error: '#f04040',
            },
            bgLight: '#F5EDED',
            bgLightAccent: '#E0D0D0',
            bgDark: '#1C1111',
            bgDarkAccent: '#140C0C',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    forest: {
        label: 'Forest',
        colors: {
            light: {
                primary: '#2D6A4F',
                primaryAccent: '#1B4332',
                secondary: '#8B5E3C',
                secondaryAccent: '#6B4226',
                info: '#A3B18A',
                infoAccent: '#7F9468',
                success: '#40916C',
                error: '#C44536',
            },
            dark: {
                primary: '#48907a',
                primaryAccent: '#306858',
                secondary: '#b08060',
                secondaryAccent: '#906840',
                info: '#c0ccaa',
                infoAccent: '#a0b488',
                success: '#58b088',
                error: '#e06050',
            },
            bgLight: '#EDF2E8',
            bgLightAccent: '#D1DACA',
            bgDark: '#1A2418',
            bgDarkAccent: '#111A10',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    cherry_blossom: {
        label: 'Cherry Blossom',
        colors: {
            light: {
                primary: '#DB7093',
                primaryAccent: '#C2185B',
                secondary: '#9B72CF',
                secondaryAccent: '#7B52AB',
                info: '#E8A0BF',
                infoAccent: '#D4789E',
                success: '#66BB6A',
                error: '#EF5350',
            },
            dark: {
                primary: '#e890aa',
                primaryAccent: '#d84080',
                secondary: '#b890e0',
                secondaryAccent: '#9870c8',
                info: '#f0b8d0',
                infoAccent: '#e098b8',
                success: '#80d084',
                error: '#f07070',
            },
            bgLight: '#FBF0F4',
            bgLightAccent: '#F0D6E0',
            bgDark: '#2A1A22',
            bgDarkAccent: '#1E1018',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    simple: {
        label: 'Simple',
        colors: {
            light: {
                primary: '#7C8FA6',
                primaryAccent: '#5E7189',
                secondary: '#A6B5C4',
                secondaryAccent: '#8A9DB0',
                info: '#B8C5D0',
                infoAccent: '#95A8B8',
                success: '#81B29A',
                error: '#C97C7C',
            },
            dark: {
                primary: '#98aabe',
                primaryAccent: '#7890a8',
                secondary: '#bcc8d4',
                secondaryAccent: '#a0b0c0',
                info: '#ccd6e0',
                infoAccent: '#aabcc8',
                success: '#98c8b0',
                error: '#d89898',
            },
            bgLight: '#F0F0F0',
            bgLightAccent: '#DCDCDC',
            bgDark: '#2C2C2C',
            bgDarkAccent: '#222222',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    ocean: {
        label: 'Ocean',
        colors: {
            light: {
                primary: '#0D7377',
                primaryAccent: '#065A5E',
                secondary: '#E07A5F',
                secondaryAccent: '#C4603F',
                info: '#32AEB1',
                infoAccent: '#1E8D90',
                success: '#2E8B57',
                error: '#D64545',
            },
            dark: {
                primary: '#20909a',
                primaryAccent: '#108080',
                secondary: '#e89878',
                secondaryAccent: '#d07858',
                info: '#50c8cc',
                infoAccent: '#38aab0',
                success: '#40a870',
                error: '#e06060',
            },
            bgLight: '#E8F4F4',
            bgLightAccent: '#C9E0E0',
            bgDark: '#0E1F20',
            bgDarkAccent: '#081516',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    sunset: {
        label: 'Sunset',
        colors: {
            light: {
                primary: '#E07328',
                primaryAccent: '#C45A14',
                secondary: '#8E4585',
                secondaryAccent: '#6E2F68',
                info: '#E8A838',
                infoAccent: '#CA8A04',
                success: '#4CAF50',
                error: '#E53935',
            },
            dark: {
                primary: '#f09048',
                primaryAccent: '#d87028',
                secondary: '#a868a0',
                secondaryAccent: '#8a4888',
                info: '#f0c050',
                infoAccent: '#dda420',
                success: '#60c068',
                error: '#f05050',
            },
            bgLight: '#F5EDE3',
            bgLightAccent: '#E0D0BE',
            bgDark: '#231A12',
            bgDarkAccent: '#18100A',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    fire: {
        label: 'Fire',
        colors: {
            light: {
                primary: '#CC0000',
                primaryAccent: '#8B0000',
                secondary: '#2B2B2B',
                secondaryAccent: '#1A1A1A',
                info: '#D4A017',
                infoAccent: '#B8860B',
                success: '#2E8B57',
                error: '#DC2626',
            },
            dark: {
                primary: '#e82020',
                primaryAccent: '#b80000',
                secondary: '#606060',
                secondaryAccent: '#484848',
                info: '#e8c030',
                infoAccent: '#d0a818',
                success: '#40a870',
                error: '#f04040',
            },
            bgLight: '#F2F2F2',
            bgLightAccent: '#D9D9D9',
            bgDark: '#1A1A1A',
            bgDarkAccent: '#111111',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    lavender: {
        label: 'Lavender',
        colors: {
            light: {
                primary: '#7E57C2',
                primaryAccent: '#5E35A1',
                secondary: '#5C9DCA',
                secondaryAccent: '#3F7FAD',
                info: '#B39DDB',
                infoAccent: '#9575CD',
                success: '#66BB6A',
                error: '#EF5350',
            },
            dark: {
                primary: '#9878d8',
                primaryAccent: '#7858c0',
                secondary: '#78b0d8',
                secondaryAccent: '#5898c0',
                info: '#ccb8e8',
                infoAccent: '#b098d8',
                success: '#80d084',
                error: '#f07070',
            },
            bgLight: '#F3EFF8',
            bgLightAccent: '#DDD4EB',
            bgDark: '#1C162A',
            bgDarkAccent: '#120E1E',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    // -- Accessibility: Color blindness optimized themes --
    cb_protanopia: {
        label: 'Protanopie (Rotschwäche)',
        colors: {
            light: {
                primary: '#0072B2',
                primaryAccent: '#005080',
                secondary: '#E69F00',
                secondaryAccent: '#CC8400',
                info: '#56B4E9',
                infoAccent: '#3A9AD9',
                success: '#009E73',
                error: '#D55E00',
            },
            dark: {
                primary: '#2090cc',
                primaryAccent: '#1070a8',
                secondary: '#f0b820',
                secondaryAccent: '#e0a000',
                info: '#78c8f0',
                infoAccent: '#58b0e0',
                success: '#20b890',
                error: '#f07820',
            },
            bgLight: '#F0F4F8',
            bgLightAccent: '#D6DEE6',
            bgDark: '#1A2332',
            bgDarkAccent: '#111826',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    cb_deuteranopia: {
        label: 'Deuteranopie (Grünschwäche)',
        colors: {
            light: {
                primary: '#0077BB',
                primaryAccent: '#005588',
                secondary: '#EE7733',
                secondaryAccent: '#CC5511',
                info: '#33BBEE',
                infoAccent: '#1199CC',
                success: '#0077BB',
                error: '#CC3311',
            },
            dark: {
                primary: '#2098d0',
                primaryAccent: '#1078a8',
                secondary: '#f09050',
                secondaryAccent: '#e07030',
                info: '#58d0f0',
                infoAccent: '#38b8e0',
                success: '#2098d0',
                error: '#e05030',
            },
            bgLight: '#F2F5F7',
            bgLightAccent: '#D8DFE5',
            bgDark: '#1B2430',
            bgDarkAccent: '#121920',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    cb_tritanopia: {
        label: 'Tritanopie (Blauschwäche)',
        colors: {
            light: {
                primary: '#CC2936',
                primaryAccent: '#9E1F2A',
                secondary: '#008080',
                secondaryAccent: '#006060',
                info: '#E8A838',
                infoAccent: '#CA8A04',
                success: '#2B9348',
                error: '#CC2936',
            },
            dark: {
                primary: '#e04858',
                primaryAccent: '#c03040',
                secondary: '#20a0a0',
                secondaryAccent: '#108888',
                info: '#f0c050',
                infoAccent: '#dda420',
                success: '#40b060',
                error: '#e04858',
            },
            bgLight: '#F5F0EE',
            bgLightAccent: '#DDD5D0',
            bgDark: '#221A18',
            bgDarkAccent: '#181010',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    cb_monochrome: {
        label: 'Achromatopsie (Monochrom)',
        colors: {
            light: {
                primary: '#505050',
                primaryAccent: '#333333',
                secondary: '#888888',
                secondaryAccent: '#6A6A6A',
                info: '#707070',
                infoAccent: '#585858',
                success: '#404040',
                error: '#1A1A1A',
            },
            dark: {
                primary: '#a0a0a0',
                primaryAccent: '#808080',
                secondary: '#b8b8b8',
                secondaryAccent: '#a0a0a0',
                info: '#989898',
                infoAccent: '#808080',
                success: '#909090',
                error: '#c0c0c0',
            },
            bgLight: '#F5F5F5',
            bgLightAccent: '#D9D9D9',
            bgDark: '#222222',
            bgDarkAccent: '#161616',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
}

export type ThemeKey = keyof typeof THEMES

export const DarkMode = { SYSTEM: 'system', DARK: 'dark', LIGHT: 'light' } as const
export type DarkModeValue = (typeof DarkMode)[keyof typeof DarkMode]
