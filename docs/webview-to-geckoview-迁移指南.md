# Capacitor 插件迁移为 Capacitor-GeckoView 插件

## 目的

本文档总结把已有的 Capacitor 官方插件改造成 **Capacitor-GeckoView** 版本的通用经验，供开发者在自己的仓库中执行迁移。

---

## 背景

Capacitor 官方 Android 侧基于 WebView（`@capacitor/android`）。而 Capacitor-GeckoView 是基于 **GeckoView** 引擎的魔改版本（`@capacitor-geckoview/android`）。

官方插件原本**不依赖** GeckoView（基于 WebView）。迁移到 GeckoView 的核心是：**把 Android 依赖从官方包切换到 GeckoView 魔改包，并让需要 GeckoView 的插件显式依赖它、正确获取 GeckoRuntime**。

> 注意：Capacitor-GeckoView 只影响 **Android**。iOS 侧仍使用官方 `@capacitor/ios`，通常无需改动。

---

## 一、通用模板改动（每个插件都要改）

以下 3 处改动在所有插件中完全一致，属于"照抄"部分。

### 1. `package.json`

| 字段              | 原值                             | 改为                                       |
| ----------------- | -------------------------------- | ------------------------------------------ |
| `devDependencies` | `"@capacitor/android": "^x.y.z"` | `"@capacitor-geckoview/android": "^x.y.z"` |

要点：

- `devDependencies` 中 **移除** `@capacitor/android`，替换为 `@capacitor-geckoview/android`（版本号保持一致）。
- `@capacitor/core`、`@capacitor/ios`、`@capacitor/docgen` 等保持不变。
- `peerDependencies` 保持 `"@capacitor/core": ">=8.0.0"` 不变。

### 2. `android/settings.gradle`

```diff
 include ':capacitor-android'
-project(':capacitor-android').projectDir = new File('../node_modules/@capacitor/android/capacitor')
+project(':capacitor-android').projectDir = new File('../node_modules/@capacitor-geckoview/android/capacitor')
```

即把本地 `capacitor-android` 工程指向 GeckoView 魔改包。

### 3. `android/build.gradle` —— 依赖仓库（可选）

如果只需要 `google()` + `mavenCentral()` 这两个仓库，插件里**不需要添加** `repositories {}` 块（根级已定义）。

仅当需要引用自定义特殊仓库（例如本地打包的 geckoview 依赖）时才需要在插件里显式添加 `repositories {}`，并同步在宿主 `settings.gradle` 中强制设置仓库解析模式，否则无法解析到本地 geckoview 依赖：

```groovy
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
}
```

> `RepositoriesMode` 有三种：`PREFER_SETTINGS`（优先 settings 仓库）、`PREFER_PROJECT`（优先项目仓库）、`FAIL_ON_PROJECT_REPOS`（项目声明仓库直接报错）。本地调试引用本地 geckoview 包时用 `PREFER_SETTINGS`。

---

## 二、让插件依赖 GeckoView（仅涉及 GeckoView 的插件）

只有与 WebView / GeckoView 交互的插件才需要。判断方法：**插件源码中是否 import 了 `org.mozilla.geckoview.*` 的类**。

### 添加 geckoview 依赖

在 `android/build.gradle` 的 `dependencies` 中追加：

```groovy
implementation "$rootProject.ext.geckoviewDependency"
```

- 版本号由宿主应用通过 `rootProject.ext.geckoviewDependency` 统一提供（该变量在引入 `@capacitor-geckoview/android` 的宿主根工程中定义），插件内不写死版本。
- 某些插件源码虽不直接 import geckoview，但运行时依赖 geckoview 类（例如通知场景）。若构建/运行时出现 `ClassNotFoundException` 或依赖缺失，也需追加同样的依赖。

---

## 三、GeckoRuntime 获取方式

GeckoView 规定 **每个进程只允许存在一个 GeckoRuntime（进程级单例）**。需要 GeckoView 的插件，统一通过魔改版 Capacitor 的 `Bridge.getGeckoRuntime()` 获取全局实例，不要自行创建 runtime。

```java
import com.getcapacitor.Bridge;
import org.mozilla.geckoview.GeckoRuntime;

GeckoRuntime runtime = Bridge.getGeckoRuntime();
```

要点：

- 补充 `import com.getcapacitor.Bridge;`。
- 直接使用 `Bridge.getGeckoRuntime()` 获取进程级单例。

---

## 四、WebView 的 GeckoView 替代（可选，视插件而定）

如果插件源码中使用了 `android.webkit.*` 的 WebView API 或 Chrome Custom Tabs，需要找到 GeckoView 中对应的 API 来替代。GeckoView 的 API 可通过 [GeckoView Javadoc](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/package-summary.html) 或拉取 GeckoView 源码查询。

常见对应关系：

| WebView 用法                            | GeckoView 替代                                         |
| --------------------------------------- | ------------------------------------------------------ |
| `android.webkit.WebView`                | `org.mozilla.geckoview.GeckoView`                      |
| `WebView.getSettings()`                 | `GeckoSession.Settings`                                |
| `WebViewClient.onPageFinished()` 等回调 | `GeckoSession.NavigationDelegate` / `ProgressDelegate` |

---

## 五、迁移清单（Checklist）

对每个插件按顺序执行：

- [ ] `package.json`：devDependencies 换成 `@capacitor-geckoview/android`
- [ ] `android/settings.gradle`：`@capacitor/android/capacitor` → `@capacitor-geckoview/android/capacitor`
- [ ] `android/build.gradle`：删除顶层 `repositories {}` 块（可选，或宿主配置 `RepositoriesMode.PREFER_SETTINGS`）
- [ ] 若涉及 GeckoView：添加 `implementation "$rootProject.ext.geckoviewDependency"`
- [ ] 源码中通过 `Bridge.getGeckoRuntime()` 获取 GeckoRuntime，并 `import com.getcapacitor.Bridge`
- [ ] 若使用 WebView / Custom Tabs：按第四节替换为 GeckoView 对应 API
- [ ] 重新安装依赖并验证构建

---
