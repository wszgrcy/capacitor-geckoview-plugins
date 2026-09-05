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

## 五、Gradle 8 → 9 升级兼容修改

> 所有插件在升级时统一把构建链从 Gradle 8 / AGP 8 升级到 Gradle 9 / AGP 9，涉及多处写法变更（最终状态如下，后续未再改动）。

> ⚠️ **采用方案**：本仓库保持 `apply plugin: 'com.android.library'`（普通 Android Library，Groovy DSL），**未迁移到 KMP**。不涉及 `com.android.kotlin.multiplatform.library`、`kotlin { android {} }`、`androidMain.dependencies {}`、`androidHostTest`/`androidDeviceTest` 等 KMP 专属写法。若参考 AGP 8.x→9.x 的 **KMP** 迁移文档，其大部分内容（插件 ID、`kotlin { android {} }` 结构、buildTypes 移除、依赖配置、测试源码集等）**均不适用**；只有 `minSdk`/`targetSdk` 写法变更等 AGP 9 通用变化可参考。

> 📎 **官方参考**：AGP 9.0.0 官方 Release Notes —— <https://developer.android.com/build/releases/agp-9-0-0-release-notes>。本仓库记录的是升级时实际遇到的问题；**不同插件/宿主升级时遇到的问题可能各不相同**，遇到本文未覆盖的具体报错时，可查阅该官方文档确认对应 DSL / API 的变更。

### 版本升级

| 项目           | 原值                                          | 改为                                          |
| -------------- | --------------------------------------------- | --------------------------------------------- |
| Gradle Wrapper | `gradle-8.14.3-all.zip`                       | `gradle-9.3.1-all.zip`                        |
| AGP            | `com.android.tools.build:gradle:8.13.0`       | `com.android.tools.build:gradle:9.3.2`        |
| publish-plugin | `io.github.gradle-nexus:publish-plugin:1.3.0` | `io.github.gradle-nexus:publish-plugin:2.0.0` |

- `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改为 Gradle 9.3.1。
- `buildscript { dependencies { classpath ... } }` 中 AGP 改为 9.3.2，publish-plugin 改为 2.0.0。
- **依据**（提交 `7ce2b24a`）：AGP 9 要求 Gradle 9.x（官方兼容表：Gradle ≥ 9.1.0），因此升级 Gradle Wrapper 与 AGP；publish-plugin 同步升级到与 AGP 9 兼容的版本。

### `defaultConfig` 写法变更（AGP 9 API）

AGP 9 废弃了 `minSdkVersion` / `targetSdkVersion` 属性，必须改为块语法 + `release(...)`：

```groovy
// 旧写法（AGP 8）
minSdkVersion project.hasProperty('minSdkVersion') ? rootProject.ext.minSdkVersion : 24
targetSdkVersion project.hasProperty('targetSdkVersion') ? rootProject.ext.targetSdkVersion : 36

// 新写法（AGP 9，最终状态）
minSdk {
    version = release(project.hasProperty('minSdkVersion') ? rootProject.ext.minSdkVersion : 24)
}
targetSdk {
    version = release(project.hasProperty('targetSdkVersion') ? rootProject.ext.targetSdkVersion : 36)
}
```

### `compileOptions` 调整（视插件而定）

部分插件（如 `app`、`browser`、`text-zoom`）把 `compileOptions` 从 `JavaVersion.VERSION_21` 降为 `VERSION_17`；其余插件（如 `action-sheet`）保持 `VERSION_21` 不变。是否降级取决于插件实际用到的依赖/特性。

---

## 六、迁移清单（Checklist）

对每个插件按顺序执行：

- [ ] `package.json`：devDependencies 换成 `@capacitor-geckoview/android`
- [ ] `android/settings.gradle`：`@capacitor/android/capacitor` → `@capacitor-geckoview/android/capacitor`
- [ ] `android/build.gradle`：删除顶层 `repositories {}` 块（可选，或宿主配置 `RepositoriesMode.PREFER_SETTINGS`）
- [ ] 升级 Gradle 8 → 9：
- [ ] 若涉及 GeckoView：添加 `implementation "$rootProject.ext.geckoviewDependency"`
- [ ] 源码中通过 `Bridge.getGeckoRuntime()` 获取 GeckoRuntime，并 `import com.getcapacitor.Bridge`
- [ ] 若使用 WebView / Custom Tabs：按第四节替换为 GeckoView 对应 API
- [ ] 重新安装依赖并验证构建

---
