import { useContext } from 'react'
import { ThemeContext } from './themeStore'

export const useTheme = () => useContext(ThemeContext)
