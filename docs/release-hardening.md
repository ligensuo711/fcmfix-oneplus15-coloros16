# 发布安全加固记录

本轮从 `0b8fa2b` / `53-coloros-8` 开始。包名、签名体系、推荐作用域不变；
本轮不自动创建新版稳定 Release，不向 LSPosed 分发仓库推送未经实机回归的 APK。

## 修改边界

- 广播入口读取 Binder 真实 UID，核对 GMS、精确 RECEIVE action、明确且一致的目标包、
  允许列表。只在一个入口安装信任作用域，避免 AMS → BroadcastController 重复建立窗口。
- 不向 Intent extras 写入信任标记。线程内作用域保存目标和发送 UID；嵌套入口（包括
  空 Intent、不可信请求）遮蔽外层信任，正常返回、提前返回、异常均清理并恢复外层状态。
- 异步队列路径读取框架 BroadcastRecord.callingUid；服务路径按已核实的完整方法签名读取
  callingUid，并交叉核对 ServiceRecord.appInfo / 分类目标包。不以清除身份后的 Binder UID
  或字符串 `bsgcm` 单独作为凭据。未知签名不启用对应服务 Hook，并记录日志。
- Firebase 应用自拉起服务只在自己的有效窗口内放行，不能用自发 RECEIVE 建立窗口。
  ColorOS 的 `com.google.android.gms.gcm.ACTION_TASK_READY` 仅在已核验 GMS 的 `bsgcm`
  bind 分支接受，不全局认作 FCM 广播。该动作是 GCM 任务入口，不是收到消息的单独证据。
- 保留单 UID 20 秒、单调时钟、最大到期时间语义；到期查询与续期串行化，避免删除新窗口。
  不永久放开应用，也不全局关闭 Hans。当前 UID 解析限进程所属用户，不跨用户套用窗口。
- Ice Box 改为单工作线程、最多 8 个排队任务的尽力激活；没有异步重放系统原方法、
  没有给 int 广播入口返回 Boolean。外部 SDK 挂起也不会阻塞原广播线程；本条消息可能丢失。
- Hook 适配层每次注册只执行自己的回调。回调故障记录日志并保留框架原调用/异常；清理在
  finally 中执行。回调对对象产生的既有副作用不具备通用事务回滚能力。
- 配置用一次 getAll 读取构造不可变快照，后台串行重载、一次发布；损坏配置保留上一份快照。
  Android 14+ 配置刷新核对发送包和 UID；旧 Android 不开放无身份校验的刷新入口，需重启。
- 去掉 Analytics Measurement 组件误判；扫描 FCM 接收器及服务，结果仅代表包含组件，
  不保证应用服务端实际启用 FCM。诊断广播仅发往 GMS。
- 保留通知 Hook 按已知完整签名匹配并校验实参；未知 OTA 单点失配回退系统原行为。
  删除无功能的 BootCompletedReceiver 及其开机权限。未大规模重写历史非 ColorOS 分支。

## 签名依据

来自已保存的 PLK110 ColorOS 16 框架分析（不是由参数数量猜测）：

```text
OplusStartupStrategy.isAppClassifyRestricted(String,String,String,int,int,Intent): boolean
OplusAppStartupManager.isAllowStartFromBindService(ProcessRecord,String,int,ServiceRecord,Intent,String): boolean
OplusAppStartupManager.isAllowStartFromStartService(ProcessRecord,int,int,String,ServiceRecord,Intent): boolean
NotificationManagerService.cancelAllNotificationsInt(int,int,String,String,int,int,int,int): void
```

旧通知签名另依据 [AOSP Android 13 源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-13.0.0_r1/services/core/java/com/android/server/notification/NotificationManagerService.java)。
签名一致不等于 OTA 内部语义永不变化，更新系统后仍需回归。

## 自动验证与发布

普通 master push / PR：JUnit、lintDebug、lintRelease、Debug/Release 构建，上传测试报告；
无签名密钥、无发布步骤。正式发布仅 `53-coloros-*` tag 触发，校验 tag 等于 versionName，
测试和 lint 成功后才签名、校验签名并生成 SHA256SUMS。所有 Actions 固定到已核对的提交。
源码仓库 tag 与 LSPosed 分发仓库的 `versionCode-versionName` tag 是两套约定，不能混用。

本地建议 JDK 21、Android SDK 36：

```text
gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Windows 中文路径下曾出现“测试类已编译却 ClassNotFoundException”，同一源码在纯英文路径
构建可正常运行。不要删除失败测试或关闭 lint 来规避。验证时可建立不含 .git、构建缓存和
签名文件的英文路径副本，再确认源码摘要一致；GitHub Linux CI 是另一个独立验证层。

测试覆盖：精确动作、发送者/目标组合、目标冲突、自发伪造、GCM bind 边界、嵌套与线程隔离、
真实 Hook 调度器异常/提前返回清理、窗口边界/续期/并发/UID 隔离、配置默认值/不可变性/损坏拒绝、
ColorOS 与通知方法签名失配。

2026-09-11 本地验证：25 项 JUnit 测试通过；lintDebug / lintRelease 无错误（Debug 有 13 条
警告，主要为旧依赖、目标 SDK 与现有 UI 资源）；Debug / Release 构建成功；两份工作流通过
actionlint。没有新增 lint baseline，也没有关闭 lint。验证副本与提交源码的相关文件哈希一致。
GitHub CI 结果以本提交对应的 Actions 运行记录为准。

## 发布前仍需真机回归

2026-09-11 检查时无 ADB 设备连接，以下项目未验证，不能用历史 Nekogram 结果替代：

1. PLK110：普通后台、锁屏冻结、force-stop 后，真实 GMS 消息可拉起并生成通知。
2. 微信：记录发送、GMS Received/Successful broadcast、进程启动及通知时间，区分各段延迟。
3. 非允许列表目标不获得额外放行；普通无特权测试 App 伪造 RECEIVE 不能建立窗口或解冻。
   不以 root / adb shell 发送代替普通 UID 的攻击面测试。
4. 核验 GCM bsgcm bind 和 Firebase 自拉起服务未被误拦截。
5. 连续消息延长同 UID 窗口；超过末次有效事件 20 秒后回归系统策略；其他 UID 不受影响。
6. Ice Box 未安装、权限拒绝、已冻结、已启用；无 system_server 崩溃、ANR 或原方法重放。
7. 正常配置刷新成功，外部 App 发送相同动作被拒绝；OTA 签名失配日志可定位。
8. 系统界面/系统进程稳定性和锁屏耗电对照。自动测试通过不等于已验证耗电改善。

正式稳定包及 LSPosed 分发更新以完成上述回归为前提。

2026-09-14 的日志广播用户身份、后台有界转发及重复诊断限频调整见 [日志说明](logging.md)。
该调整不改变上述推送信任边界或 20 秒窗口。
