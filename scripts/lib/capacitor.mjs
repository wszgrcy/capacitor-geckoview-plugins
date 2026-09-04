// 插件依赖的官方 capacitor 包（完整包名）。
// android 为本地魔改版本，使用 @capacitor-geckoview 前缀；其余为 npm 官方包。
export const PROJECTS = [
  '@capacitor-geckoview/android',
  '@capacitor/cli',
  '@capacitor/core',
  '@capacitor/ios',
];

export const PEERPROJECTS = ['@capacitor/core'];

// 本地依赖映射（包名 -> 相对仓库根目录的本地路径），用于 toggle-local。
export const LOCAL_PATHS = {
  '@capacitor-geckoview/android': '../../capacitor-geckoview/android',
};
