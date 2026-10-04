# unionclef 修复报告（对照 2026-10-03 行为逻辑审计）

**修复基准**：`main` @ `6a490c9c`（2026-10-04 04:21 +0300，比审计快照新一天）
**修复提交**：`6d4624d1 → 22fac909` 共 7 个 commit（本文件之上方）
**验证状态**：`:1.21.1:build` + `:tungsten:build` 全量通过（含 remapJar/shadowJar，产物 `unionclef-1.21.1-0.95.56-all.jar`）；tungsten 纯逻辑单元测试 **35 个全部通过**；`:1.21:compileJava` 通过
**未做**：未在真实 Minecraft 客户端里运行（与审计作者相同，运行时行为以仓库 TODOS.md 的实测记录和本报告的风险边界为准）

---

## 〇、对审计报告本身的辩证核验结论

按"实际为准"原则，10 个角度逐条对照实际 HEAD 复核后：**6 条确认、3 条需修正/限定、1 条被推翻**。

| 审计论断 | HEAD 实况 | 修复取向 |
|---|---|---|
| 角度6 versions/ 缺失 → 构建阻断 | **已被上游修复**（空目录 + `.settings` 已提交，`buildFileName` 回指根 build.gradle） | 不需修；但补了 CI 防回归 |
| 角度10 Py4j `new GatewayServer(..., null)` 绑定所有网卡 | **被推翻**。null 参数是 customCommands 列表；py4j 0.10.9.7 官方 javadoc/FAQ 明文默认绑 `127.0.0.1`。真实 0.0.0.0 暴露面只有 McpServer 的 HTTP | 只修 McpServer；在 GatewayServer 构造点留下纠正注释 |
| 角度8 `ignoreFallDamage=true` 短路摔落守卫 → A* 可能规划摔死路线 | **不成立于 HEAD**。现行 `searchIgnoresFallDamage()` 耦合式（`relaxed \|\| (ignore && !avoid)`）在默认参数下返回 false，守卫**生效**；`TungstenConfig:1329` 的注释描述的是 2026-08-23 之前的旧世界——**是注释在撒谎**，审计被注释误导。`:1350-1358` 记录该守卫带 A/B 门禁上线（nav 14/14、craft 22/22、伤害均值 23.9→2.45） | 修注释 + 抽真值表测试固化语义 |
| 角度7 "三层容差 0.45/1.5/1.5 互不相同导致抖动" | 部分成立：`PathExecutor:561` 的 `<=2.25`（1.5 格）**只是遥测计数器，不是控制流门**；真实到达门是 tungsten GotoCommand 重试门 2.0²、FastNavigator 2.0+settledBody、emit gate 0.447 | 单一来源常量类（值不变）+关系不变量测试 |
| 角度3 "35 个失效调用点" | 快照后已增长：**77 个**（cancel=40/clearGoal=20/pause=5/cancelEverything=2/stopExploring=10）；且上游已加了第六个**有真实行为**的 `Nav.cancelAll()`（接了 6 处） | 按方法分级接线（见 F9） |
| 角度2 优先级分布表 | 大体成立，补充：MobDefenseChain 还有 **80 档**（"打不过就跑"）与 `cachedLastPriority` 粘性重放；**SupervisorTaskChain 从未被实例化（死代码）**；UserTaskChain 优先级运行时可改（BotBehaviour setter） | 优先级梯子写入 TaskRunner 注释 + @priority 命令 |
| 其余（角度1/2/4/5/9 的主体） | 确认 | 见下文逐项 |

另有两个环境事实：jitpack 对 `com.github.replaymod:preprocessor:1678b67` 返回缓存 404（新克隆在依赖 jitpack 的环境会撞上——CI 已加）；`gradle-wrapper.jar` 未入库。本沙盒的解法：源码构建 preprocessor 同 commit 发布 mavenLocal + 本地 Gradle 9.4.1（工具性质，未改仓库）。

---

## 一、逐项修复（对照审计的 10 个角度）

