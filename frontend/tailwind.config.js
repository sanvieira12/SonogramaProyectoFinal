/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,jsx}",
  ],
  theme: {
    extend: {
      colors: {
        stone: {
          50:  '#F4F7F8',
          100: '#E6ECEE',
          200: '#CDD9DD',
          300: '#A8BDC4',
          400: '#7E9FA8',
          500: '#5C7D87',
          600: '#46626B',
          700: '#374D54',
          800: '#283740',
          900: '#1A2429',
          950: '#0D1316',
        },
        estado: {
          disponible:    '#5B8C7D',
          reservado:     '#B8975E',
          vendido:       '#6B7280',
          descontinuado: '#A66363',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}

