# 接口整改 Plan：`EbgDsCompareServiceImpl.getTaskList` N+1 查询优化

## 一、背景与目标

检视意见（严重，衡兆雷 30020184，置信度 9）指出：`EbgDsCompareServiceImpl.getTaskList` 的 `forEach` 循环中（第142-148行 accountName 查询 + 第150-155行白名单查询），对列表每一项执行数据库查询：

- `customerOrgSdkDao.getAccountByNumber(...)`（客户组织名称）
- `siteNetworkConfigDao.countBySiteNetworkId(...)`（是否白名单）

构成典型的 **N+1 查询性能瓶颈**。当 `taskList` 数据量较大时，频繁发起数据库连接和查询，易导致数据库负载过高、响应时间增加甚至服务超时；循环内频繁创建 `ArrayList` 也增加内存分配开销。

**目标**：循环前批量查询，封装为 `Map`/`Set` 缓存到内存，循环内直接从内存集合取值，消除循环内 DB IO。

---

## 二、现状梳理（代码链路）

```
EbgDsCompareController.getEbgDsCompareList (POST /ebg/ds/compare-list)
  └─ ebgDsCompareService.getTaskList(bo)                 // EbgDsCompareServiceImpl
       ├─ ebgCompDsLogDao.getTaskList(bo, compPermissionBo) // 返回 List<EbgCompareListRespVo>
       └─ taskList.forEach(vo -> { ... })                // 循环内 N+1 查库
            ├─ customerOrgSdkDao.getAccountByNumber(...)   // accountName（第142-148行）
            └─ siteNetworkConfigDao.countBySiteNetworkId(...) // 白名单（第150-155行）
```

### 2.1 关键类与文件

| 层   | 文件  | 说明  |
| --- | --- | --- |
| Service | `service/.../ebg/service/impl/EbgDsCompareServiceImpl.java` | `getTaskList`（第110-163行），N+1 所在 |
| DAO | `service/.../dao/SiteNetworkConfigDao.java` | 白名单 DAO，仅 `countBySiteNetworkId`（单条） |
| Mapper | `service/src/main/resources/mapper/SiteNetworkConfigMapper.xml` | 白名单 SQL |
| 外部SDK | `CustomerOrgSdkDao.getAccountByNumber(List<String>)` | 客户组织名称，外部依赖，返回类型未知 |

### 2.2 现状关键点

1. **accountName**：`getAccountByNumber(List<String>)` 接收账号列表，返回实体列表（元素有 `getAccountName()`）。当前循环内单条调用。
2. **白名单**：`countBySiteNetworkId(siteNetworkId, bgType)` 单条判断，返回命中数。当前循环内单条调用。
3. **外部SDK返回类型**：`CustomerOrgSdkDao` 来自 `impcen-commonsdk`（外部依赖，本地无 jar），无法确认返回实体是否含 `getAccountNumber()`。检视意见示例使用 `AccountEntity.getAccountNumber()`/`getAccountName()`。

---

## 三、整改方案

### 3.1 改动点总览

```
SiteNetworkConfigDao (新增 selectWhitelistedSiteNetworkIds 批量查询)
   ↓
SiteNetworkConfigMapper.xml (新增批量查询 SQL)
   ↓
EbgDsCompareServiceImpl.getTaskList (循环前批量查询 → Map/Set 缓存 → 循环内取内存)
```

### 3.2 具体改动点

#### ① DAO：`SiteNetworkConfigDao.java` 新增批量白名单查询

```java
/**
 * 查询指定作业场景下所有白名单局点网络/客户组织标识（未删除）
 *
 * @param bgType 作业场景（CNBG/EBG）
 * @return 白名单标识列表
 */
List<String> selectWhitelistedSiteNetworkIds(@Param("bgType") String bgType);
```

#### ② Mapper：`SiteNetworkConfigMapper.xml` 新增批量查询 SQL

```xml
<select id="selectWhitelistedSiteNetworkIds" resultType="java.lang.String">
    SELECT site_network_id FROM t_site_network_config
    WHERE bg_type = #{bgType}
      AND is_delete = '0'
</select>
```

#### ③ Service：`EbgDsCompareServiceImpl.getTaskList` 消除 N+1

在 `forEach` 循环**前**批量查询并缓存，循环内直接取内存：