### F5 角度7：容差单一来源 — `PathTolerances`
**修法**：新建 `tungsten/.../path/PathTolerances.java` 收编全部"多近算到"常量；**数值全部不变**（行为保持），只消除散落字面量。
- `EMIT_GATE_SQ=0.2`（≈0.447 格，PathFinder:1833）、`EMIT_GATE_FLUID_SQ=0.9`（水面/梯子 ：1811/:1813）
- `GOTO_ARRIVAL_SQ=4.0`（tungsten GotoCommand 重试/停止门）、`NAVIGATOR_ARRIVAL=2.0`（FastNavigator，与 goto 门锁定相等）
- `SEGMENT_END_TELEMETRY_SQ=2.25`（PathExecutor — **注释明示 TELEMETRY ONLY，禁止接行为**）
- `STUCK_AXIS=1.5`（UnstuckChain 逐轴判定）
**测试**：`PathTolerancesTest`（7 个用例）固化"emit < 遥测 < 到达球"等关系链。

### F3 角度8：摔落开关统一 — `FallDamagePolicy`
**修法**：决策函数抽成纯类，`TungstenModDataContainer.searchIgnoresFallDamage()` 委托之；重写两处撒谎注释（TungstenConfig:1329-1337、TungstenModDataContainer:73-104），把"守卫被短路"的旧世界描述改为现状+历史。
**测试**：`FallDamagePolicyTest`（5 个用例）——第一案就是**默认参数下守卫生效**，钉死审计误报；全真值表与历史公式一致。
**行为变更**：无。`;settings ignoreFallDamage` 命令语义不变。

### F4 角度1：漂移阈值自适应 — `DriftPolicy`
**修法**：`Agent.compare` 的放行量 `threshold + perTick*tick` 中的**增长项**乘以帧率因子 `clamp(frameMs/50ms, 1, 4)`（与 WindMouseRotation 同源同界；帧时钟由 MixinInGameHud 喂给 `DriftPolicy.noteFrame`）。tick-0 阈值（tick-1 时 drift 1.723 的历史事故防线）**不动**。配置开关 `driftFrameTimeAdaptive`（默认 true），关掉即回到精确旧界。
**测试**：`DriftPolicyTest`（6 个用例）——含"因子 1.0 精确复现旧公式"与"tick-0 保证不变"。

### F6 角度5：优先级治理
- **MLG 抢夺收窄**：`MlgPolicy.shouldTrigger` — MLG 只在坠落**会致伤**时抢身体（`fallDistance + 射线落地距离 >= 3.0`，故意取 vanilla 首伤 4 格前一格的保守边界）。`MobDefenseChain:847` 的让位从裸 `isFalling` 改为 `willCatchFall`——"为了救一次无害击退跳跃交出战斗控制权"的行为被修掉。设置项 `mlgBucketOnlyWhenHarmful`（默认 true，关掉即旧行为）。
- **@priority 命令**：暴露既有的 `BotBehaviour.setUserTaskChainPriority`（0-99 钳制），外部 agent 可抬升自身免被普通链抢占；100 档（岩浆/火/缺氧/致伤 MLG）保持不可越。
- **优先级梯子文档**：完整档位+平局裁决序（注册序）+ SupervisorTaskChain 休眠事实，写入 TaskRunner.tick 注释。
**测试**：`MlgPolicyTest`（5 个用例）。

### F8 角度9：战斗信号
- **可见性 bug**：`SafetySystem.stage/isBraking/isRepositioning` 加 `volatile`（渲染线程写、客户端线程读，原来非 volatile 可无限期读到旧值）。
- **reset 残留**：`SafetySystem.reset()` 补清 `repositioning` + `wasBrakingLastFrame/wasRepositioningLastFrame`——上一场战斗的 repositioning=true 曾能穿越 reset 存活（active=false 后逐帧清零路径永不执行）。
- **盾牌单点**：`ShieldAuthority`（纯逻辑，5 个用例）统一 MobDefenseChain（InputControls 通道）与 CombatController→ShieldBlocker（直写 useKey 通道）：任一源要求举盾则盾保持；引擎通道在链通道持盾时**退让**（engineShouldPress），杜绝同 tick 双写按压/释放。
**保留未动（如实声明）**：追击/逃跑的高层意图（runAwayTask / SafetySystem.stage / isBraking 三层）目前做到共享可见+统一盾决策；把三层收敛为单写者是更大的手术，涉及行为调参，无 A/B 课程背书不动。

