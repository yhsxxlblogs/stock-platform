import type { DefineComponent } from 'vue'

// 声明 Element Plus 组件
// @ts-ignore
declare module 'vue' {
  export interface GlobalComponents {
    // Element Plus 基础组件
    ElContainer: DefineComponent<{}, {}, any>
    ElHeader: DefineComponent<{}, {}, any>
    ElMain: DefineComponent<{}, {}, any>
    ElFooter: DefineComponent<{}, {}, any>
    ElRow: DefineComponent<{}, {}, any>
    ElCol: DefineComponent<{}, {}, any>
    ElCard: DefineComponent<{}, {}, any>
    ElButton: DefineComponent<{}, {}, any>
    ElInput: DefineComponent<{}, {}, any>
    ElForm: DefineComponent<{}, {}, any>
    ElFormItem: DefineComponent<{}, {}, any>
    ElTable: DefineComponent<{}, {}, any>
    ElTableColumn: DefineComponent<{}, {}, any>
    ElMenu: DefineComponent<{}, {}, any>
    ElMenuItem: DefineComponent<{}, {}, any>
    ElDropdown: DefineComponent<{}, {}, any>
    ElDropdownMenu: DefineComponent<{}, {}, any>
    ElDropdownItem: DefineComponent<{}, {}, any>
    ElAvatar: DefineComponent<{}, {}, any>
    ElIcon: DefineComponent<{}, {}, any>
    ElLink: DefineComponent<{}, {}, any>
    ElTag: DefineComponent<{}, {}, any>
    ElEmpty: DefineComponent<{}, {}, any>
    ElDivider: DefineComponent<{}, {}, any>
    ElAutocomplete: DefineComponent<{}, {}, any>
    ElRadioGroup: DefineComponent<{}, {}, any>
    ElRadioButton: DefineComponent<{}, {}, any>
    ElPagination: DefineComponent<{}, {}, any>
    ElMessage: DefineComponent<{}, {}, any>
    ElMessageBox: DefineComponent<{}, {}, any>
  }
}

export {}
