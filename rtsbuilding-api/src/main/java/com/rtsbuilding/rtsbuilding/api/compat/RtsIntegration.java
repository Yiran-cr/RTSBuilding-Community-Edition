package com.rtsbuilding.rtsbuilding.api.compat;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * 宿主 mod 集成（addon）的统一生命周期抽象。
 *
 * <p>每个内置 addon（AE2 / Refined Storage / Beyond Dimensions / Sophisticated Backpacks）
 * 实现本接口并在构造时通过 {@link RtsCompatRegistry#registerIntegration(RtsIntegration)}
 * 注册。主 mod 通过 {@link RtsCompatRegistry#getIntegrations()} 统一消费：
 *
 * <ul>
 *   <li><b>发现</b>：{@link #integrationId()} + {@link #available()} 决定是否生效；</li>
 *   <li><b>健康检查</b>：{@link #selfCheck()} 返回反射绑定/编译依赖的自检诊断串，失败时主 mod 打 WARN 并在集成面板展示；</li>
 *   <li><b>注册</b>：{@link #register(RtsCompatRegistry)} 统一向注册表注入 storage/fluid/backpack/icon provider。</li>
 * </ul>
 *
 * <p>这是阶段二（Addon 集成统一抽象）的 SPI 基础。实现方在 {@code @Mod} 构造内调用
 * {@code registerIntegration(...)}；host 未加载或反射失败时应返回 {@code available()==false}
 * 并让 {@link #selfCheck()} 给出原因，而不是静默跳过。</p>
 *
 * @see RtsCompatRegistry
 */
@ApiStatus.Experimental
public interface RtsIntegration {

    /** 集成标识（日志 / 诊断用），如 {@code "ae2"}、{@code "refinedstorage"}。 */
    String integrationId();

    /** 宿主 mod 是否已加载且本集成可生效（反射绑定成功等）。 */
    boolean available();

    /**
     * 自检当前绑定的健康度。
     *
     * @return 诊断串（如 {@code "missing: clStorageComponent"}）；健康时返回 {@code null} 或空串。
     */
    @Nullable
    String selfCheck();

    /**
     * 统一注册入口：向注册表注入本集成提供的 provider / 图标解析器等。
     *
     * @param registry 兼容注册表
     */
    void register(RtsCompatRegistry registry);
}
