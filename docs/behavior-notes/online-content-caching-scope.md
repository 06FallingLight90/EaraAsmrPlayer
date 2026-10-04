# OnlineContentRepository 缓存作用域与并发语义

- **行为描述**：ASMR.ONE 解析/详情/曲目树缓存（`asmrOneResolvedCache` / `asmrOneResolvedDetailsCache` / `asmrOneTracksCache`）随 C4b-3 下沉到 `OnlineContentRepository`（`@Singleton`）后，从"每个 AlbumDetailViewModel 实例一份"变为**进程级全局共享**。任何一个 VM 实例切端点调用 `invalidateAsmrOneCaches()` 会清掉其他存活实例的缓存；`forgetAsmrOneResolution` 同理影响全局。仅影响缓存命中率与再解析次数，不影响正确性（解析幂等）。
- **代码位置**：`data/repository/OnlineContentRepository.kt`（缓存字段与 `invalidateAsmrOneCaches` / `forgetAsmrOneResolution` / `cancelAsmrOneResolutionInFlight`）。
- **触发条件**：多个 AlbumDetail 实例并存（如返回栈中多个详情页）+ 其中之一切换 ASMR.ONE 端点或手动刷新。
- **并发保护**（2026-10-04 审查 P1-1 修复）：原 VM 实现所有缓存访问在 Main.immediate 单线程串行；repo 化后在途解析跑在 `SupervisorJob + Dispatchers.IO`，多协程可并发触达缓存。现容器为 `ConcurrentHashMap`，复合读写（TTL 判定+写入+淘汰、在途去重 get-or-put）由 `cacheMutex` 串行化；`peekAsmrOneResolvedDetails` / `invalidate*` / `forget*` 的单键操作依赖 CHM 的原子性（弱一致快照，语义可接受）。
- **钉测试引用**：`OnlineContentRepositoryTest`（MockWebServer 行为级，未钉并发路径）。
- **发现于**：R2 阶段 C 门禁审查 P2-3（缓存共享漂移未录档）+ P1-1（并发收敛被改）。
