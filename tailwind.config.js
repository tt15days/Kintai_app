/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        './src/main/resources/templates/**/*.html',
    ],
    theme: {
        extend: {
            colors: {
                primary: {
                    DEFAULT: '#2563eb',
                    hover:   '#1d4ed8',
                    light:   'rgba(37, 99, 235, 0.1)',
                },
                accent: {
                    DEFAULT: '#7c3aed',
                    light:   'rgba(124, 58, 237, 0.1)',
                },
                success: {
                    DEFAULT: '#10b981',
                    light:   'rgba(16, 185, 129, 0.2)',
                },
                danger: {
                    DEFAULT: '#ef4444',
                    light:   'rgba(239, 68, 68, 0.2)',
                },
                warning: {
                    DEFAULT: '#f59e0b',
                    light:   'rgba(245, 158, 11, 0.2)',
                },
                glass: {
                    bg:     'rgba(255, 255, 255, 0.7)',
                    border: 'rgba(0, 0, 0, 0.08)',
                    hover:  'rgba(0, 0, 0, 0.03)',
                    'nav-hover': 'rgba(0, 0, 0, 0.05)',
                    input:  'rgba(255, 255, 255, 0.9)',
                },
                txt: {
                    primary:   '#020617',
                    secondary: '#334155',
                },
            },
            fontFamily: {
                sans: ['Inter', 'sans-serif'],
                mono: ['"JetBrains Mono"', 'monospace'],
            },
            boxShadow: {
                glass:  '0 8px 32px 0 rgba(31, 38, 135, 0.05)',
                'glass-heavy': '0 10px 40px 0 rgba(31, 38, 135, 0.08)',
                primary: '0 4px 15px rgba(59, 130, 246, 0.4)',
                'primary-hover': '0 6px 20px rgba(59, 130, 246, 0.6)',
            },
            backdropBlur: {
                glass: '12px',
                'glass-heavy': '16px',
            },
            borderRadius: {
                panel: '16px',
                'panel-heavy': '24px',
            },
            keyframes: {
                fadeIn: {
                    'from': { opacity: '0', transform: 'translateY(10px)' },
                    'to':   { opacity: '1', transform: 'translateY(0)' },
                },
                pulse: {
                    '0%, 100%': { opacity: '1' },
                    '50%':      { opacity: '0.5' },
                },
            },
            animation: {
                'fade-in': 'fadeIn 0.5s ease forwards',
                'pulse-slow': 'pulse 2s infinite',
            },
        },
    },
    plugins: [],
};
