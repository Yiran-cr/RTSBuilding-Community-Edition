package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.RtsService;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * {@link RtsPageServiceImpl} 的默认实现——处理远程储存浏览器的页面构建和刷新。
 *
 * <p>该实现类负责：
 * <ul>
 *   <li>接收并处理客户端的页面请求（搜索、排序、分类、分页）</li>
 *   <li>调用 {@link com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder} 构建页面数据</li>
 *   <li>通过 {@link com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService} 注册缓存</li>
 *   <li>记录最近使用的物品到会话</li>
 *   <li>标记存储视图为脏以触发客户端刷新</li>
 * </ul>
 */
public final class RtsPageServiceImpl implements RtsService {

    private final RtsServer server = RtsServer.get();

    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending) {
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);
        boolean pinyinSearchEnabled = session != null && session.browser.pinyinSearchEnabled;
        List<String> localizedSearchMatches = session == null ? List.of() : List.copyOf(session.browser.localizedSearchMatches);
        int pageSize = session == null ? RtsStoragePageBuilder.DEFAULT_PAGE_SIZE : session.browser.pageSize;
        requestPage(player, page, search, category, sort, ascending,
                pageSize, pinyinSearchEnabled, localizedSearchMatches);
    }

    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled) {
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);
        List<String> localizedSearchMatches = session == null ? List.of() : List.copyOf(session.browser.localizedSearchMatches);
        int pageSize = session == null ? RtsStoragePageBuilder.DEFAULT_PAGE_SIZE : session.browser.pageSize;
        requestPage(player, page, search, category, sort, ascending,
                pageSize, pinyinSearchEnabled, localizedSearchMatches);
    }

    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, int pageSize,
                            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        long perfStartNanos = System.nanoTime();
        RtsStorageSession session = server.session().getOrCreate(player);
        refreshMissingGuiBindingIcons(player, session);
        String newSearch = search == null ? "" : search;
        String newCategory = RtsStoragePageBuilder.normalizeCategory(category);
        RtsStorageSort newSort = sort == null ? RtsStorageSort.QUANTITY : sort;
        int newPageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        // 浏览器状态变化检测：纯分页/自动刷新且状态未变时跳过 NBT 写盘，
        // 避免每次 REQUEST_PAGE（含搜索按键）都触发整包序列化（B8 边界性能优化）。
        boolean browserChanged = !session.browser.search.equals(newSearch)
                || !session.browser.category.equals(newCategory)
                || session.browser.sort != newSort
                || session.browser.ascending != ascending
                || session.browser.pageSize != newPageSize
                || session.browser.pinyinSearchEnabled != pinyinSearchEnabled;
        int oldPage = session.browser.page;
        int recentModCount = session.uiMemory.getRecentModCount();
        session.browser.search = newSearch;
        session.browser.category = newCategory;
        session.browser.sort = newSort;
        session.browser.ascending = ascending;
        session.browser.pageSize = newPageSize;
        session.browser.pinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.localizedSearchMatches.clear();
        session.browser.localizedSearchMatches.addAll(
                RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;

        List<LinkedHandler> activeHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        RtsLinkedHandlerResolutionService.registerStorageCaches(player, activeHandlers);
        long perfResolveMs = (System.nanoTime() - perfStartNanos) / 1_000_000L;
        var result = RtsStoragePageBuilder.build(
                player, session, page, session.browser.pageSize,
                activeHandlers, activeFluidHandlers);
        long perfBuildMs = (System.nanoTime() - perfStartNanos) / 1_000_000L;
        PacketDistributor.sendToPlayer(player, result.payload());
        session.transfer.storageViewDirty = false;
        session.browser.page = result.safePage();
        // 浏览器状态、翻页或最近条目任一变化才写盘（B8 优化）。
        // recent 变化通过 uiMemory 修改计数追踪，避免漏存 recent 更新。
        if (browserChanged || result.safePage() != oldPage
                || recentModCount != session.uiMemory.getSavedRecentModCount()) {
            server.session().saveToPlayerNbt(player, session);
            session.uiMemory.markRecentSaved();
        }
        long perfTotalMs = (System.nanoTime() - perfStartNanos) / 1_000_000L;

        if (perfTotalMs >= 30L) {
            com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.info(
                    "RTS-PERF: requestPage resolve+register={} ms, build={} ms, saveToPlayerNbt={} ms, total={} ms (player={})",
                    perfResolveMs, perfBuildMs - perfResolveMs, perfTotalMs - perfBuildMs,
                    perfTotalMs, player.getName().getString());
        }
    }

    public void markStorageViewDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return;
        if (session.transfer.storageViewDirty) return;
        session.transfer.storageViewDirty = true;
        PacketDistributor.sendToPlayer(player, new S2CRtsStorageDirtyPayload(true));
    }

    public void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        RtsStorageRecentEntries.recordRecentItem(session, itemId, kind, amount);
    }

    public void removeRecentItem(ServerPlayer player, String itemId) {
        if (player == null) return;
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) return;
        if (RtsStorageRecentEntries.removeRecentEntry(session, itemId)) {
            server.session().saveToPlayerNbt(player, session);
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private void refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        if (RtsStorageBindings.refreshMissingGuiBindingIcons(player, session)) {
            server.session().saveToPlayerNbt(player, session);
        }
    }
}
