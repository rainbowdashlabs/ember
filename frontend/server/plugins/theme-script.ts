const THEME_SCRIPT = `<script>try{var m=localStorage.getItem('dark_mode')||localStorage.getItem('theme');if(m==='LIGHT'||m==='light'){document.documentElement.classList.add('light')}else if(m==='DARK'||m==='dark'){document.documentElement.classList.add('dark')}else{document.documentElement.classList.add(window.matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light')}}catch(e){}</script>`

export default defineNitroPlugin((nitroApp) => {
    nitroApp.hooks.hook('render:html', (html) => {
        html.head.unshift(THEME_SCRIPT)
    })
})