```java
// 循环前：收集所有非空 accountNumber（去重）
List<String> accountNumbers = taskList.stream()
        .map(EbgCompareListRespVo::getAccountNumber)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());

// 循环前：批量查询客户组织名称 → Map<accountNumber, accountName>
Map<String, String> accountNameMap = new HashMap<>();
if (!accountNumbers.isEmpty()) {
    var accountList = customerOrgSdkDao.getAccountByNumber(accountNumbers);
    if (accountList != null) {
        for (var acc : accountList) {
            accountNameMap.put(acc.getAccountNumber(), acc.getAccountName());
        }
    }
}

// 循环前：批量查询白名单 → Set<accountNumber>
Set<String> whitelistedAccountSet = new HashSet<>();
if (ProductLineEnum.DATA_STORAGE.getProductLineCode()
        .equals(taskList.stream().map(EbgCompareListRespVo::getProductLineCode)
                .findFirst().orElse(null))) {
    // 仅数据存储产品线需要白名单判断
    List<String> whitelistedIds = siteNetworkConfigDao
            .selectWhitelistedSiteNetworkIds(BgTypeEnum.EBG.getBgType());
    if (whitelistedIds != null) {
        whitelistedAccountSet.addAll(whitelistedIds);
    }
}
```

`forEach` 循环内改为：

```java
taskList.forEach(vo -> {
    // ... 其他逻辑（regionName/officeName 等不变）...
    // 客户组织名称 - 从内存 Map 获取
    vo.setAccountName(StringUtils.isNotBlank(vo.getAccountNumber())
            ? accountNameMap.get(vo.getAccountNumber())
            : null);
    // 是否白名单（仅数据存储产品线判断；其他产品线不返回）
    if (ProductLineEnum.DATA_STORAGE.getProductLineCode().equals(vo.getProductLineCode())) {
        boolean isWhitelisted = StringUtils.isNotBlank(vo.getAccountNumber())
                && whitelistedAccountSet.contains(vo.getAccountNumber());
        vo.setIsAccountWhitelisted(isWhitelisted ? "0" : "1");
    }
});
```

> 说明：
> 
> - **accountName**：`getAccountByNumber` 返回实体需含 `getAccountNumber()`（检视意见示例），据此构建 `Map`。若实体不含该方法，需调整（见「待确认事项」）。
> - **白名单**：`selectWhitelistedSiteNetworkIds` 一次查出该 bgType 下全部白名单标识，构建 `Set`，循环内 `contains` 判断，O(1)。
> - **产品线隔离**：白名单仅数据存储产品线返回。批量查询白名单时，仅当列表中存在数据存储产品线才查询（避免无谓查询）。
> - **accountNumber 为空**：accountName 返回 null；白名单视为不在，返回 `"1"`（与现有一致）。

---

## 四、改动文件清单

| #   | 文件  | 改动类型 |
| --- | --- | --- |
| 1   | `service/.../dao/SiteNetworkConfigDao.java` | 新增 `selectWhitelistedSiteNetworkIds` 方法 |
| 2   | `service/src/main/resources/mapper/SiteNetworkConfigMapper.xml` | 新增批量查询 SQL |
| 3   | `service/.../ebg/service/impl/EbgDsCompareServiceImpl.java` | `getTaskList` 循环前批量查询 + 循环内取内存 |

---

## 五、验证方案

1. **编译**：IDEA 编译通过（本地无 Maven）。
2. **接口调用**：`POST /ebg/ds/compare-list`，出参 `accountName`、`isAccountWhitelisted` 与改造前一致。
3. **性能**：确认循环内不再有 `getAccountByNumber`/`countBySiteNetworkId` DB 调用（日志/断点验证）。
4. **白名单判断**：数据存储产品线 + 客户组织在白名单 → `"0"`；不在 → `"1"`。
5. **产品线隔离**：非数据存储产品线不返回 `isAccountWhitelisted`（null）。

---

## 六、待确认事项

1. **`getAccountByNumber` 返回实体是否含 `getAccountNumber()`**：外部 SDK（`impcen-commonsdk`）本地无 jar，无法确认。检视意见示例使用 `AccountEntity.getAccountNumber()`。若实体不含该方法，需改用其他方式构建 `Map`（如按返回顺序与入参对应，或改用 SDK 其他接口）。**需在 IDEA 编译验证**。