### F7 角度2：输入仲裁器 — `InputArbiter`
**修法**：按 tick 记录按键归属的中央注册器（5 个命名域），共享 tick 基准锚在 altoclef `onTickPre`（MinecraftClient.tick HEAD，天然先于同 tick 内所有 tungsten 驱动）。已接线：altoclef 全部按压（InputControls.tryPress/hold）、tungsten MovementQueue.applyInputs、ApproachLatch、CombatMoveIntent.writeKeys、外部通道（AgentActionButtons、py4j mouseClick pickItem）。
**强制模式默认关**：`TungstenConfig.inputArbiterEnforce=false`（影子模式）——先记录冲突（每 50 次打一条摘要日志），审过冲突谱再开强制。这正是 G-0 的教训：错误但强制的规则比正确但被记录的规则危险。
**释放不仲裁**（避免卡键风险）；tungsten 其余 ~200 处 setPressed 属域内相位纪律，登记归属不逐点改写——边界在 FIXES 与 InputArbiter javadoc 里如实写明。
**测试**：`InputArbiterTest`（7 个用例）。

### F9 角度3：Nav 五个空函数彻底接线（用户拍板项）
每个方法都有真行为，每个都有独立杀开关（G-0 的 22/22→15/22 历史要求可单独回退）：

| 方法 | 新语义 | 开关（默认） |
|---|---|---|
| `cancel()` | 放弃本次物理尝试：`TungstenHelper.stop()`（搜索/执行器 stop 标志+30s 锁清空；执行器在**自己的 tick 边界**消费 stop，非线程击杀；导航器/队列/步行者不动） | `navRealCancel` (true) |
| `clearGoal()` | = cancel()（stop 同时清 hasGoal 的 active 与锁） | 同上 |
| `stopExploring()` | = cancel()（放弃当前 wander 尝试） | 同上 |
| `cancelEverything()` | 委托已真实的 `cancelAll()`（全量 teardown） | `navStopOnTaskEnd`（上游既有） |
| `pause()` | **时间窗**而非按键清除：`navPauseTicks`（默认 3）tick 内 mixin 挂起四个导航驱动（navigator/queue/walker/executor）并逐 tick 释放移动键；战斗原语（弓/盾/闪避）照常 | `navRealPause` + `navPauseTicks` (true/3) |

**关键配套**：driveTungstenPrimary 里两处 **kick 后立即 cancel** 的调用被移除并留下完整注释——那就是审计引用的 G-0 同 tick 相杀（pdEnter=1921, mqStarted=0）；kick 本身即所有权声明。三处"撒谎注释"（EnterNetherPortalTask:75、MLGBucketTask:467、InteractWithBlockTask:275）在新语义下自动成真，保留。Nav.java 头注释重写为"它现在改变行为"，G-0 历史完整保留为文档。
**风险如实声明**：38 处 cancel 调用点从 no-op 变为真实停止，进度检查器误报时会把当前腿停掉重来（重新寻路，非冻结）；课程通过率（uctest 的 craft 22/22、nav 14/14）需要一次真机回归才能确认——这是本报告与 CI 都无法替代的一步。

### F2 角度4：名实相符
`CustomBaritoneGoalTask` → `CustomTungstenGoalTask`（34 个 Java 文件，`git mv` 保留历史；改名前全仓核实**无字符串注册依赖**，仅 2 处调试标签一并更新）；类内 "Baritone failed — try Tungsten" 等误导文案修正；类 javadoc 记录改名原因与"历史文档保留旧名"的决定。`BaritoneHelper` 仅加 javadoc 说明（避免不必要 churn）。

### F1 角度6：构建与文档
- `scripts/README.md:45`：`#goto` 示例更正为 `;`/`@` 前缀 + `#` 前缀已随 G-0 退役的说明；:100 "tungsten/baritone commands" 更正
- `scripts/example_goto.py:11`：py4j API 误用（dict 当 GatewayParameters）修复
- `gradle.properties`：`minecraft_version/yarn_mappings/fabric_api_version` 三个**死属性**标注为 legacy（build.gradle 的 per-version 映射表才是真值）
- `AGENTS.md`：`compile all three modules`、`yarn 1.21+build.9 统一`等过时表述按现状改写；yarn build.3/build.4 的双映射 build 号记为已知瑕疵
- 新增 **CI**（`.github/workflows/build.yml`）：tungsten 编译+测试、altoclef 三版本编译——versions/ 空目录与 jitpack 404 这类问题以后会在引入当天暴露

