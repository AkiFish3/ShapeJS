# ShapeJS

**给 KubeJS 的方块加上「按 blockstate 动态碰撞 / 选择盒」。**（Fabric 1.20.1）

## 为什么需要它

KubeJS 的 `BlockBuilder` 只能给方块配**一个静态形状**：

| API | 实际行为 |
|---|---|
| `b.box(...)` / `b.customShape` | 追加到 `List<AABB>`，构造时合成**一个** `VoxelShape` |
| `BasicBlockJS.shape` | **单值字段** |
| `HorizontalDirectionalBlockJS.shapes` | `Map<Direction, VoxelShape>`，**只按 facing 分派** |
| `b.createCustom(...)` | 不走 builder，会丢掉 `property` / `placementState` / `blockEntity` |

⇒ **没有任何「按属性返回不同形状」的入口。** 想要「`half=top` 用上半格、`shape=inner_left` 用 L 形」就办不到。

ShapeJS 用 mixin 在 `getOutlineShape` / `getCollisionShape` 上接管，把这个入口补上。

> 顺带一个 1.20.1 的冷知识（javap 实证）：
> `AbstractBlock.getCollisionShape` → `BlockState.getCollisionShape` → `AbstractBlockState.getCollisionShape(world,pos,ctx)` → **`Block.getOutlineShape`**。
> 也就是说**这一版碰撞盒和选择盒本来就是同一个方法**，所以接管一处两者同时生效。

## 用法

```js
StartupEvents.registry('block', event => {
    let b = event.create('mymod:slanted_panel', 'basic')

    // ① 任意盒：同一个键写多条 = 取并集 ⇒ L 形 / 不规则形
    b.dynShape('half=bottom', 0, 0, 0, 16, 8, 16)
    b.dynShape('half=top',    0, 8, 0, 16, 16, 16)
    b.dynShape('shape=inner_left', 0, 0, 0, 16, 16, 8)
    b.dynShape('shape=inner_left', 8, 0, 8, 16, 16, 16)

    // ② 实心斜坡（楼梯式碰撞），4 级、向南升高
    b.dynSlope('facing=north', 'south', 4)
    b.dynSlope('facing=north', 'south', 4, 0, 0, 0, 16, 16, 16)   // 指定范围

    // ③ 薄板斜面（斜玻璃/斜板）：一条 2 像素厚的对角带，8 级
    b.dynSlopePanel('facing=north', 'south', 8, 2)

    // ④ 批量 / 清空
    b.dynShapeList(['half=bottom', 'half=top'], [[0,0,0,16,8,16], [0,8,0,16,16,16]])
    b.dynShapeClear()

    // 没装 ShapeJS 时上面的调用不存在 ⇒ 保留 b.box(...) 兜底即可
    b.box(0, 0, 0, 16, 8, 16)
})
```

### 规则语法

- **键** = blockstate `variants` 的键语法（`属性=值`，逗号分隔），**子集匹配**：键里没写的属性匹配任意值。
  例如 `half=top` 对 `facing`/`shape`/`waterlogged` 的全部取值都生效。
- **多条命中时取「最具体」的那一组**（匹配上的属性对最多）并集；同样具体的多条一起并。
- **没有任何命中 ⇒ 回落 KubeJS 原有形状**，不影响没登记规则的方块。
- **坐标是像素**（0..16），内部统一 `/16`。
- 命中后**同时替换碰撞盒和选择盒**。

## 实现

| 文件 | 作用 |
|---|---|
| `mixin/AbstractBlockMixin` | 注入 `AbstractBlock.getCollisionShape`（HEAD，cancellable）——兜底层，覆盖所有没覆写该方法的方块 |
| `mixin/BasicBlockJSMixin` | 注入 `BasicBlockJS.getOutlineShape` —— KubeJS `basic` 型 |
| `mixin/HorizontalDirectionalBlockJSMixin` | 注入 `HorizontalDirectionalBlockJS.getOutlineShape` —— KubeJS `cardinal` 型 |
| `mixin/BlockBuilderMixin` | 给 `BlockBuilder` **新增** `dynShape` / `dynShapeList` / `dynSlope` / `dynSlopePanel` / `dynShapeClear` 五个方法 |
| `DynamicShapes` | 规则表 + 匹配 + 两级缓存 |

- 目标是 KubeJS 的类，但用的是**新增方法**（不是 `@Inject`），所以**编译期不需要 KubeJS 依赖**，jar 里也不会打进 KubeJS 的类。
- 方块 → 规则通过 KubeJS 自带的 `Block.kjs$getBlockBuilder()`（反射）反查，对 `basic` / `cardinal` / `shaped` 全部类型通用。
- **性能**：`getCollisionShape` 是热路径（实体移动每 tick 每方块都调），所以做了两级 `IdentityHashMap` 缓存
  （`Block → 规则表`、`BlockState → VoxelShape`），「查过没有规则」也会记住，不重复反射。

## 构建

需要 JDK 17+（Gradle 8.3 不支持 JDK 25，请用 JDK 21）。

```bash
./gradlew build
# 产物：build/libs/shapejs-<version>.jar
```

把 jar 丢进 `mods/`，配合 KubeJS 使用。

## 许可

CC0-1.0
