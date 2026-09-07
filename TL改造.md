# 导入清表状态 ThreadLocal 改造方案

## 背景与问题

6 个线下表导入功能（ABCD划分清单、伙伴地图、星星点灯、产品类别映射、核心部件、卓战核白分类）在"全部导入失败不执行清表 DeleteAll"修复中，使用 `public static boolean isFirstCurrentData` 静态字段记录"是否已清表"。

**问题**：静态字段跨请求共享，存在线程安全问题。两个并发导入会互相干扰——A 请求把 flag 置 false 后，B 请求的第一个有效数据会跳过 `deleteAll()`，导致新旧数据混存。

虽然该场景为管理员操作、并发极少，但为彻底消除隐患，采用 **ThreadLocal** 方案改造。

## 方案核心思想

用 `ThreadLocal<Boolean>` 替代静态字段。每个请求线程持有独立的副本，天然线程隔离——A 请求的 `set(false)` 不影响 B 请求的 `get()`。

**关键前提**：Spring MVC 中 controller → service 是同步调用，同一请求在同一线程内执行，所以 ThreadLocal 在 controller 设置、service 读取是有效的。

## 命名约定

| 原静态字段 | 新 ThreadLocal 字段 | 重置方法 | 清理方法 |
| --- | --- | --- | --- |
| `isFirstCurrentData` | `HAS_CORRECT_DATA` | `resetHasCorrectData()` | `clearHasCorrectData()` |

## 具体改动（以 ABCD 划分清单为例，共 3 处）

### 改动1：Service 字段与封装方法（`EbgProductBomTypeServiceImpl`）

**改前：**

```java
/**
 * 判断是否是第一个正确的数据
 * 用于：若数据全错则不清表
 */
public static boolean isFirstCurrentData = true;
```

**改后：**

```java
/**
 * 判断是否已存在正确的数据（线程隔离，避免并发导入互相干扰）
 * 用于：若数据全错则不清表
 */
private static final ThreadLocal<Boolean> HAS_CORRECT_DATA = ThreadLocal.withInitial(() -> true);

/**
 * 重置导入判定（每次导入前调用）
 */
public static void resetHasCorrectData() {
    HAS_CORRECT_DATA.set(true);
}

/**
 * 清理线程局部变量（导入结束后调用，避免线程池复用残留）
 */
public static void clearHasCorrectData() {
    HAS_CORRECT_DATA.remove();
}
```

### 改动2：Service 的 `saveExcelData` 判断逻辑

**改前：**

```java
if (isFirstCurrentData) {
    deleteAll();
    isFirstCurrentData = false;
}
```

**改后：**

```java
if (HAS_CORRECT_DATA.get()) {
    deleteAll();
    HAS_CORRECT_DATA.set(false);
}
```

### 改动3：Controller 导入方法

**改前：**

```java
EbgProductBomTypeServiceImpl.isFirstCurrentData = true;
Result result = ebgProductBomTypeService.excelImport(file, request, response);
```

**改后：**

```java
EbgProductBomTypeServiceImpl.resetHasCorrectData();
try {
    Result result = ebgProductBomTypeService.excelImport(file, request, response);
    return result;
} finally {
    EbgProductBomTypeServiceImpl.clearHasCorrectData();
}
```

## 方案要点

1. **线程隔离**：`ThreadLocal.withInitial(() -> true)` 每个线程首次 `get()` 返回 true，之后 `set(false)` 只影响当前线程，并发导入互不干扰。
  
2. **`withInitial` 优于 `set(true)` 初始化**：保证每个新线程首次访问时自动得到 true，即使 controller 忘记重置，也不会出现"上次残留 false 导致本次不清表"的问题（比静态字段更健壮）。
  
3. **finally 清理**：`clearHasCorrectData()` 调用 `remove()`，防止线程池复用导致 ThreadLocal 值残留。虽然每次导入前 `resetHasCorrectData()` 会覆盖，但规范起见仍清理。
  
4. **方法封装**：把 `set(true)`/`remove()` 封装成 `resetHasCorrectData()`/`clearHasCorrectData()`，避免 controller 直接操作 ThreadLocal 内部细节，也便于后续复用。
  

## 应用到其他 5 个导入

以下 5 个导入结构完全一致，按上述 3 处改动逐一改造即可：

| 导入  | Service | Controller 方法 |
| --- | --- | --- |
| 伙伴地图 | `EbgPartnerMapServiceImpl` | `excelImport` |
| 星星点灯 | `EbgStarryPartnerServiceImpl` | `excelImportStarryPartner` |
| 产品类别映射 | `EbgProductCategoryTypeServiceImpl` | `excelImportProductCategoryType` |
| 核心部件 | `EbgCoreComponentServiceImpl` | `excelImportCoreComponent` |
| 卓战核白分类 | `EbgCustomerNaLevelServiceImpl` | `excelImportCustomerNaLevel` |

## 注意事项

- `ThreadLocal` 属于 `java.lang` 包，无需 import。
- 改造后需删除 controller 中对应的 `XxxServiceImpl.isFirstCurrentData = true;` 旧语句，替换为 `resetHasCorrectData()`。
- 若 `saveExcelData` 内部存在异步或事务代理切换线程的场景，ThreadLocal 会失效（本项目为同步调用，无此问题）。