### F10 角度10：并发与安全面
- **MCP**：`start(port, bindAddress)` + `Settings.mcpBindAddress`，**默认 127.0.0.1**；显式设 0.0.0.0 时启动日志打 WARNING（bearer-only、无 TLS）；HTTP 线程池从无界 cachedThreadPool 收敛为固定 4
- **Py4j**：构造点注释固化"默认回环"事实（纠正审计）；`setPerspective`、`countLogsNear`（活世界扫描）补 `onClientThread` 编组（与既有 55 处同机制：MinecraftClient.execute + 5s 超时回退）
- **A\* 并发**：`enableParallelStreaming` 默认 true→false（配置注释自己承认 CME 风险——并行流只会放大并发读者数）。**如实声明残留**：搜索线程/池无快照读活 ClientWorld 的结构问题需要"块快照或线程封闭"级别的重写，本次以"降低并发压力 + 补齐已知的 null 容忍点 + 文档化"为界，不在本次伪修
- 角度10 的"零测试零 CI"由测试基建（35 用例）与 build.yml 关闭

---

## 二、测试与验证

| 验证 | 结果 |
|---|---|
| `:tungsten:test`（JUnit5：PathTolerances 7 + FallDamagePolicy 5 + DriftPolicy 6 + MlgPolicy 5 + ShieldAuthority 5 + InputArbiter 7） | **35/35 通过** |
| `:1.21.1:build`、`:tungsten:build`（compile+processResources+remapJar+shadowJar+sourcesJar） | **通过**，产物 `unionclef-1.21.1-0.95.56-all.jar`（4.2MB）、`tungsten-BETA-1.0.0.jar` |
| `:1.21:compileJava` | 通过 |
| 基线（改动前 HEAD） | `:1.21.1` 705 类、`:tungsten` 198 类编译通过（对照证明本次修复未破坏既有编译面） |
| 真机课程回归（craft/nav/pvp） | **未做**（无 Minecraft 运行时）；所有行为开关可独立回退 |

## 三、风险边界与回退

1. **Nav 接线是最大的行为变更**。若课程回归出现倒退，按开关粒度回退：`navRealCancel=false`、`navRealPause=false`（其余修复与 Nav 开关无关）。
2. **MLG 收窄**改变战斗-MLG 交接频率（方向：更少抢夺）。若想回到"任何下落都 MLG"：`mlgBucketOnlyWhenHarmful=false`。
3. **漂移自适应**只在低帧率机器上放宽增长项；`driftFrameTimeAdaptive=false` 即旧行为。
4. **仲裁器**默认影子模式，不改变任何按键行为；开强制前先看 `[InputArbiter]` 冲突日志。
5. **MCP 默认只绑回环**——依赖 LAN 连接 agent 的部署需显式 `mcpBindAddress=0.0.0.0`（启动日志会 WARNING）。
6. 未修复项（如实）：A* 线程对活世界的无快照读（结构性重写）、三层战斗意图的单写者化、`versions/mainProject` 之外的 1.21.11 源码生成依赖 preprocessor 本地可用（jitpack 404 影响新克隆）。

## 四、审计报告修正清单（本次辩证核验新增）

1. 推翻：Py4j 绑定所有网卡（官方文档+javap 双证：默认 127.0.0.1，null 是 customCommands）
2. 推翻：默认参数下摔落守卫被短路（现行耦合式在默认下返回 false；审计被 TungstenConfig 陈旧注释误导；该守卫 2026-08-23 带门禁上线）
3. 修正：PathExecutor 2.25 是遥测不是门（真实门在 GotoCommand/FastNavigator）
4. 修正：Nav 失效调用点 35→77；上游已存在真实的 cancelAll()
5. 修正：优先级表补充 80 档/粘性重放；SupervisorTaskChain 是从未实例化的死代码
6. 修正：versions/ 构建阻断已被上游解决；新的环境风险是 jitpack preprocessor 404 与 wrapper jar 缺失（CI 已覆盖前者）
