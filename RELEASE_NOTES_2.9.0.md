## ✨ Lucent 2.9.0

### 🆕 新功能
- **关于页面 (About)** — 设置中新增关于页：版本号、构建信息、开发者、开源许可
- **高级页面 (Advanced)** — 位于「关于」上方，集中存放 Shizuku 配对等高级功能
- **Shizuku 配对** — 可在高级页查看 Shizuku 状态并一键发起配对授权
- **自动更新开关** — 关于页可开启「启动时自动获取并安装最新版本」
- **多语言 README** — 新增简中 / 日本語 / 한국어 项目说明

### 🌍 多语言
- 关于页、高级页、Shizuku 相关文案全部适配 **简体中文 / English / 日本語 / 한국어** 四国语言

### 🔧 改进
- Token 缓存优化：WeakHashMap 缓存提升 token 计算性能
- PrivilegedShell 抽象层：统一 Android 特权 shell 接口
- Shizuku 反射集成：无需编译期依赖，降低 APK 体积
- 构建号自动化：versionCode 随每次构建严格递增，保证覆盖安装兼容

### 🐛 修复
- **修复点击「关于」闪退** — 该页嵌套了滚动容器，与设置外壳的滚动冲突导致
  `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`
- 修复旧版本覆盖安装失败问题
- 修复设置页面开关闪烁问题
