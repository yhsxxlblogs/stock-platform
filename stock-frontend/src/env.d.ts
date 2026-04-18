/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 声明 Element Plus 组件类型
declare module 'element-plus' {
  export * from 'element-plus'
}

// 声明图标组件
declare module '@element-plus/icons-vue' {
  import type { DefineComponent } from 'vue'
  export const User: DefineComponent<{}, {}, any>
  export const Lock: DefineComponent<{}, {}, any>
  export const TrendCharts: DefineComponent<{}, {}, any>
  export const Search: DefineComponent<{}, {}, any>
  export const ArrowDown: DefineComponent<{}, {}, any>
  export const Star: DefineComponent<{}, {}, any>
  export const Message: DefineComponent<{}, {}, any>
  export const Phone: DefineComponent<{}, {}, any>
}
