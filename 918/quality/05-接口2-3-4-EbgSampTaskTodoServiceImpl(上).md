# 05-接口2-3-4-EbgSampTaskTodoServiceImpl(上).md

> 本文件包含以下源码（按包结构整理）

---

```
=== service/src/main/java/com/huawei/qua/ebg/service/impl/EbgSampTaskTodoServiceImpl.java ===
```

```java
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.qua.ebg.service.impl;

import static com.huawei.dispatchcenter.amsservice.validator.RegexpExpression.PHONE_REGEX;
import static com.huawei.qua.exception.InspectionErrorCodeEnum.DATA_IS_NOT_EXIST;

import com.huawei.dispatchcenter.amsservice.common.exception.BaseException;
import com.huawei.dispatchcenter.amsservice.common.exception.ExceptionPrint;
import com.huawei.dispatchcenter.amsservice.common.log.MessageUtil;
import com.huawei.dispatchcenter.amsservice.permission.bo.UserInfoBo;
import com.huawei.dispatchcenter.amsservice.session.UserSessionUtil;
import com.huawei.dispatchcenter.amsservice.utils.AnonytionUtils;
import com.huawei.dispatchcenter.amsservice.utils.KmsCryptoUtil;
import com.huawei.dispatchcenter.amsservice.utils.LocalBeanUtil;
import com.huawei.dispatchcenter.amsservice.utils.LocaleUtil;
import com.huawei.dispatchcenter.amsservice.utils.Result;
import com.huawei.dispatchcenter.amsservice.utils.UserUtil;
import com.huawei.dispatchcenter.amsservice.utils.edm.EdmClientUtil;
import com.huawei.dispatchcenter.amsservice.utils.permission.SystemConfigSdkUtil;
import com.huawei.it.edm.client.exception.EdmException;
import com.huawei.qua.common.constants.Constants;
import com.huawei.qua.common.constants.DataConstants;
import com.huawei.qua.common.constants.FileConstants;
import com.huawei.qua.common.enums.inspection.QualityResultEnum;
import com.huawei.qua.common.util.CommonUtil;
import com.huawei.qua.common.util.RoleUtil;
import com.huawei.qua.dao.entity.FileEntity;
import com.huawei.qua.ebg.bo.MajorInfoBo;
import com.huawei.qua.ebg.bo.NotifySendRecordBo;
import com.huawei.qua.ebg.bo.callcenter.EbgCallRecordBo;
import com.huawei.qua.ebg.bo.callcenter.EbgCallRecordRespBo;
import com.huawei.qua.ebg.bo.callcenter.EbgSrFileBo;
import com.huawei.qua.ebg.bo.samp.DeleteEbgSampTaskTodoAttachmentReqBo;
import com.huawei.qua.ebg.bo.samp.EbgSampTaskCategoryBo;
import com.huawei.qua.ebg.bo.samp.EbgSampTaskCategoryResultBo;
import com.huawei.qua.ebg.bo.samp.EbgSampTaskIndicatorResultBo;
import com.huawei.qua.ebg.bo.samp.EbgSampTaskTodoAttachmentBo;
import com.huawei.qua.ebg.bo.samp.EbgSampTaskTodoBo;
import com.huawei.qua.ebg.bo.samp.EbgSampTaskTodoLogBo;
import com.huawei.qua.ebg.bo.samp.GetEbgSampTaskTodoAttachmentReqBo;
import com.huawei.qua.ebg.bo.samp.GetEbgSampTaskTodoBatchReqBo;
import com.huawei.qua.ebg.bo.samp.GetEbgSampTaskTodoCommitReqBo;
import com.huawei.qua.ebg.bo.samp.GetEbgSampTaskTodoDetailReqBo;
import com.huawei.qua.ebg.bo.samp.GetEbgSampTaskTodoTransferReqBo;
import com.huawei.qua.ebg.bo.samp.SaveEbgSampTaskBaseReqBo;
import com.huawei.qua.ebg.bo.samp.SaveEbgSampTaskTodoReqBo;
import com.huawei.qua.ebg.bo.samp.TransferBatchReqBo;
import com.huawei.qua.ebg.bo.srcheck.EbgAuditBo;
import com.huawei.qua.ebg.bo.srcheck.EbgNodeBo;
import com.huawei.qua.ebg.bo.srcheck.EbgSrOrderBo;
import com.huawei.qua.ebg.bo.srcheck.EbgTaskBo;
import com.huawei.qua.ebg.bo.task.TodoRuleTaskResultReqBo;
import com.huawei.qua.ebg.common.EbgConstants;
import com.huawei.qua.ebg.common.enums.CheckTypeEnum;
import com.huawei.qua.ebg.common.enums.EbgBusiTypeEnum;
import com.huawei.qua.ebg.common.enums.EbgSampApprovalTypeEnum;
import com.huawei.qua.ebg.common.enums.EbgSampCheckStatusEnum;
import com.huawei.qua.ebg.common.enums.EbgSampOperateTypeEnum;
import com.huawei.qua.ebg.common.enums.EbgTimeFieldEnum;
import com.huawei.qua.ebg.common.utils.AccountUtil;
import com.huawei.qua.ebg.dao.EbgAuditDao;
import com.huawei.qua.ebg.dao.EbgNodeDao;
import com.huawei.qua.ebg.dao.EbgSrOrderDao;
import com.huawei.qua.ebg.dao.EbgTaskDao;
import com.huawei.qua.ebg.dao.MajorIssueDao;
import com.huawei.qua.ebg.dao.callcenter.EbgCallRecordDao;
import com.huawei.qua.ebg.dao.callcenter.EbgSrFileDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskBaseDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskCategoryResultDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskIndicatorResultDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskTodoAttachmentDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskTodoDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskTodoLogDao;
import com.huawei.qua.ebg.outer.vo.samp.EbgSampTaskTodoAttachmentVo;
import com.huawei.qua.ebg.outer.vo.samp.EbgSampTaskTodoLogVo;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskBaseVo;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskCategoryResultVo;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskResultVo;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskTimeVo;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskTodoVo;
import com.huawei.qua.ebg.outer.vo.task.GetEbgSampTaskRecordReqVo;
import com.huawei.qua.ebg.outer.vo.task.TransferBatchRespVo;
import com.huawei.qua.ebg.outer.vo.task.TransferFailItemVo;
import com.huawei.qua.ebg.service.EbgSampTaskTodoService;
import com.huawei.qua.exception.QuaErrorCodeEnum;
import com.huawei.qua.service.FileService;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.pagehelper.PageInfo;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 采样任务代办实现类
 *
 * @author gwx1503283
 * @since 2026-06-02
 */
@Service
public class EbgSampTaskTodoServiceImpl implements EbgSampTaskTodoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EbgSampTaskTodoServiceImpl.class);

    // 判断是否符合手机号正则
    private static final Pattern VALIDATE_PHONE = Pattern.compile(PHONE_REGEX);

    @Autowired
    private EbgSampTaskIndicatorResultDao ebgSampTaskIndicatorResultDao;

    @Autowired
    private EbgSampTaskTodoDao ebgSampTaskTodoDao;

    @Autowired
    private EbgSampTaskBaseDao ebgSampTaskBaseDao;

    @Autowired
    private EbgSampTaskTodoAttachmentDao ebgSampTaskTodoAttachmentDao;

    @Autowired
    private EbgSampTaskCategoryResultDao ebgSampTaskCategoryResultDao;

    @Autowired
    private EbgSampTaskTodoLogDao ebgSampTaskTodoLogDao;

    @Autowired
    private EbgCallRecordDao ebgCallRecordDao;

    @Autowired
    private EbgSrFileDao ebgSrFileDao;

    @Resource
    private EdmClientUtil edmClientUtil;

    @Autowired
    private AccountUtil accountUtil;

    @Autowired
    private EbgAuditDao ebgAuditDao;

    @Resource
    private EbgSrOrderDao srOrderDao;

    @Resource
    private EbgTaskDao taskDao;

    @Resource
    private EbgNodeDao nodeDao;

    @Autowired
    private MajorIssueDao majorIssueDao;

    @Resource
    private FileService fileService;

    /**
     * 采样待办任务详情基础信息查询
     *
     * @param reqBo 采样任务和分页信息
     * @return 任务详情基础信息
     */
    @Override
    public Result query(GetEbgSampTaskTodoDetailReqBo reqBo) {
        if (!StringUtils.isEmpty(reqBo.getBusiType())) {
            if (EbgBusiTypeEnum.ECARE.getType().equals(reqBo.getBusiType())) {
                EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(reqBo.getTodoId());
                todoConvertName(ebgSampTaskTodoVo);
                return Result.success(ebgSampTaskTodoVo);
            } else {
                EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(reqBo.getTodoId());
                baseConvertName(ebgSampTaskBaseVo);
                return Result.success(ebgSampTaskBaseVo);
            }
        } else {
            EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(reqBo.getTodoId());
            if (ObjectUtils.isNotEmpty(ebgSampTaskTodoVo)) {
                todoConvertName(ebgSampTaskTodoVo);
                return Result.success(ebgSampTaskTodoVo);
            }
            EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(reqBo.getTodoId());
            if (ObjectUtils.isNotEmpty(ebgSampTaskBaseVo)) {
                baseConvertName(ebgSampTaskBaseVo);
                return Result.success(ebgSampTaskBaseVo);
            }
        }
        return Result.success();
    }

    private void baseConvertName(EbgSampTaskBaseVo ebgSampTaskBaseVo) {
        ebgSampTaskBaseVo.setPseAssessorName(AccountUtil.batchGetUserNames(ebgSampTaskBaseVo.getPseAssessor()));
        ebgSampTaskBaseVo.setCseAssessorName(AccountUtil.batchGetUserNames(ebgSampTaskBaseVo.getCseAssessor()));
        UserInfoBo userInfoBo = UserUtil.getUserInfoBoByAccount(ebgSampTaskBaseVo.getAssessor());
        ebgSampTaskBaseVo.setAssessorName(userInfoBo != null ? userInfoBo.getFullName() : "");
        UserInfoBo createUser = UserUtil.getUserInfoBoByAccount(ebgSampTaskBaseVo.getCreateUser());
        ebgSampTaskBaseVo.setCreateUserName(createUser != null ? createUser.getFullName() : "");
        ebgSampTaskBaseVo.setProductLine(CommonUtil.getDictDataNameCn(EbgConstants.EBG_SR_SERVICE_GROUPS,
            ebgSampTaskBaseVo.getProductLine()));
    }

    private void todoConvertName(EbgSampTaskTodoVo ebgSampTaskTodoVo) {
        ebgSampTaskTodoVo.setCcrAssessorName(AccountUtil.batchGetUserNames(ebgSampTaskTodoVo.getCcrAssessor()));
        ebgSampTaskTodoVo.setCseAssessorName(AccountUtil.batchGetUserNames(ebgSampTaskTodoVo.getCseAssessor()));
        UserInfoBo userInfoBo = UserUtil.getUserInfoBoByAccount(ebgSampTaskTodoVo.getAssessor());
        ebgSampTaskTodoVo.setAssessorName(userInfoBo != null ? userInfoBo.getFullName() : "");
        UserInfoBo createUser = UserUtil.getUserInfoBoByAccount(ebgSampTaskTodoVo.getCreateUser());
        ebgSampTaskTodoVo.setCreateUserName(createUser != null ? createUser.getFullName() : "");
    }

    private String mergeProcessors(String... processors) {
        return Arrays.stream(processors).filter(StringUtils::isNotBlank).distinct().collect(Collectors.joining(","));
    }

    /**
     * 申诉受理槽位：封装单个受理人（当前处理器、确认角色常量、处理器调用）
     */
    private record AppealSlot(
        String processor,
        String roleConst,
        Supplier<Integer> handler) {
    }

    /**
     * 申诉处理器支持：封装 代办/base 两套 VO/save 的类型差异访问器
     */
    private record AppealProcessSupport<T, S>(
        T vo,
        S save,
        Function<T, Integer> getCheckStatus,
        Function<T, String> getFinalReviewProcessor,
        BiConsumer<S, Integer> setCheckStatus,
        BiConsumer<S, String> setCurrentProcessor,
        BiConsumer<S, String> setProcessor,
        BiConsumer<S, Date> setFinishTime,
        Function<S, Integer> update,
        EbgSampOperateTypeEnum operateType,
        Function<T, String> getRemark,
        Function<S, Integer> getSaveCheckStatus,
        Function<T, String> getRemainingProcessor) {
    }

    /**
     * 申诉编排模板：双受理槽位校验 + 确认分发 + 汇总
     *
     * @param userAccount userAccount
     * @param indicatorList indicatorList
     * @param checkStatus checkStatus
     * @param slotA slotA
     * @param slotB slotB
     * @param currentProcessor currentProcessor
     * @return int
     */
    private int doAppealResult(
            String userAccount,
            List<EbgSampTaskIndicatorResultBo> indicatorList,
            Integer checkStatus,
            String currentProcessor,
            AppealSlot slotA, AppealSlot slotB) {
        int resultA = 1;
        int resultB = 1;
        if (!EbgSampCheckStatusEnum.PENDING_APPEAL.getStatus().equals(checkStatus)) {
            throw new BaseException(QuaErrorCodeEnum.OPERATE_STATUS_ERROR.getErrorCode());
        }
        long currentUserId = Objects.requireNonNull(UserSessionUtil.getSession()).getUserId();
        if ((!userAccount.equals(slotA.processor()) && !userAccount.equals(slotB.processor()))
                && !RoleUtil.isSysOrModuleAdmin(currentUserId)) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        if (isSlotPending(slotA, currentProcessor) && (userAccount.equals(slotA.processor())
                || RoleUtil.isSysOrModuleAdmin(currentUserId))) {
            if (checkConfirm(indicatorList, slotA.roleConst())) {
                throw new BaseException(QuaErrorCodeEnum.CONFIRM_ERROR.getErrorCode());
            }
            resultA = slotA.handler().get();
        }
        if (isSlotPending(slotB, currentProcessor) && (userAccount.equals(slotB.processor())
                || RoleUtil.isSysOrModuleAdmin(currentUserId))) {
            if (checkConfirm(indicatorList, slotB.roleConst())) {
                throw new BaseException(QuaErrorCodeEnum.CONFIRM_ERROR.getErrorCode());
            }
            resultB = slotB.handler().get();
        }
        return (resultA == 1 && resultB == 1) ? 1 : 0;
    }

    private boolean isSlotPending(AppealSlot slot, String currentProcessor) {
        if (StringUtils.isEmpty(slot.processor()) || StringUtils.isEmpty(currentProcessor)) {
            return false;
        }
        Set<String> processing = Arrays.stream(currentProcessor.split(Constants.COMMA))
            .map(String::trim).filter(StringUtils::isNotEmpty).collect(Collectors.toSet());
        return Arrays.stream(slot.processor().split(Constants.COMMA))
            .map(String::trim).anyMatch(processing::contains);
    }

    /**
     * 申诉处理器模板：状态判定 + 完成/待复核 + 完成时间与责任人清空 + 日志
     *
     * @param ebgSampTaskTodoLogBo ebgSampTaskTodoLogBo
     * @param ebgSampTaskTodoLogBoList ebgSampTaskTodoLogBoList
     * @param curRegion curRegion
     * @param indicatorList indicatorList
     * @param userAccount userAccount
     * @return int
     */
    private <T, S> int doAppealProcess(
            EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
            List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList,
            String userAccount,
            String curRegion,
            List<EbgSampTaskIndicatorResultBo> indicatorList,
            AppealProcessSupport<T, S> c) {
        Integer beforeStatus = c.getCheckStatus().apply(c.vo());
        c.setProcessor().accept(c.save(), userAccount);
        if (!checkHasUnconfirmed(indicatorList)) {
            if (checkAllUnappealed(indicatorList)) {
                c.setCheckStatus().accept(c.save(), EbgSampCheckStatusEnum.FINISH.getStatus());
                c.setCurrentProcessor().accept(c.save(), "");
            } else {
                c.setCheckStatus().accept(c.save(), EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus());
                c.setCurrentProcessor().accept(c.save(), mergeProcessors(c.getFinalReviewProcessor().apply(c.vo())));
            }
        } else {
            c.setCurrentProcessor().accept(c.save(), mergeProcessors(c.getRemainingProcessor().apply(c.vo())));
        }
        c.setFinishTime().accept(c.save(), new Date());
        int result = c.update().apply(c.save());
        EbgSampTaskTodoLogBo appealLog = new EbgSampTaskTodoLogBo();
        BeanUtils.copyProperties(ebgSampTaskTodoLogBo, appealLog);
        appealLog.setOperateType(c.operateType().getCode());
        String remarkStr = c.getRemark().apply(c.vo());
        if (remarkStr == null) {
            remarkStr = "";
        }
        String remarkLabel = c.operateType().name().split("_")[0];
        String content = "操作内容：" + c.operateType().getType() + "；" + remarkLabel + "申诉备注：" + remarkStr;
        Integer newStatus = c.getSaveCheckStatus().apply(c.save());
        if (newStatus != null && !newStatus.equals(beforeStatus)) {
            content = content + "；状态改为" + EbgSampCheckStatusEnum.getDescByStatus(newStatus, curRegion);
        }
        appealLog.setOperateContent(content);
        ebgSampTaskTodoLogBoList.add(appealLog);
        return result;
    }

    /**
     * 采样待办任务质检指标和指标规则分类明细查询
     *
     * @param reqBo 采样任务代办信息
     * @return 质检指标和指标规则分类明细
     */
    @Override
    public List<EbgSampTaskResultVo> getIndicatorQuery(GetEbgSampTaskTodoDetailReqBo reqBo) {
        List<EbgSampTaskCategoryBo> list = ebgSampTaskCategoryResultDao.selectTaskResultById(reqBo);
        List<EbgSampTaskResultVo> resultVoList = new ArrayList<>();
        Map<String, List<EbgSampTaskCategoryBo>> indicatorDataMap = new HashMap<>();

        // 按指标分组
        for (EbgSampTaskCategoryBo item : list) {
            String indicator = item.getIndicator();
            indicatorDataMap.computeIfAbsent(indicator, k -> new ArrayList<>()).add(item);
        }

        // 处理每个指标的数据
        for (Map.Entry<String, List<EbgSampTaskCategoryBo>> entry : indicatorDataMap.entrySet()) {
            // 处理category数据
            List<EbgSampTaskCategoryResultVo> categoryResultVos = processCategoryResult(entry.getValue());
            // 整合该指标的数据到返回列表 resultVoList
            EbgSampTaskResultVo resultVo = buildResultVo(entry.getKey(), entry.getValue(), categoryResultVos);
            resultVoList.add(resultVo);
        }
        return resultVoList;
    }

    private List<EbgSampTaskCategoryResultVo> processCategoryResult(List<EbgSampTaskCategoryBo> categoryList) {
        List<EbgSampTaskCategoryResultVo> categoryResultVos = new ArrayList<>();

        for (EbgSampTaskCategoryBo item : categoryList) {
            if (QualityResultEnum.NOT_PASS.getType().equals(item.getQuantityResult())) {
                EbgSampTaskCategoryResultVo ebgSampTaskCategoryResultVo = buildCategoryResultVo(item);
                List<EbgSampTaskTimeVo> timeVoList = getTimeVoList(item);
                ebgSampTaskCategoryResultVo.setPointInTime(timeVoList);
                categoryResultVos.add(ebgSampTaskCategoryResultVo);
            }
        }
        return categoryResultVos;
    }

    private EbgSampTaskCategoryResultVo buildCategoryResultVo(EbgSampTaskCategoryBo item) {
        EbgSampTaskCategoryResultVo ebgSampTaskCategoryResultVo = new EbgSampTaskCategoryResultVo();
        BeanUtils.copyProperties(item, ebgSampTaskCategoryResultVo);
        ebgSampTaskCategoryResultVo.setEcareRuleCategoryName(CommonUtil.getDictDataNameCn(
            DataConstants.ECARE_INDICATORS, ebgSampTaskCategoryResultVo.getEcareRuleCategory()));
        return ebgSampTaskCategoryResultVo;
    }

    private List<EbgSampTaskTimeVo> getTimeVoList(EbgSampTaskCategoryBo item) {
        List<EbgSampTaskTimeVo> ebgSampTaskTimeVoList = new ArrayList<>();
        String pointInTime = CommonUtil.getDictDataExt(DataConstants.ECARE_INDICATORS, item.getEcareRuleCategory(),
            "pointInTime");
        String busiType = CommonUtil.getDictDataExt(DataConstants.ECARE_INDICATORS, item.getEcareRuleCategory(),
            "busiType");
        String checkType = CommonUtil.getDictDataExt(DataConstants.ECARE_INDICATORS, item.getEcareRuleCategory(),
            "checkType");

        try {
            JSONArray jsonArray = JSON.parseArray(pointInTime);
            if (jsonArray == null || jsonArray.isEmpty()) {
                return Collections.emptyList();
            }
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (jsonObject == null) {
                    continue;
                }
                String key = jsonObject.getString("key");
                String name = jsonObject.getString("name");
                EbgSampTaskTimeVo ebgSampTaskTimeVo = new EbgSampTaskTimeVo();
                ebgSampTaskTimeVo.setName(name);
                processTimeFields(key, ebgSampTaskTimeVo, item, busiType, checkType);
                ebgSampTaskTimeVoList.add(ebgSampTaskTimeVo);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse pointInTime: {}", pointInTime);
            ExceptionPrint.print(e);
        }
        return ebgSampTaskTimeVoList;
    }

    private void processTimeFields(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, EbgSampTaskCategoryBo item,
        String busiType, String checkType) {
        if (EbgBusiTypeEnum.ECARE.getType().equals(busiType)) {
            processEcareTimeFields(key, ebgSampTaskTimeVo, item);
        } else {
            processNonEcareTimeFields(key, ebgSampTaskTimeVo, item, checkType);
        }
    }

    private void processEcareTimeFields(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, EbgSampTaskCategoryBo item) {
        // 获取相关数据
        List<String> srNumList = Collections.singletonList(item.getSrNum());
        List<EbgSrOrderBo> ebgSrOrderBos = srOrderDao.queryQualityInfoBySrNumList(srNumList);
        List<String> srKeyList = ebgSrOrderBos.stream().map(EbgSrOrderBo::getSrKey).collect(Collectors.toList());
        EbgSrOrderBo ebgSrOrderBo = new EbgSrOrderBo();
        if (!ebgSrOrderBos.isEmpty()) {
            ebgSrOrderBo = ebgSrOrderBos.getFirst();
        }
        List<EbgNodeBo> ebgNodeBoList = nodeDao.queryBySrKeyList(srKeyList);
        ebgSrOrderBo.setNodeList(ebgNodeBoList);
        List<EbgAuditBo> ebgAuditBos = ebgAuditDao.queryBySrKeyList(srKeyList);
        List<EbgTaskBo> taskList = taskDao.queryBySrNumList(srNumList);
        List<EbgCallRecordBo> ebgCallRecordBos = ebgCallRecordDao.queryBySrNumList(srNumList);
        GetEbgSampTaskRecordReqVo reqVo = new GetEbgSampTaskRecordReqVo();
        reqVo.setSrNum(item.getSrNum());
        List<EbgCallRecordRespBo> ebgCallRecordRespBoList = getRecordQuery(reqVo, 1, 1000);
        getValue(key, ebgSampTaskTimeVo, ebgSrOrderBo, ebgAuditBos, taskList, ebgCallRecordBos,
            ebgCallRecordRespBoList);
    }

    private static void getValue(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, EbgSrOrderBo ebgSrOrderBo,
        List<EbgAuditBo> ebgAuditBos, List<EbgTaskBo> taskList, List<EbgCallRecordBo> ebgCallRecordBos,
        List<EbgCallRecordRespBo> ebgCallRecordRespBoList) {
        if (EbgTimeFieldEnum.REPORT_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgSrOrderBo.getReportDate());
        } else if (EbgTimeFieldEnum.DISPATCH_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgSrOrderBo.getDispatchTime());
        } else if (EbgTimeFieldEnum.FIRST_CCR_CALLBACK_TIME.getType().equals(key)) {
            getFistCcrCallBackTime(ebgSampTaskTimeVo, ebgCallRecordBos);
        } else if (EbgTimeFieldEnum.TASK_PLANNED_END_DATE.getType().equals(key)) {
            setTaskTimeValue(ebgSampTaskTimeVo, taskList, EbgTaskBo::getTaskPlannedEndDate);
        } else if (EbgTimeFieldEnum.TASK_PLANNED_START_DATE.getType().equals(key)) {
            setTaskTimeValue(ebgSampTaskTimeVo, taskList, EbgTaskBo::getTaskPlannedStartDate);
        } else if (EbgTimeFieldEnum.CALL_DATE_TIME.getType().equals(key)) {
            getCallDateTime(ebgSampTaskTimeVo, ebgCallRecordBos);
        } else if (EbgTimeFieldEnum.CALL_ED_TIME.getType().equals(key)) {
            getCallEdTime(ebgSampTaskTimeVo, ebgCallRecordRespBoList);
        } else if (EbgTimeFieldEnum.RESPOND_ON_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgSrOrderBo.getRespondOnDate());
        } else if (EbgTimeFieldEnum.FIRST_CSE_CALLBACK_TIME.getType().equals(key)) {
            getFistCseCallBackTime(ebgSampTaskTimeVo, ebgCallRecordBos);
        } else if (EbgTimeFieldEnum.LAST_CALL_ED_TIME.getType().equals(key)) {
            if (!ebgCallRecordRespBoList.isEmpty()) {
                ebgSampTaskTimeVo.setValue(ebgCallRecordRespBoList.getFirst().getEdTime());
            }
        } else if (EbgTimeFieldEnum.RESOLVE_ON_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgSrOrderBo.getResolveOnDate());
        } else if (EbgTimeFieldEnum.CANCELLED_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgSrOrderBo.getCancelledTime());
        } else if (EbgTimeFieldEnum.TASK_CREATION_DATE.getType().equals(key)) {
            if (!taskList.isEmpty()) {
                ebgSampTaskTimeVo.setValue(taskList.getFirst().getTaskCreationDate());
            }
        } else if (EbgTimeFieldEnum.TASK_ACTUAL_START_DATE.getType().equals(key)) {
            if (!taskList.isEmpty()) {
                ebgSampTaskTimeVo.setValue(taskList.getFirst().getTaskActualStartDate());
            }
        } else if (EbgTimeFieldEnum.CALL_SATISFY_TRANSFER_TIME.getType().equals(key)) {
            getSatisfyTransferTime(ebgSampTaskTimeVo, ebgCallRecordBos);
        }
    }

    private static void getSatisfyTransferTime(EbgSampTaskTimeVo ebgSampTaskTimeVo,
        List<EbgCallRecordBo> ebgCallRecordBos) {
        if (!ebgCallRecordBos.isEmpty()) {
            for (EbgCallRecordBo ebgCallRecordBo : ebgCallRecordBos) {
                String time = ebgCallRecordBo.getSatisfyTransferTime();
                if (!StringUtils.isEmpty(time)) {
                    ebgSampTaskTimeVo.setValue(time);
                    break;
                }
            }
        }
    }

    private static void getFistCcrCallBackTime(EbgSampTaskTimeVo ebgSampTaskTimeVo,
        List<EbgCallRecordBo> ebgCallRecordBos) {
        if (!ebgCallRecordBos.isEmpty()) {
            for (EbgCallRecordBo ebgCallRecordBo : ebgCallRecordBos) {
                if (EbgConstants.ASS_CCR.equals(ebgCallRecordBo.getAgentRole()) && "1".equals(ebgCallRecordBo
                    .getCallType())) {
                    ebgSampTaskTimeVo.setValue(ebgCallRecordBo.getDateTime());
                    break;
                }
            }
        }
    }

    private static void getFistCseCallBackTime(EbgSampTaskTimeVo ebgSampTaskTimeVo,
        List<EbgCallRecordBo> ebgCallRecordBos) {
        if (!ebgCallRecordBos.isEmpty()) {
            for (EbgCallRecordBo ebgCallRecordBo : ebgCallRecordBos) {
                if (EbgConstants.ASS_CSE.equals(ebgCallRecordBo.getAgentRole()) && "1".equals(ebgCallRecordBo
                    .getCallType())) {
                    ebgSampTaskTimeVo.setValue(ebgCallRecordBo.getDateTime());
                    break;
                }
            }
        }
    }

    private static void getCallDateTime(EbgSampTaskTimeVo ebgSampTaskTimeVo, List<EbgCallRecordBo> ebgCallRecordBos) {
        if (ebgCallRecordBos == null || ebgCallRecordBos.isEmpty()) {
            return;
        }

        for (EbgCallRecordBo ebgCallRecordBo : ebgCallRecordBos) {
            // 1. 获取原始值
            String talkingTimeStr = ebgCallRecordBo.getTalkingTime();

            // 2. 有效性校验：检查是否为 null 或空字符串
            if (talkingTimeStr == null || talkingTimeStr.trim().isEmpty()) {
                continue; // 跳过无效数据
            }
            long seconds = parseTimeToSeconds(talkingTimeStr.trim());

            // 5. 业务逻辑判断
            if (seconds > 15) {
                ebgSampTaskTimeVo.setValue(ebgCallRecordBo.getDateTime());
                break; // 找到第一个符合条件的即退出
            }
        }
    }

    /**
     * 定义一个辅助方法解析时间字符串为秒数
     *
     * @param timeStr timeStr
     * @return long
     */
    public static long parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return 0;
        }
        try {
            // 假设格式为 HH:mm:ss
            String[] parts = timeStr.split(":");
            if (parts.length != 3) {
                return 0; // 格式错误
            }
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 通用方法：查找首个任务，并将结果转为 String 设置到 VO
     *
     * @param extractor 返回 String 的取值函数
     * @param vo vo
     * @param taskList taskList
     */
    private static void setTaskTimeValue(EbgSampTaskTimeVo vo, List<EbgTaskBo> taskList,
        Function<EbgTaskBo, String> extractor) {
        if (taskList == null || taskList.isEmpty())
            return;

        taskList.stream().findFirst().ifPresent(task -> {
            // 提取器保证返回 String，所以这里可以直接赋值
            String value = extractor.apply(task);
            vo.setValue(value);
        });
    }

    private static void getCallEdTime(EbgSampTaskTimeVo ebgSampTaskTimeVo,
                                      List<EbgCallRecordRespBo> ebgCallRecordRespBoList) {
        if (ebgCallRecordRespBoList == null || ebgCallRecordRespBoList.isEmpty()) {
            return;
        }
        // 1. 从系统配置获取关键词
        String configKey = EbgConstants.CALL_ED_TIME_KEYWORD;
        String configValue = SystemConfigSdkUtil.getString(configKey, "");
        // 如果配置为空，使用默认关键词或返回
        if (configValue == null || configValue.trim().isEmpty()) {
            return;
        }
        // 2. 解析关键词列表
        String[] keywords = configValue.split(Constants.COMMA);
        // 去除每个关键词前后的空格，防止配置中有空格导致匹配失败
        List<String> keywordList = Arrays.stream(keywords).map(String::trim).filter(k -> !k.isEmpty()).toList();
        if (keywordList.isEmpty()) {
            return;
        }
        // 3. 遍历通话记录
        for (EbgCallRecordRespBo ebgCallRecordRespBo : ebgCallRecordRespBoList) {
            String text = ebgCallRecordRespBo.getText();
            if (text != null && !text.isEmpty()) {
                // 4. 判断文本是否包含任意一个配置关键词
                boolean containsKeyword = keywordList.stream()
                        .anyMatch(text::contains);

                if (containsKeyword) {
                    // 5. 命中，设置时间并跳出
                    ebgSampTaskTimeVo.setValue(ebgCallRecordRespBo.getEdTime());
                    break;
                }
            }
        }
    }

    private void processNonEcareTimeFields(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, EbgSampTaskCategoryBo item,
        String checkType) {
        // 获取相关数据
        List<String> srNumList = Collections.singletonList(item.getSrNum());
        List<EbgCallRecordBo> ebgCallRecordBos = ebgCallRecordDao.queryBySrNumList(srNumList);
        EbgCallRecordBo ebgCallRecordBo = new EbgCallRecordBo();
        if (!ebgCallRecordBos.isEmpty()) {
            for (EbgCallRecordBo callRecordBo : ebgCallRecordBos) {
                if (EbgConstants.ASS_CSE.equals(callRecordBo.getAgentRole())) {
                    ebgCallRecordBo = callRecordBo;
                    break;
                }
            }
        }

        if (CheckTypeEnum.INCIDENT_ITEM.getType().equals(checkType)) {
            List<NotifySendRecordBo> smsRecordBoList = getNotifySendRecords("SMS", "ANN", "续报%", srNumList);
            List<NotifySendRecordBo> notifySendRecordBoList = getNotifySendRecords("Email", "ANN", "续报%", srNumList);
            List<MajorInfoBo> incidentInfoList = majorIssueDao.getIncidentInfoList(srNumList);
            MajorInfoBo majorInfoBo = new MajorInfoBo();
            if (!incidentInfoList.isEmpty()) {
                majorInfoBo = incidentInfoList.getFirst();
            }
            getIncident(key, ebgSampTaskTimeVo, majorInfoBo, ebgCallRecordBo, notifySendRecordBoList, smsRecordBoList);
        } else if (CheckTypeEnum.CRITICAL_ITEM.getType().equals(checkType)) {
            List<NotifySendRecordBo> smsSendRecordBoList = getNotifySendRecords("SMS", "CRN", "进展通报", srNumList);
            List<NotifySendRecordBo> sendRecordBoList = getNotifySendRecords("Email", "CRN", "进展通报", srNumList);
            List<MajorInfoBo> issueList = majorIssueDao.getMajorIssueList(srNumList);
            MajorInfoBo issueMajorInfoBo = new MajorInfoBo();
            if (!issueList.isEmpty()) {
                issueMajorInfoBo = issueList.getFirst();
            }
            getCritical(key, ebgSampTaskTimeVo, issueMajorInfoBo, ebgCallRecordBo, sendRecordBoList,
                smsSendRecordBoList);
        } else {
            List<NotifySendRecordBo> recordBoList = getNotifySendRecords("Email", "MEN", "续报", srNumList);
            List<MajorInfoBo> upgradeList = majorIssueDao.getManageUpgradeList(srNumList);
            MajorInfoBo upgradeMajorInfoBo = new MajorInfoBo();
            if (!upgradeList.isEmpty()) {
                upgradeMajorInfoBo = upgradeList.getFirst();
            }
            getUpgrade(key, ebgSampTaskTimeVo, upgradeMajorInfoBo, recordBoList);
        }
    }

    private static void getUpgrade(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, MajorInfoBo upgradeMajorInfoBo,
        List<NotifySendRecordBo> recordBoList) {
        if (EbgTimeFieldEnum.ESCA_ITR_TEAM_START_LEVEL_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(upgradeMajorInfoBo.getEscaItrTeamStartLevelDate());
        } else if (EbgTimeFieldEnum.FIRST_EMAIL_CREATION_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(upgradeMajorInfoBo.getFirstEmailCreationDate());
        } else if (EbgTimeFieldEnum.FOLLOW_EMAIL_CREATION_DATE.getType().equals(key)) {
            if (!CollectionUtils.isEmpty(recordBoList)) {
                List<String> dateList = recordBoList.stream().map(NotifySendRecordBo::getCreationDate).toList();
                ebgSampTaskTimeVo.setValue(StringUtils.join(dateList, Constants.COMMA));
            }
        } else if (EbgTimeFieldEnum.ESCA_SOLUTION_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(upgradeMajorInfoBo.getEscaSolutionDate());
        } else if (EbgTimeFieldEnum.ESCA_CLOSED_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(upgradeMajorInfoBo.getEscaClosedDate());
        } else if (EbgTimeFieldEnum.FINAL_EMAIL_CREATION_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(upgradeMajorInfoBo.getFinalEmailCreationDate());
        } else if (EbgTimeFieldEnum.ESCA_OPEN_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(upgradeMajorInfoBo.getEscaOpenDate());
        }
    }

    private static void getCritical(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, MajorInfoBo issueMajorInfoBo,
        EbgCallRecordBo ebgCallRecordBo, List<NotifySendRecordBo> sendRecordBoList,
        List<NotifySendRecordBo> smsSendRecordBoList) {
        if (EbgTimeFieldEnum.CSE_ESCALATE_ON.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getCseEscalateOn());
        } else if (EbgTimeFieldEnum.HANG_UP_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgCallRecordBo.getHangUpTime());
        } else if (EbgTimeFieldEnum.INCIDENT_SEVERITY_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getIncidentSeverityDate());
        } else if (EbgTimeFieldEnum.MAJOR_FIRST_CREATION_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getMajorFirstSmsCreationDate() + "/" + issueMajorInfoBo
                .getMajorFirstEmailCreationDate());
        } else if (EbgTimeFieldEnum.PROGRESS_SMS_CREATION_DATE.getType().equals(key)) {
            if (!CollectionUtils.isEmpty(smsSendRecordBoList)) {
                List<String> dateList = smsSendRecordBoList.stream().map(NotifySendRecordBo::getCreationDate).toList();
                ebgSampTaskTimeVo.setValue(StringUtils.join(dateList, Constants.COMMA));
            }
        } else if (EbgTimeFieldEnum.PROGRESS_EMAIL_CREATION_DATE.getType().equals(key)) {
            if (!CollectionUtils.isEmpty(sendRecordBoList)) {
                List<String> dateList = sendRecordBoList.stream().map(NotifySendRecordBo::getCreationDate).toList();
                ebgSampTaskTimeVo.setValue(StringUtils.join(dateList, Constants.COMMA));
            }
        } else if (EbgTimeFieldEnum.INCIDENT_TIME_LOCAL.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getIncidentTimeLocal());
        } else if (EbgTimeFieldEnum.RESTORED_TIME_LOCAL.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getRestoredTimeLocal());
        } else if (EbgTimeFieldEnum.REPORT_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getReportDate());
        } else if (EbgTimeFieldEnum.RESTORE_START_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(issueMajorInfoBo.getRestoreStartTime());
        }
    }

    private static void getIncident(String key, EbgSampTaskTimeVo ebgSampTaskTimeVo, MajorInfoBo majorInfoBo,
        EbgCallRecordBo ebgCallRecordBo, List<NotifySendRecordBo> notifySendRecordBoList,
        List<NotifySendRecordBo> smsRecordBoList) {
        if (EbgTimeFieldEnum.CSE_ESCALATE_ON.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getCseEscalateOn());
        } else if (EbgTimeFieldEnum.HANG_UP_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(ebgCallRecordBo.getHangUpTime());
        } else if (EbgTimeFieldEnum.REPORT_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getReportTime());
        } else if (EbgTimeFieldEnum.FIRST_CREATION_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getFirstSmsCreationDate() + "/" + majorInfoBo
                .getFirstEmailCreationDate());
        } else if (EbgTimeFieldEnum.FOLLOW_SMS_CREATION_DATE.getType().equals(key)) {
            if (!CollectionUtils.isEmpty(smsRecordBoList)) {
                List<String> dateList = smsRecordBoList.stream().map(NotifySendRecordBo::getCreationDate).toList();
                ebgSampTaskTimeVo.setValue(StringUtils.join(dateList, Constants.COMMA));
            }
        } else if (EbgTimeFieldEnum.FOLLOW_EMAIL_CREATION_DATE.getType().equals(key)) {
            if (!CollectionUtils.isEmpty(notifySendRecordBoList)) {
                List<String> dateList = notifySendRecordBoList.stream().map(NotifySendRecordBo::getCreationDate)
                    .toList();
                ebgSampTaskTimeVo.setValue(StringUtils.join(dateList, Constants.COMMA));
            }
        } else if (EbgTimeFieldEnum.RC_PUBLISH_TILL_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getRcPublishTillTime());
        } else if (EbgTimeFieldEnum.BASE_EMAIL_CREATION_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getBaseEmailCreationDate());
        } else if (EbgTimeFieldEnum.ACCIDENT_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getAccidentTime());
        } else if (EbgTimeFieldEnum.RESTORED_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getRestoredTime());
        } else if (EbgTimeFieldEnum.REPORT_DATE.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getReportDate());
        } else if (EbgTimeFieldEnum.RESTORE_START_TIME.getType().equals(key)) {
            ebgSampTaskTimeVo.setValue(majorInfoBo.getRestoreStartTime());
        }
    }

    private List<NotifySendRecordBo> getNotifySendRecords(String notifyType, String businessModule,
        String notifyPhaseNameCn, List<String> srNumList) {
        NotifySendRecordBo notifySendRecordBo = new NotifySendRecordBo();
        notifySendRecordBo.setNotifyType(notifyType);
        notifySendRecordBo.setBusinessModule(businessModule);
        notifySendRecordBo.setNotifyPhaseNameCn(notifyPhaseNameCn);
        return majorIssueDao.selectNotifySendRecords(notifySendRecordBo, srNumList);
    }

    private EbgSampTaskResultVo buildResultVo(String indicator, List<EbgSampTaskCategoryBo> categoryList,
        List<EbgSampTaskCategoryResultVo> categoryResultVos) {
        EbgSampTaskResultVo ebgSampTaskResultVo = new EbgSampTaskResultVo();
        ebgSampTaskResultVo.setIndicator(indicator);
        ebgSampTaskResultVo.setIndicatorName(CommonUtil.getDictDataNameCn(DataConstants.ECARE_INDICATORS, indicator));
        ebgSampTaskResultVo.setSample(CommonUtil.getDictDataExt(DataConstants.ECARE_INDICATORS, indicator, "sample"));
        EbgSampTaskCategoryBo ebgSampTaskCategoryBo = new EbgSampTaskCategoryBo();
        if (!categoryList.isEmpty()) {
            ebgSampTaskCategoryBo = categoryList.getFirst();
        }
        String result = QualityResultEnum.NO_CHECK.getType().equals(ebgSampTaskCategoryBo.getResult()) ?
                QualityResultEnum.PASS.getType() : ebgSampTaskCategoryBo.getResult();
        ebgSampTaskResultVo.setQuantityResult(result);
        ebgSampTaskResultVo.setCcrAssessor(getAssessor(ebgSampTaskCategoryBo.getCcrAssessor(), ebgSampTaskCategoryBo
            .getTodoCcrAssessor()));
        ebgSampTaskResultVo.setCseAssessor(getAssessor(ebgSampTaskCategoryBo.getCseAssessor(), ebgSampTaskCategoryBo
            .getTodoCseAssessor()));
        ebgSampTaskResultVo.setPseAssessor(getAssessor(ebgSampTaskCategoryBo.getPseAssessor(), ebgSampTaskCategoryBo
            .getTodoPseAssessor()));

        ebgSampTaskResultVo.setAppealReason(ebgSampTaskCategoryBo.getAppealReason());
        ebgSampTaskResultVo.setFirstReviewRemark(ebgSampTaskCategoryBo.getFirstReviewRemark());
        ebgSampTaskResultVo.setFinalReviewRemark(ebgSampTaskCategoryBo.getFinalReviewRemark());
        ebgSampTaskResultVo.setIsAppealed(ebgSampTaskCategoryBo.getIsAppealed());
        ebgSampTaskResultVo.setIsCheck(ebgSampTaskCategoryBo.getIsCheck());
        ebgSampTaskResultVo.setAppealConfirm(ebgSampTaskCategoryBo.getAppealConfirm());
        ebgSampTaskResultVo.setIsCse(ebgSampTaskCategoryBo.getIsCse());
        ebgSampTaskResultVo.setIsCcr(ebgSampTaskCategoryBo.getIsCcr());
        ebgSampTaskResultVo.setIsPse(ebgSampTaskCategoryBo.getIsPse());
        ebgSampTaskResultVo.setCategory(categoryResultVos);
        return ebgSampTaskResultVo;
    }

    private List<String> getAssessor(String current, String todo) {
        List<String> assessorList = new ArrayList<>();
        if (StringUtils.isNotEmpty(current)) {
            String[] parts = current.split(Constants.COMMA);
            assessorList = Arrays.stream(parts).toList();
        }
        else if (StringUtils.isNotEmpty(todo)) {
            String[] parts = todo.split(Constants.COMMA);
            if (parts.length > 0) {
                assessorList.add(parts[0].trim());
            }
        }
        return assessorList;
    }

    /**
     * 采样待办任务附件列表查询
     *
     * @param reqBo 采样任务代办信息
     * @return 附件列表
     */
    @Override
    public List<EbgSampTaskTodoAttachmentVo> getAttachmentQuery(GetEbgSampTaskTodoDetailReqBo reqBo) {
        List<EbgSampTaskTodoAttachmentBo> list = ebgSampTaskTodoAttachmentDao.selectTaskTodoAttachment(reqBo);
        Map<String, String> indicatorMap = CommonUtil.getDictDataNameCnMap(DataConstants.ECARE_INDICATORS);
        // 当前登录人
        String userAccount = UserSessionUtil.getSession().getUserAccount();
        // 当前问题所处环节（checkStatus）
        EbgSampTaskTodoBo todo = ebgSampTaskTodoDao.selectTaskTodoById(reqBo.getTodoId());
        Integer checkStatus = todo != null ? todo.getCheckStatus() : null;
        // 是否模块管理员/系统管理员
        boolean isAdmin = RoleUtil.checkIsSysOrModuleAdminOrAdminOrManager();

        return list.stream().map(item -> {
            UserInfoBo userInfoBo = UserUtil.getUserInfoBoByAccount(item.getUploader());
            EbgSampTaskTodoAttachmentVo vo = new EbgSampTaskTodoAttachmentVo();
            BeanUtils.copyProperties(item, vo);
            vo.setUploaderName(userInfoBo != null ? userInfoBo.getFullName() : "");
            vo.setIndicator(indicatorMap.get(item.getIndicator()));
            vo.setIsDeletable(isDeletable(item, checkStatus, userAccount, isAdmin));
            return vo;
        }).toList();
    }

    /**
     * 判断附件是否可删除
     * 1. 模块管理员/系统管理员可删除
     * 2. 附件的上传环节与当前问题的所处环节一致，且登录人为上传人，也可删除
     *
     * @param attachment 附件信息
     * @param checkStatus 当前问题所处环节
     * @param userAccount 当前登录人
     * @param isAdmin 是否模块管理员/系统管理员
     * @return 0-可以 1-不可以
     */
    private String isDeletable(EbgSampTaskTodoAttachmentBo attachment, Integer checkStatus,
                               String userAccount, boolean isAdmin) {
        if (isAdmin) {
            return "0";
        }
        // 上传环节与当前问题所处环节一致，且登录人为上传人
        if (isStageMatchCurrentStatus(attachment.getUploadStage(), checkStatus)
                && Strings.CS.equals(userAccount, attachment.getUploader())) {
            return "0";
        }
        return "1";
    }

    /**
     * 上传环节（中文）与当前问题所处环节（checkStatus）是否一致
     *
     * @param uploadStage 上传环节（初审/申诉/复审/关闭）
     * @param checkStatus 当前问题所处环节
     * @return 是否一致
     */
    private boolean isStageMatchCurrentStatus(String uploadStage, Integer checkStatus) {
        if (StringUtils.isEmpty(uploadStage) || checkStatus == null) {
            return false;
        }
        if (EbgConstants.FIRST_REVIEW_NAME.equals(uploadStage)) {
            return EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(checkStatus);
        }
        if (EbgConstants.APPEAL_NAME.equals(uploadStage)) {
            return EbgSampCheckStatusEnum.PENDING_APPEAL.getStatus().equals(checkStatus);
        }
        if (EbgConstants.FINAL_REVIEW_NAME.equals(uploadStage)) {
            return EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus().equals(checkStatus);
        }
        if (EbgConstants.CLOSE_NAME.equals(uploadStage)) {
            return EbgSampCheckStatusEnum.FINISH.getStatus().equals(checkStatus);
        }
        return false;
    }

    /**
     * 采样待办任务附件删除
     * 权限校验：模块管理员/系统管理员可删除；或附件的上传环节与当前问题的所处环节一致，且登录人为上传人
     *
     * @param reqBo 采样任务附件删除信息
     * @return 结果
     */
    @Override
    public int deleteAttachment(DeleteEbgSampTaskTodoAttachmentReqBo reqBo) {
        // 1. 查询附件
        EbgSampTaskTodoAttachmentBo fileEntity = ebgSampTaskTodoAttachmentDao.selectById(reqBo.getFileId());
        if (fileEntity == null) {
            LOGGER.error("EbgSampTaskTodoServiceImpl deleteAttachment file not exist. fileId: {}", reqBo.getFileId());
            throw new BaseException(QuaErrorCodeEnum.FILE_ID_NOT_EXIST.getErrorCode());
        }
        // 2. 校验附件归属待办与入参一致
        if (!Objects.equals(fileEntity.getTodoId(), reqBo.getTodoId())) {
            LOGGER.error("EbgSampTaskTodoServiceImpl deleteAttachment todoId not match. fileId: {}, " +
                    "attachmentTodoId: {},"
                    + " reqTodoId: {}", reqBo.getFileId(), fileEntity.getTodoId(), reqBo.getTodoId());
            throw new BaseException(QuaErrorCodeEnum.PARAM_ERROR.getErrorCode());
        }
        // 3. 已删除校验
        if (Integer.valueOf(EbgConstants.FILE_DELETED_STATUS).equals(fileEntity.getFileStatus())) {
            LOGGER.error("EbgSampTaskTodoServiceImpl deleteAttachment file already deleted. fileId: {}",
                    reqBo.getFileId());
            throw new BaseException(QuaErrorCodeEnum.FILE_DELETED.getErrorCode());
        }
        // 4. 权限校验（与附件列表查询 isDeletable 逻辑一致）
        String userAccount = UserSessionUtil.getSession().getUserAccount();
        boolean isAdmin = RoleUtil.checkIsSysOrModuleAdminOrAdminOrManager();
        EbgSampTaskTodoBo todo = ebgSampTaskTodoDao.selectTaskTodoById(reqBo.getTodoId());
        Integer checkStatus = todo != null ? todo.getCheckStatus() : null;
        if (!"0".equals(isDeletable(fileEntity, checkStatus, userAccount, isAdmin))) {
            LOGGER.error("EbgSampTaskTodoServiceImpl deleteAttachment permission denied. user: {}, uploader: {},"
                    + " fileId: {}", userAccount, fileEntity.getUploader(), reqBo.getFileId());
            throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
        }
        // 5. 逻辑删除：文件状态置为已删除
        EbgSampTaskTodoAttachmentBo attachment = new EbgSampTaskTodoAttachmentBo();
        attachment.setFileId(reqBo.getFileId());
        attachment.setFileStatus(EbgConstants.FILE_DELETED_STATUS);
        ebgSampTaskTodoAttachmentDao.updateTaskTodoAttachment(attachment);
        // 6. 删除edm存储的信息（远程调用，失败仅记录日志，不影响本地逻辑删除结果）
        deleteEdmDoc(fileEntity.getEdmDocId());
        return 1;
    }

    /**
     * 删除EDM平台存储的文件
     * 远程调用，失败时仅记录日志，不抛出异常，避免影响本地逻辑删除的提交结果
     *
     * @param edmDocId EDM平台文档ID
     */
    private void deleteEdmDoc(String edmDocId) {
        if (StringUtils.isBlank(edmDocId)) {
            LOGGER.warn("EbgSampTaskTodoServiceImpl deleteEdmDoc edmDocId is blank, skip edm delete");
            return;
        }
        try {
            edmClientUtil.initIam();
            edmClientUtil.deleteDoc(edmDocId, "system");
        } catch (Exception e) {
            ExceptionPrint.print(e);
            LOGGER.error("EbgSampTaskTodoServiceImpl deleteEdmDoc failed, edmDocId: {}", edmDocId);
        }
    }

    /**
     * 采样待办任务附件下载
     *
     * @param reqBo 采样任务附件信息
     * @param response response
     */
    @Override
    public void download(GetEbgSampTaskTodoAttachmentReqBo reqBo, HttpServletResponse response) {
        // 1. 基础参数与上下文获取
        String userAccount = UserSessionUtil.getSession().getUserAccount();
        // 2. 数据库查询
        EbgSampTaskTodoAttachmentBo fileEntity = ebgSampTaskTodoAttachmentDao.selectById(reqBo.getFileId());

        // [合并校验] 权限检查
        // 逻辑：如果是上传者本人，或者具有管理员/处理人/系统管理员角色，则允许下载
        boolean isAdmin = RoleUtil.checkIsSysOrModuleAdminOrAdminOrManager();

        // 如果当前用户不是该文件的上传者也不是管理员也不是流程处理人
        if (!isAuthorizedUser(userAccount, fileEntity, isAdmin)) {
            LOGGER.error("EbgSampTaskTodoServiceImpl checkPermission failed. user: {}, uploader: {}, fileId: {}",
                userAccount, fileEntity.getUploader(), fileEntity.getFileId());
            throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
        }
        String fileDownloadVoStr = MessageUtil.filterLogMsg(fileEntity);
        FileEntity file = new FileEntity();
        BeanUtils.copyProperties(fileEntity, file);
        file.setOriginFileName(fileEntity.getFileName());
        fileService.checkAndFileDown(fileEntity.getFileId(), response, file, fileDownloadVoStr);
    }

    /**
     * 采样待办任务附件批量下载
     *
     * @param reqBo 采样任务待办信息
     * @param response response
     */
    @Override
    public void batchDownload(GetEbgSampTaskTodoBatchReqBo reqBo, HttpServletResponse response) {
        String userAccount = UserSessionUtil.getSession().getUserAccount();
        Long todoId = reqBo.getTodoId();
        GetEbgSampTaskTodoDetailReqBo detailReqBo = new GetEbgSampTaskTodoDetailReqBo();
        BeanUtils.copyProperties(reqBo, detailReqBo);
        List<EbgSampTaskTodoAttachmentBo> attachmentList = ebgSampTaskTodoAttachmentDao.selectTaskTodoAttachment(
            detailReqBo);
        if (CollectionUtils.isEmpty(attachmentList)) {
            LOGGER.warn("EbgSampTaskTodoServiceImpl batchDownload no attachments found for todoId: {}", todoId);
            throw new BaseException(QuaErrorCodeEnum.FILE_ID_NOT_EXIST.getErrorCode());
        }
        boolean isAdmin = RoleUtil.checkIsSysOrModuleAdminOrAdminOrManager();
        // 遍历所有附件，确保当前用户是每一个文件的上传者
        for (EbgSampTaskTodoAttachmentBo attachment : attachmentList) {
            // 如果当前用户不是该文件的上传者也不是管理员也不是流程处理人
            if (!isAuthorizedUser(userAccount, attachment, isAdmin)) {
                LOGGER.error("EbgSampTaskTodoServiceImpl batchDownload permission denied. user: {}, uploader: {},"
                    + " todoId: {}, file: {}", userAccount, attachment.getUploader(), todoId, attachment.getFileId());
                throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
            }
        }
        // 流式传输：ZipOutputStream 直接包装 response.getOutputStream
        // 这样数据是边生成边写入客户端，无需将所有文件加载到内存中的 tempOut
        try (OutputStream outputStream = response.getOutputStream(); ZipOutputStream zipOut = new ZipOutputStream(
            outputStream)) {
            // 设置响应头
            response.reset();
            response.setCharacterEncoding("utf-8");
            String zipFileName = URLEncoder.encode("samp-todo-attachments.zip", StandardCharsets.UTF_8).replace("+",
                "%20");
            response.addHeader("Content-Disposition", "attachment; filename=" + zipFileName);
            response.setContentType(FileConstants.FILE_RESPONSE_CONTENT_TYPE);
            edmClientUtil.initIam();
            getZip(attachmentList, zipOut, userAccount);
            zipOut.finish();
        } catch (EdmException | IOException e) {
            ExceptionPrint.print(e);
            LOGGER.error("EbgSampTaskTodoServiceImpl batchDownload exception for todoId: {}", todoId);
            response.reset();
            throw new BaseException(QuaErrorCodeEnum.FILE_DOWNLOAD_FAIL.getErrorCode());
        }
    }
```
