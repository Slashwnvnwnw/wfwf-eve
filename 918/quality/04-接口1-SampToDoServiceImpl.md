# 04-接口1-SampToDoServiceImpl

> 本文件包含以下源码（按包结构整理）

---

```
=== service/src/main/java/com/huawei/qua/ebg/service/impl/SampToDoServiceImpl.java ===
```

```java
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.qua.ebg.service.impl;

import com.huawei.dispatchcenter.amsservice.common.enums.CommonErrorCodeEnum;
import com.huawei.dispatchcenter.amsservice.common.exception.BaseException;
import com.huawei.dispatchcenter.amsservice.common.exception.ExceptionPrint;
import com.huawei.dispatchcenter.amsservice.common.log.MessageUtil;
import com.huawei.dispatchcenter.amsservice.permission.bo.DictTreeBo;
import com.huawei.dispatchcenter.amsservice.permission.bo.UserInfoBo;
import com.huawei.dispatchcenter.amsservice.permission.service.DataDimensionSdkService;
import com.huawei.dispatchcenter.amsservice.session.UserSessionUtil;
import com.huawei.dispatchcenter.amsservice.utils.SpringContextUtils;
import com.huawei.dispatchcenter.amsservice.utils.StringUtil;
import com.huawei.qua.bo.inspection.FileBo;
import com.huawei.qua.bo.rule.RuleInfoTodoResBo;
import com.huawei.qua.bo.rule.SampToDoTaskRuleGetReqBo;
import com.huawei.qua.common.constants.Constants;
import com.huawei.qua.common.constants.DataConstants;
import com.huawei.qua.common.enums.ebg.BusinessTypeEnum;
import com.huawei.qua.common.enums.inspection.QualityResultEnum;
import com.huawei.qua.common.util.CommonUtil;
import com.huawei.qua.common.util.DataDictUtil;
import com.huawei.qua.common.util.RoleUtil;
import com.huawei.qua.common.util.UserUtil;
import com.huawei.qua.dao.RuleInfoDao;
import com.huawei.qua.ebg.bo.samp.*;
import com.huawei.qua.ebg.bo.task.SampTodoLogBo;
import com.huawei.qua.ebg.bo.task.TodoRuleTaskResultReqBo;
import com.huawei.qua.ebg.bo.task.TodoTaskQuaIndicatorUpdateBo;
import com.huawei.qua.ebg.common.EbgConstants;
import com.huawei.qua.ebg.common.enums.EbgBusiTypeEnum;
import com.huawei.qua.ebg.common.enums.EbgSampCheckStatusEnum;
import com.huawei.qua.ebg.common.utils.AccountUtil;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskBaseDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskCategoryResultDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskIndicatorResultDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskTodoAttachmentDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskTodoDao;
import com.huawei.qua.ebg.dao.samp.EbgSampTaskTodoLogDao;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskBaseVo;
import com.huawei.qua.ebg.outer.vo.task.EbgSampTaskTodoVo;
import com.huawei.qua.ebg.service.EbgSrQualityCfgService;
import com.huawei.qua.ebg.service.SampToDoService;
import com.huawei.qua.exception.QuaErrorCodeEnum;
import com.huawei.qua.service.FileService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 采样待办任务规则信息ServiceImpl
 *
 * @author dWX1508530
 * @since 2026-06-03
 */
@Service
public class SampToDoServiceImpl implements SampToDoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampToDoServiceImpl.class);

    @Autowired
    private RuleInfoDao ruleInfoDao;

    @Autowired
    private EbgSampTaskCategoryResultDao ebgSampTaskCategoryResultDao;

    @Autowired
    private EbgSampTaskTodoDao sampTaskTodoDao;  // 问题单采样待办dao

    @Autowired
    private EbgSampTaskBaseDao sampTaskTodoMajorDao; // 重大问题采样待办dao

    @Autowired
    private EbgSampTaskIndicatorResultDao indicatorResultDao;

    @Autowired
    private EbgSampTaskTodoAttachmentDao sampTaskTodoAttachmentDao;

    @Autowired
    private EbgSampTaskTodoLogDao sampTaskTodoLogDao;

    @Autowired
    private AccountUtil accountUtil;

    @Autowired
    private EbgSrQualityCfgService ebgSrQualityCfgService;

    @Autowired
    private FileService fileService;

    @Autowired
    private EbgSampTaskTodoDao ebgSampTaskTodoDao;

    @Autowired
    private EbgSampTaskBaseDao ebgSampTaskBaseDao;

    /**
     * 新增 采样待办任务日志表
     *
     * @param sampTodoLogBo 对象
     */
    @Override
    public void insertSampTodoLog(SampTodoLogBo sampTodoLogBo) {
        sampTodoLogBo.setOperator(UserUtil.getCurrentAccount());
        ebgSampTaskCategoryResultDao.insertSampTodoLog(sampTodoLogBo);
    }

    /**
     * 上传采样待办任务文件
     *
     * @param file 任务文件
     * @return 文件id
     */
    @Override
    public Long uploadTodoFile(MultipartFile file) {
        FileBo bo = fileService.fileUpload(file);
        ebgSampTaskCategoryResultDao.insertTodoAttach(bo);
        return bo.getFileId();
    }

    /**
     * 采样待办任务规则信息查询接口
     *
     * @param reqBo 请求对象
     * @return 结果
     */
    @Override
    public List<RuleInfoTodoResBo> getRuleInfoTodoResList(SampToDoTaskRuleGetReqBo reqBo) {
        return ruleInfoDao.getRuleListToDo(reqBo);
    }

    /**
     * 采样待办任务指标规则结果新增接口
     *
     * @param reqBo 请求对象
     * @return id
     */
    @Override
    public Long addToDoTaskRuleResult(TodoRuleTaskResultReqBo reqBo) {
        check(reqBo);
        reqBo.setCreateUser(UserUtil.getCurrentAccount());
        ebgSampTaskCategoryResultDao.insertTodoRuleTaskResult(reqBo);
        SampTodoLogBo sampTodoLogBo = new SampTodoLogBo();
        sampTodoLogBo.setOperateType("新增指标");
        Map<String, String> indicatorMap =
                DataDictUtil.getCodeKeyCnValueMap(EbgConstants.ECARE_INDICATORS_KEY, "0", null);
        sampTodoLogBo
                .setOperateContent("新增指标(" + indicatorMap.get(reqBo.getIndicator()) + "：" + reqBo.getRuleName() + ")");
        sampTodoLogBo.setBusiType(reqBo.getBusiType());
        sampTodoLogBo.setCheckType(reqBo.getCheckType());
        sampTodoLogBo.setIndicator(reqBo.getIndicator());
        sampTodoLogBo.setTodoId(reqBo.getTodoId());
        insertSampTodoLog(sampTodoLogBo);
        // 是否是重大问题的待办
        boolean isMajor = BusinessTypeEnum.ECare_Major.getCode().equals(reqBo.getBusiType());
        EbgSampTaskTodoBo ebgSampTaskTodoBo = getEbgSampTaskTodoBo(reqBo.getTodoId(), isMajor);
        updateQuantityResult(reqBo.getBusiType(), reqBo.getCheckType(), reqBo.getTodoId(),
                reqBo.getIndicator(), ebgSampTaskTodoBo.getCheckStatus(), indicatorMap);
        updateSrRuleResult(reqBo.getBusiType(), reqBo.getCheckType(), reqBo.getTodoId(), ebgSampTaskTodoBo, isMajor);
        return reqBo.getId();
    }

    private EbgSampTaskTodoBo getEbgSampTaskTodoBo(Long todoId, boolean isMajor) {
        // 1. 根据检查类型 来 获取待办任务信息
        EbgSampTaskTodoBo ebgSampTaskTodoBo = new EbgSampTaskTodoBo();
        if (!isMajor) {
            ebgSampTaskTodoBo = sampTaskTodoDao.selectTaskTodoById(todoId);
        } else {
            EbgSampTaskBaseBo ebgSampTaskBaseBo = sampTaskTodoMajorDao
                    .selectTaskBaseById(todoId);
            BeanUtils.copyProperties(ebgSampTaskBaseBo, ebgSampTaskTodoBo);
        }
        return ebgSampTaskTodoBo;
    }

    private void check(TodoRuleTaskResultReqBo reqBo) {
        List<DictTreeBo> indicatorList =
            SpringContextUtils.getBean(DataDimensionSdkService.class).getDataTree(DataConstants.ECARE_INDICATORS, true);
        String ecareRuleCategoryCode = null;
        for (DictTreeBo dictTreeBo : indicatorList) {
            if (!dictTreeBo.getCode().equals(reqBo.getIndicator())) {
                continue;
            }

            if (CollectionUtils.isEmpty(dictTreeBo.getChildren())) {
                continue;
            }
            for (DictTreeBo childDict : dictTreeBo.getChildren()) {
                if (childDict.getDictTreeNameCn().equals(reqBo.getEcareRuleCategory())) {
                    ecareRuleCategoryCode = childDict.getCode();
                    break;
                }
            }
            if (StringUtils.isNotBlank(ecareRuleCategoryCode)) {
                break;
            }
        }
        if (StringUtil.isEmpty(ecareRuleCategoryCode)) {
            throw new BaseException(CommonErrorCodeEnum.VALID_NUMBER.getErrorCode(), new String[]{"ecareRuleCategory"});
        }
        checkAuth(reqBo.getBusiType(), reqBo.getTodoId());

        reqBo.setEcareRuleCategory(ecareRuleCategoryCode);
        SampToDoTaskRuleGetReqBo sampToDoTaskRuleGetReqBo = new SampToDoTaskRuleGetReqBo();
        sampToDoTaskRuleGetReqBo.setBusiType(reqBo.getBusiType());
        sampToDoTaskRuleGetReqBo.setCheckType(reqBo.getCheckType());
        sampToDoTaskRuleGetReqBo.setRuleName(reqBo.getRuleName());
        sampToDoTaskRuleGetReqBo.setEcareRuleCategory(ecareRuleCategoryCode);
        List<RuleInfoTodoResBo> ruleInfoTodoResBos = ruleInfoDao.getRuleListToDo(sampToDoTaskRuleGetReqBo);
        if (CollectionUtils.isEmpty(ruleInfoTodoResBos)) {
            throw new BaseException(QuaErrorCodeEnum.RULE_NAME_AND_CATEGORY_NOT_MATCH.getErrorCode());
        }
        // 一个指标梳理不能超过20个
        TodoRuleTaskResultReqBo categoryQuery = new TodoRuleTaskResultReqBo();
        categoryQuery.setBusiType(reqBo.getBusiType());
        categoryQuery.setCheckType(reqBo.getCheckType());
        categoryQuery.setIndicator(reqBo.getIndicator());
        categoryQuery.setTodoId(reqBo.getTodoId());
        List<EbgSampTaskCategoryResultBo> categoryList =
            ebgSampTaskCategoryResultDao.selectTaskResultList(categoryQuery);
        if (categoryList != null && categoryList.size() >= 20) {
            throw new BaseException(QuaErrorCodeEnum.SAME_INDICATOR_CANNOT_EXCEED.getErrorCode());
        }
    }

    private void checkAuth(String busiType, Long todoId) {
        boolean isEdit = false;
        if (EbgBusiTypeEnum.ECARE.getType().equals(busiType)) {
            // 问题单 QA初审状态初审责任人有权限编辑，QA复审，复审责任人有编辑，关闭状态模块管理员有权限编辑
            EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(todoId);
            if (ebgSampTaskTodoVo == null) {
                throw new BaseException(QuaErrorCodeEnum.DATA_IS_NOT_EXIST.getErrorCode(), new String[] {todoId + ""});
            }
            if (EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(ebgSampTaskTodoVo.getCheckStatus())) {
                if (UserUtil.getCurrentAccount().equals(ebgSampTaskTodoVo.getFirstReviewProcessor())) {
                    isEdit = true;
                }
            } else if (EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus()
                .equals(ebgSampTaskTodoVo.getCheckStatus())) {
                if (UserUtil.getCurrentAccount().equals(ebgSampTaskTodoVo.getFinalReviewProcessor())) {
                    isEdit = true;
                }
            } else if (EbgSampCheckStatusEnum.FINISH.getStatus().equals(ebgSampTaskTodoVo.getCheckStatus())) {
                if (RoleUtil.isSysAdmin()) {
                    isEdit = true;
                }
            }
        } else {
            // 重大问题 QA初审状态初审责任人有权限编辑，QA复审，复审责任人有编辑，关闭状态模块管理员有权限编辑
            EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(todoId);
            if (ebgSampTaskBaseVo == null) {
                throw new BaseException(QuaErrorCodeEnum.DATA_IS_NOT_EXIST.getErrorCode(), new String[] {todoId + ""});
            }
            if (EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(ebgSampTaskBaseVo.getCheckStatus())) {
                if (UserUtil.getCurrentAccount().equals(ebgSampTaskBaseVo.getFirstReviewProcessor())) {
                    isEdit = true;
                }
            } else if (EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus()
                .equals(ebgSampTaskBaseVo.getCheckStatus())) {
                if (UserUtil.getCurrentAccount().equals(ebgSampTaskBaseVo.getFinalReviewProcessor())) {
                    isEdit = true;
                }
            } else if (EbgSampCheckStatusEnum.FINISH.getStatus().equals(ebgSampTaskBaseVo.getCheckStatus())) {
                if (RoleUtil.isSysAdmin()) {
                    isEdit = true;
                }
            }
        }
        if (!isEdit && !RoleUtil.isSysOrModuleAdmin(Objects
                .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
    }

    /**
     * 采样待办任务指标规则结果编辑接口
     *
     * @param reqBo 请求对象
     */
    @Override
    public void editToDoTaskRuleResult(TodoRuleTaskResultReqBo reqBo) {
        EbgSampTaskCategoryResultBo sampTaskCategoryResultBo =
            ebgSampTaskCategoryResultDao.getTaskResultById(reqBo.getId());
        if (sampTaskCategoryResultBo == null) {
            throw new BaseException(QuaErrorCodeEnum.DATA_IS_NOT_EXIST.getErrorCode(),
                new String[] {reqBo.getId() + ""});
        }

        checkAuth(sampTaskCategoryResultBo.getBusiType(), sampTaskCategoryResultBo.getTodoId());

        ebgSampTaskCategoryResultDao.updateTodoRuleTaskResult(reqBo);
        SampTodoLogBo sampTodoLogBo = new SampTodoLogBo();
        sampTodoLogBo.setOperateType("更新指标");
        Map<String, String> indicatorMap =
            DataDictUtil.getCodeKeyCnValueMap(EbgConstants.ECARE_INDICATORS_KEY, "0", null);
        String ruleName = sampTaskCategoryResultBo.getRuleName();
        if (StringUtils.isBlank(ruleName)) {
            ruleName = "";
        }
        sampTodoLogBo.setOperateContent(
            "修改指标(" + indicatorMap.get(sampTaskCategoryResultBo.getIndicator()) + "：" + ruleName + ")");
        sampTodoLogBo.setBusiType(sampTaskCategoryResultBo.getBusiType());
        sampTodoLogBo.setCheckType(sampTaskCategoryResultBo.getCheckType());
        sampTodoLogBo.setIndicator(sampTaskCategoryResultBo.getIndicator());
        sampTodoLogBo.setTodoId(sampTaskCategoryResultBo.getTodoId());
        insertSampTodoLog(sampTodoLogBo);
        // 是否是重大问题的待办
        boolean isMajor = BusinessTypeEnum.ECare_Major.getCode().equals(sampTaskCategoryResultBo.getBusiType());
        EbgSampTaskTodoBo ebgSampTaskTodoBo = getEbgSampTaskTodoBo(sampTaskCategoryResultBo.getTodoId(), isMajor);
        updateQuantityResult(sampTaskCategoryResultBo.getBusiType(), sampTaskCategoryResultBo.getCheckType(),
                sampTaskCategoryResultBo.getTodoId(), sampTaskCategoryResultBo.getIndicator(),
                ebgSampTaskTodoBo.getCheckStatus(), indicatorMap);
        updateSrRuleResult(sampTaskCategoryResultBo.getBusiType(), sampTaskCategoryResultBo.getCheckType(),
                sampTaskCategoryResultBo.getTodoId(), ebgSampTaskTodoBo, isMajor);
    }

    /**
     * 采样待办任务指标规则结果删除接口
     *
     * @param id id主键
     * @return 删除条数
     */
    @Override
    public int deleteTodoTaskRuleResult(Long id) {
        EbgSampTaskCategoryResultBo sampTaskCategoryResultBo = ebgSampTaskCategoryResultDao.getTaskResultById(id);
        if (sampTaskCategoryResultBo == null) {
            throw new BaseException(QuaErrorCodeEnum.DATA_IS_NOT_EXIST.getErrorCode(), new String[] {id + ""});
        }
        checkAuth(sampTaskCategoryResultBo.getBusiType(), sampTaskCategoryResultBo.getTodoId());

        int result = ebgSampTaskCategoryResultDao.deleteTodoTaskRuleResult(id);
        SampTodoLogBo sampTodoLogBo = new SampTodoLogBo();
        Map<String, String> indicatorMap =
            DataDictUtil.getCodeKeyCnValueMap(EbgConstants.ECARE_INDICATORS_KEY, "0", null);
        String ruleName = sampTaskCategoryResultBo.getRuleName();
        if (StringUtils.isBlank(ruleName)) {
            ruleName = "";
        }
        sampTodoLogBo.setOperateType("删除指标");
        sampTodoLogBo.setOperateContent(
            "删除指标(" + indicatorMap.get(sampTaskCategoryResultBo.getIndicator()) + "：" + ruleName + ")");
        sampTodoLogBo.setBusiType(sampTaskCategoryResultBo.getBusiType());
        sampTodoLogBo.setCheckType(sampTaskCategoryResultBo.getCheckType());
        sampTodoLogBo.setIndicator(sampTaskCategoryResultBo.getIndicator());
        sampTodoLogBo.setTodoId(sampTaskCategoryResultBo.getTodoId());
        insertSampTodoLog(sampTodoLogBo);
        // 是否是重大问题的待办
        boolean isMajor = BusinessTypeEnum.ECare_Major.getCode().equals(sampTaskCategoryResultBo.getBusiType());
        EbgSampTaskTodoBo ebgSampTaskTodoBo = getEbgSampTaskTodoBo(sampTaskCategoryResultBo.getTodoId(), isMajor);
        updateQuantityResult(sampTaskCategoryResultBo.getBusiType(), sampTaskCategoryResultBo.getCheckType(),
                sampTaskCategoryResultBo.getTodoId(), sampTaskCategoryResultBo.getIndicator(),
                ebgSampTaskTodoBo.getCheckStatus(), indicatorMap);
        updateSrRuleResult(sampTaskCategoryResultBo.getBusiType(), sampTaskCategoryResultBo.getCheckType(),
                sampTaskCategoryResultBo.getTodoId(), ebgSampTaskTodoBo, isMajor);
        return result;
    }

    private void updateSrRuleResult(String busiType, String checkType, Long todoId,
                                    EbgSampTaskTodoBo ebgSampTaskTodoBo, Boolean isMajor) {
        TodoTaskQuaIndicatorUpdateBo req = new TodoTaskQuaIndicatorUpdateBo();
        req.setBusiType(busiType);
        req.setCheckType(checkType);
        req.setTodoId(todoId);
        // 3. 获取最新的全量指标结果
        List<EbgSampTaskIndicatorResultBo> indicatorList = getIndicatorResultList(req);
        boolean passFlag = true;
        if (CollectionUtils.isNotEmpty(indicatorList)) {
            for (EbgSampTaskIndicatorResultBo item : indicatorList) {
                // 判断是否有不通过项
                if (QualityResultEnum.NOT_PASS.getType().equals(item.getQuantityResult())) {
                    passFlag = false;
                    break;
                }
            }
        }
        // 8. 设置最终质检结果
        ebgSampTaskTodoBo.setSrRuleResult(passFlag ? QualityResultEnum.PASS.getType()
                : QualityResultEnum.NOT_PASS.getType());
        updateToDoInfo(ebgSampTaskTodoBo, isMajor);
    }

    private void updateQuantityResult(String busiType, String checkType, Long todoId, String indicator,
                                      Integer checkStatus, Map<String, String> indicatorMap) {
        EbgSampTaskIndicatorResultBo resultBo = new EbgSampTaskIndicatorResultBo();
        resultBo.setTodoId(todoId);
        resultBo.setBusiType(busiType);
        resultBo.setCheckType(checkType);
        resultBo.setIndicator(indicator);
        TodoRuleTaskResultReqBo req = new TodoRuleTaskResultReqBo();
        req.setBusiType(busiType);
        req.setCheckType(checkType);
        req.setTodoId(todoId);
        req.setIndicator(indicator);
        // 3. 获取最新的全量指标结果
        List<EbgSampTaskCategoryResultBo> indicatorList = getCategoryResultList(req);
        boolean passFlag = true;
        if (CollectionUtils.isNotEmpty(indicatorList)) {
            for (EbgSampTaskCategoryResultBo item : indicatorList) {
                // 判断是否有不通过项
                if (QualityResultEnum.NOT_PASS.getType().equals(item.getQuantityResult())) {
                    passFlag = false;
                    break;
                }
            }
        }
        // 8. 设置最终质检结果
        String result = passFlag ? QualityResultEnum.PASS.getType() : QualityResultEnum.NOT_PASS.getType();
        resultBo.setQuantityResult(result);
        indicatorResultDao.updateIndicatorResult(resultBo);
        // 5. 记录操作日志
        String currentLoginUser = UserSessionUtil.getSession().getUserAccount();
        IndicatorHistoryBo indicatorHistoryBo = new IndicatorHistoryBo();
        TodoTaskQuaIndicatorUpdateBo updateBo = new TodoTaskQuaIndicatorUpdateBo();
        updateBo.setTodoId(todoId);
        updateBo.setBusiType(busiType);
        updateBo.setCheckType(checkType);
        updateBo.setIndicator(indicator);
        String operationType = getOperationType(checkStatus);
        updateBo.setOperationType(operationType);
        updateBo.setCheckResult(result);
        recordOperationLog(updateBo, currentLoginUser, indicatorMap, indicatorHistoryBo);
    }

    @NotNull
    private static String getOperationType(Integer checkStatus) {
        String operationType;
        if (EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(checkStatus)) {
            operationType = EbgConstants.FIRST_REVIEW_INDICATOR_EDIT;
        } else if (EbgSampCheckStatusEnum.PENDING_APPEAL.getStatus().equals(checkStatus)) {
            operationType = EbgConstants.APPEAL_INDICATOR_EDIT;
        } else if (EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus().equals(checkStatus)) {
            operationType = EbgConstants.FINAL_REVIEW_INDICATOR_EDIT;
        } else {
            operationType = EbgConstants.CLOSE_INDICATOR_EDIT;
        }
        return operationType;
    }

    /**
     * 采样待办任务--质检指标--编辑接口
     *
     * @param req 更新入参对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTodoTaskQuaIndicator(TodoTaskQuaIndicatorUpdateBo req) {
        Map<String, String> indicatorMap = DataDictUtil.getCodeKeyCnValueMap(EbgConstants.ECARE_INDICATORS_KEY,
                "0", null);
        // 设置指标责任人和指标类型
        setAssessorByDict(req);
        // 校验「是否涉及 → 考核责任人必填/非必填」
        validateAssessorByInvolve(req);

        EbgSampTaskTodoBo ebgSampTaskTodoBo = new EbgSampTaskTodoBo();
        EbgSampTaskIndicatorResultBo queryBo = new EbgSampTaskIndicatorResultBo();
        queryBo.setTodoId(req.getTodoId());
        queryBo.setCheckType(req.getCheckType());
        queryBo.setBusiType(req.getBusiType());
        queryBo.setIndicator(req.getIndicator());
        LOGGER.info("updateTodoTaskQuaIndicator getBeforeUpdateData req {}", MessageUtil.filterLogMsg(req));
        List<EbgSampTaskIndicatorResultBo> indicatorList = indicatorResultDao.selectListSortByUpdateTime(queryBo);
        // 先获取原始的指标责任人
        if (CollectionUtils.isEmpty(indicatorList)) {
            throw new BaseException(QuaErrorCodeEnum.DATA_IS_NOT_EXIST.getErrorCode(),
                    new String[]{req.getIndicator()});
        }
        EbgSampTaskIndicatorResultBo indicatorBo = indicatorList.get(0);
        LOGGER.info("updateTodoTaskQuaIndicator getBeforeUpdateData indicatorBo {}",
                MessageUtil.filterLogMsg(indicatorBo));
        // 存一下 更新前的指标责任人
        IndicatorHistoryBo indicatorHistoryBo = new IndicatorHistoryBo();
        indicatorHistoryBo.setOriginalCseAssessor(indicatorBo.getCseAssessor());
        indicatorHistoryBo.setOriginalPseAssessor(indicatorBo.getPseAssessor());
        indicatorHistoryBo.setOriginalCcrAssessor(indicatorBo.getCcrAssessor());
        // 是否是重大问题的待办
        boolean isMajor = BusinessTypeEnum.ECare_Major.getCode().equals(req.getBusiType());
        // 1. 根据检查类型 来 获取待办任务信息
        ebgSampTaskTodoBo = getEbgSampTaskTodoBo(req.getTodoId(), isMajor);

        if (Objects.isNull(ebgSampTaskTodoBo)) {
            throw new BaseException(QuaErrorCodeEnum.DATA_NOT_EXIST_OR_NO_PERMISSION.getErrorCode());
        }
        String currentLoginUser = UserSessionUtil.getSession().getUserAccount();
        try {
            // 3. 根据操作类型分发处理逻辑（使用 if-else 替代 switch）
            String operationType = req.getOperationType();
            if (EbgConstants.FIRST_REVIEW_INDICATOR_EDIT.equals(operationType)) {
                // 处理初审
                handleFirstReview(req, ebgSampTaskTodoBo, currentLoginUser, isMajor, indicatorMap);
            } else if (EbgConstants.FINAL_REVIEW_INDICATOR_EDIT.equals(operationType)) {
                // 处理复审
                handleFinalReview(req, ebgSampTaskTodoBo, currentLoginUser, isMajor, indicatorMap);
            } else if (EbgConstants.CLOSE_INDICATOR_EDIT.equals(operationType)) {
                // 处理关闭
                handleClose(req, ebgSampTaskTodoBo, isMajor);
            } else if (EbgConstants.APPEAL_INDICATOR_EDIT.equals(operationType)) {
                // 处理申诉
                handleAppeal(req, ebgSampTaskTodoBo, currentLoginUser, isMajor, indicatorMap);
            } else {
                throw new BaseException(QuaErrorCodeEnum.PARAM_ERROR.getErrorCode());
            }
            // 4. 附件处理（如果存在文件id） 上传环节：初审、申诉、复审、关闭
            handleAttachment(req);
            // 5. 记录操作日志
            recordOperationLog(req, currentLoginUser, indicatorMap, indicatorHistoryBo);
        } catch (DataIntegrityViolationException e) {
            // 责任人长度超限：指标表(≤100)、待办表/base表(≤1000)
            ExceptionPrint.print(e);
            LOGGER.error("updateTodoTaskQuaIndicator assessor length overflow, todoId:{}", req.getTodoId());
            throw new BaseException(QuaErrorCodeEnum.ASSESSOR_LENGTH_ERROR.getErrorCode());
        }
    }

    private void handleFirstReview(TodoTaskQuaIndicatorUpdateBo req, EbgSampTaskTodoBo todoBo,
                                   String loginUser, Boolean isMajor, Map<String, String> indicatorNameMap) {
        // 1. 权限与状态校验
        validateFirstReviewPermission(todoBo, loginUser);

        // 2. 更新当前指标结果
        EbgSampTaskIndicatorResultBo indicatorResultReqBo = new EbgSampTaskIndicatorResultBo();
        BeanUtils.copyProperties(req, indicatorResultReqBo);
        indicatorResultReqBo.setQuantityResult(req.getCheckResult());
        indicatorResultReqBo.setIndicatorId(req.getIndicator());
        indicatorResultReqBo.setFirstReviewRemark(req.getRemarkOrAppealReason());
        indicatorResultReqBo.setUpdateUser(loginUser);
        indicatorResultReqBo.setUpdateTime(new Date());
        // 通过的指标 不需要设置小组长
        dealAppealConfirm(req, todoBo, indicatorResultReqBo);
        indicatorResultDao.updateTaskResult(indicatorResultReqBo);

        // 3. 获取最新的全量指标结果
        List<EbgSampTaskIndicatorResultBo> indicatorList = getIndicatorResultList(req);

        // 4. 聚合指标信息 (使用 LinkedHashSet 实现去重并保持顺序)
        Set<String> cseAssessorSet = new LinkedHashSet<>();
        Set<String> pseAssessorSet = new LinkedHashSet<>();
        Set<String> ccrAssessorSet = new LinkedHashSet<>();
        List<String> firstRemarkList = new ArrayList<>();
        boolean passFlag = true;

        if (CollectionUtils.isNotEmpty(indicatorList)) {
            for (EbgSampTaskIndicatorResultBo item : indicatorList) {
                // 拼接备注
                String indicatorName = indicatorNameMap.getOrDefault(item.getIndicator(), "未知指标");
                if (StringUtils.isNotEmpty(item.getFirstReviewRemark())) {
                    firstRemarkList.add(indicatorName + ":" + item.getFirstReviewRemark() + "\n");
                }
                // 收集责任人
                addIfNotEmpty(cseAssessorSet, item.getCseAssessor(), item.getQuantityResult());
                addIfNotEmpty(pseAssessorSet, item.getPseAssessor(), item.getQuantityResult());
                addIfNotEmpty(ccrAssessorSet, item.getCcrAssessor(), item.getQuantityResult());

                // 判断是否有不通过项
                if (QualityResultEnum.NOT_PASS.getType().equals(item.getQuantityResult())) {
                    passFlag = false;
                }
            }
        }
        // 5. 更新待办表基础信息
        if (!firstRemarkList.isEmpty()) {
            todoBo.setFirstReviewRemark(StringUtils.join(firstRemarkList, Constants.COMMA));
        }
        // 6. 处理三类责任人及申诉人
        processAssessor(cseAssessorSet, todoBo::setCseAssessor);
        processAssessor(pseAssessorSet, todoBo::setPseAssessor);
        processAssessor(ccrAssessorSet, todoBo::setCcrAssessor);
        // 8. 设置最终质检结果
        todoBo.setSrRuleResult(passFlag ? QualityResultEnum.PASS.getType() : QualityResultEnum.NOT_PASS.getType());

        // 9. 更新待办表
        updateToDoInfo(todoBo, isMajor);
    }

    /**
     * 校验初审权限与状态
     *
     * @param todoBo 待办表
     * @param loginUser 登录人
     */
    private void validateFirstReviewPermission(EbgSampTaskTodoBo todoBo, String loginUser) {
        String firstReviewProcessor = todoBo.getFirstReviewProcessor();
        if (StringUtils.isNotEmpty(firstReviewProcessor)) {
            List<String> processors = Arrays.asList(firstReviewProcessor.split(Constants.COMMA));
            if (!processors.contains(loginUser) && !RoleUtil.isSysOrModuleAdmin(Objects
                    .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
                throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
            }
        }
        if (!EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(todoBo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.CHECK_STATUS_NOT_MATCH.getErrorCode());
        }
    }


    /**
     * 处理责任人集合与申诉人更新逻辑
     *
     * @param assessorSet 集合
     * @param todoBoSetter setter
     */
    private void processAssessor(Set<String> assessorSet, Consumer<String> todoBoSetter) {
        String assessorStr = "";
        if (!assessorSet.isEmpty()) {
            assessorStr = StringUtils.join(assessorSet, Constants.COMMA);
        }
        todoBoSetter.accept(assessorStr); // 更新 todoBo 中的责任人字段
    }


    /**
     * 责任人非空且 判断结果为不通过的时候加入集合
     *
     * @param set 集合
     * @param value 责任人
     * @param checkResult 判断结果
     */
    private void addIfNotEmpty(Set<String> set, String value, String checkResult) {
        if (StringUtils.isNotEmpty(value) && QualityResultEnum.NOT_PASS.getType().equals(checkResult)) {
            List<String> processors = Arrays.asList(value.split(Constants.COMMA));
            set.addAll(processors);
        }
    }

    private static void dealAppealConfirm(TodoTaskQuaIndicatorUpdateBo req, EbgSampTaskTodoBo todoBo,
                                          EbgSampTaskIndicatorResultBo indicatorResultReqBo) {
        // g/h. 根据质检结果更新申诉确认状态
        if (EbgConstants.NORMAL_CHECK_STATUS.equals(req.getCheckResult())) {
            indicatorResultReqBo.setAppealConfirm(EbgConstants.APPEAL_CONFIRM);
        } else if (EbgConstants.EXCEPTION_CHECK_STATUS.equals(req.getCheckResult())) {
            indicatorResultReqBo.setAppealConfirm(EbgConstants.APPEAL_NOT_CONFIRM);
        }
    }

    /**
     * 根据是否是重大问题，更新采样表或者重大问题待办表
     *
     * @param todoBo  待办BO
     * @param isMajor 是否是重大问题
     */
    private void updateToDoInfo(EbgSampTaskTodoBo todoBo, Boolean isMajor) {
        // 根据业务类型来判断
        if (!isMajor) {
            SaveEbgSampTaskTodoReqBo sampTaskTodoReqBo = new SaveEbgSampTaskTodoReqBo();
            BeanUtils.copyProperties(todoBo, sampTaskTodoReqBo);
            sampTaskTodoDao.updateTaskTodo(sampTaskTodoReqBo);
        }
        if (isMajor) {
            SaveEbgSampTaskBaseReqBo sampTaskTodoReqBo = new SaveEbgSampTaskBaseReqBo();
            BeanUtils.copyProperties(todoBo, sampTaskTodoReqBo);
            sampTaskTodoReqBo.setCseAppealProcessor(todoBo.getCseProcessor());
            sampTaskTodoReqBo.setPseAppealProcessor(todoBo.getPseProcessor());
            sampTaskTodoMajorDao.updateTaskBase(sampTaskTodoReqBo);
        }
    }

    /**
     * 申诉处理
     *
     * @param req              待办入参
     * @param todoBo           采样待办BO
     * @param loginUser        登录人
     * @param isMajor          是否重大问题Flag
     * @param indicatorNameMap 指标map
     */
    private void handleAppeal(TodoTaskQuaIndicatorUpdateBo req, EbgSampTaskTodoBo todoBo,
                              String loginUser, Boolean isMajor, Map<String, String> indicatorNameMap) {
        String assessorType = req.getAssessorType();
        // 1、校验当前登录人的申诉权限
        checkAppealAuth(todoBo, isMajor, assessorType, loginUser);

        // 2、封装参数准备更新指标信息
        EbgSampTaskIndicatorResultBo indicatorResultReqBo = new EbgSampTaskIndicatorResultBo();
        BeanUtils.copyProperties(req, indicatorResultReqBo);
        indicatorResultReqBo.setQuantityResult(req.getCheckResult());
        // b. 申诉确认改为已确认
        indicatorResultReqBo.setAppealConfirm(EbgConstants.APPEAL_CONFIRM);
        indicatorResultReqBo.setIsAppeal(req.getIsAppealed());
        indicatorResultReqBo.setAppealReason(req.getRemarkOrAppealReason());
        indicatorResultReqBo.setIndicatorId(req.getIndicator());
        indicatorResultReqBo.setUpdateUser(loginUser);
        indicatorResultReqBo.setUpdateTime(new Date());
        indicatorResultDao.updateTaskResult(indicatorResultReqBo);

        // 3、指标编辑结束 获取最新的申诉备注 更新代办表
        List<EbgSampTaskIndicatorResultBo> indicatorList = getIndicatorResultList(req);
        List<String> appealRemarkList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(indicatorList)) {
            indicatorList.forEach(item -> {
                        // 4. 使用 StringBuilder 替代 "+" 拼接，提升循环中的字符串拼接性能
                        String indicatorName = indicatorNameMap.getOrDefault(item.getIndicator(),
                                "未知指标");
                        if (StringUtils.isNotEmpty(item.getAppealReason())) {
                            String remark = indicatorName + ":" + item.getAppealReason() + "\n";
                            appealRemarkList.add(remark);
                        }
                    }
            );
        }
        String newAppealRemark = StringUtils.join(appealRemarkList, Constants.COMMA);
        // 3.2. 追加申诉备注
        if (StringUtils.isNotEmpty(newAppealRemark)) {
            if (EbgConstants.ASS_CSE.equals(assessorType)) {
                todoBo.setCseAppealRemark(newAppealRemark);
            } else if (EbgConstants.ASS_PSE.equals(assessorType)
            ) {
                todoBo.setPseAppealRemark(newAppealRemark);
            } else {
                todoBo.setCcrAppealRemark(newAppealRemark);
            }
        }
        // 3.3 更新采样待办表 申诉备注
        updateToDoInfo(todoBo, isMajor);
    }

    /**
     * 获取指标列表
     *
     * @param req 请求参数
     * @return 返沪列表
     */
    private List<EbgSampTaskIndicatorResultBo> getIndicatorResultList(TodoTaskQuaIndicatorUpdateBo req) {
        EbgSampTaskIndicatorResultBo indicatorResultBo = new EbgSampTaskIndicatorResultBo();
        indicatorResultBo.setTodoId(req.getTodoId());
        indicatorResultBo.setCheckType(req.getCheckType());
        indicatorResultBo.setBusiType(req.getBusiType());
        // 3.1 查询该任务下所有的指标结果
        return indicatorResultDao.selectListSortByUpdateTime(indicatorResultBo);
    }

    /**
     * 获取指标细项列表
     *
     * @param req 请求参数
     * @return 返沪列表
     */
    private List<EbgSampTaskCategoryResultBo> getCategoryResultList(TodoRuleTaskResultReqBo req) {
        TodoRuleTaskResultReqBo categoryQuery = new TodoRuleTaskResultReqBo();
        categoryQuery.setBusiType(req.getBusiType());
        categoryQuery.setCheckType(req.getCheckType());
        categoryQuery.setIndicator(req.getIndicator());
        categoryQuery.setTodoId(req.getTodoId());
        // 查询该任务下所有的指标细项结果
        return ebgSampTaskCategoryResultDao.selectTaskResultList(categoryQuery);
    }

    private static void checkAppealAuth(EbgSampTaskTodoBo todoBo, Boolean isMajor, String assessorType,
                                              String loginUser) {
        String currentProcessor = "";
        if (isMajor) {
            if (EbgConstants.ASS_CSE.equals(assessorType)) {
                currentProcessor = todoBo.getCseAppealProcessor();
            } else if (EbgConstants.ASS_PSE.equals(assessorType)) {
                currentProcessor = todoBo.getPseAppealProcessor();
            }
        } else {
            if (EbgConstants.ASS_CSE.equals(assessorType)) {
                currentProcessor = todoBo.getCseProcessor();
            } else {
                currentProcessor = todoBo.getCcrProcessor();
            }
        }
        // a. 判断当前登录人是否是申诉处理人（支持多人配置）
        boolean isValidAppealer = false;
        if (StringUtils.isNotBlank(currentProcessor)) {
            isValidAppealer = Arrays.stream(currentProcessor.split(",")).map(String::trim) // 去除可能存在的首尾空格
                    .anyMatch(loginUser::equals);
        }
        if (!isValidAppealer && !RoleUtil.isSysOrModuleAdmin(Objects
                .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
        }
    }

    /**
     * 复审处理
     *
     * @param req              待办入参
     * @param todoBo           采样待办BO
     * @param loginUser        登录人
     * @param isMajor          是否重大问题Flag
     * @param indicatorNameMap 指标map
     */
    private void handleFinalReview(TodoTaskQuaIndicatorUpdateBo req, EbgSampTaskTodoBo todoBo,
                                   String loginUser, Boolean isMajor, Map<String, String> indicatorNameMap) {
        // a. 判断当前登录人是否是复审责任人
        String finalReviewProcessor = todoBo.getFinalReviewProcessor();
        if (StringUtils.isNotEmpty(finalReviewProcessor)) {
            List<String> list = Arrays.asList(finalReviewProcessor.split(Constants.COMMA));
            if (!list.contains(loginUser) && !RoleUtil.isSysOrModuleAdmin(Objects
                    .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
                throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
            }
        }
        // b. 判断当前的状态是否为待复审 (假设 3-待复核/复审)
        if (!EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus().equals(todoBo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.CHECK_STATUS_NOT_MATCH.getErrorCode());
        }
        EbgSampTaskIndicatorResultBo indicatorResultReqBo = new EbgSampTaskIndicatorResultBo();
        BeanUtils.copyProperties(req, indicatorResultReqBo);
        indicatorResultReqBo.setUpdateUser(loginUser);
        indicatorResultReqBo.setUpdateTime(new Date());
        indicatorResultReqBo.setIndicatorId(req.getIndicator());
        indicatorResultReqBo.setQuantityResult(req.getCheckResult());
        indicatorResultDao.updateTaskResult(indicatorResultReqBo);
        // 指标编辑结束 获取最新的复审备注 获取最新的全量指标结果
        List<EbgSampTaskIndicatorResultBo> indicatorList = getIndicatorResultList(req);
        // 4. 聚合指标信息 (使用 LinkedHashSet 实现去重并保持顺序)
        Set<String> cseAssessorSet = new LinkedHashSet<>();
        Set<String> pseAssessorSet = new LinkedHashSet<>();
        Set<String> ccrAssessorSet = new LinkedHashSet<>();
        List<String> finalRemarkList = new ArrayList<>();
        AtomicBoolean passFlag = new AtomicBoolean(true);
        if (CollectionUtils.isNotEmpty(indicatorList)) {
            indicatorList.forEach(item -> {
                // 4. 使用 StringBuilder 替代 "+" 拼接，提升循环中的字符串拼接性能
                String indicatorName = indicatorNameMap.getOrDefault(item.getIndicator(), "未知指标");
                if (StringUtils.isNotEmpty(item.getFinalReviewRemark())) {
                    String remark = indicatorName + ":" + item.getFinalReviewRemark() + "\n";
                    finalRemarkList.add(remark);
                }
                // 收集责任人
                addIfNotEmpty(cseAssessorSet, item.getCseAssessor(), item.getQuantityResult());
                addIfNotEmpty(pseAssessorSet, item.getPseAssessor(), item.getQuantityResult());
                addIfNotEmpty(ccrAssessorSet, item.getCcrAssessor(), item.getQuantityResult());
                // 判断是否有不通过项
                if (QualityResultEnum.NOT_PASS.getType().equals(item.getQuantityResult())) {
                    passFlag.set(false);
                }
            });
        }
        String newFinalRemark = StringUtils.join(finalRemarkList, Constants.COMMA);
        // f. 复审备注更新为 全量复审备注
        if (StringUtils.isNotEmpty(newFinalRemark)) {
            todoBo.setFinalReviewRemark(newFinalRemark);
        }
        // 6. 处理三类责任人及申诉人
        processAssessor(cseAssessorSet, todoBo::setCseAssessor);
        processAssessor(pseAssessorSet, todoBo::setPseAssessor);
        processAssessor(ccrAssessorSet, todoBo::setCcrAssessor);
        // 8. 设置最终质检结果
        todoBo.setSrRuleResult(passFlag.get() ? QualityResultEnum.PASS.getType() :
                QualityResultEnum.NOT_PASS.getType());
        // 更新采样待办表 复审备注
        updateToDoInfo(todoBo, isMajor);
    }

    /**
     * 关闭处理
     *
     * @param req    请求入参
     * @param todoBo 采样待办BO
     * @param isMajor 是否重大问题
     */
    private void handleClose(TodoTaskQuaIndicatorUpdateBo req, EbgSampTaskTodoBo todoBo, Boolean isMajor) {
        // a. 判断当前登录人是否是模块管理员 (需结合具体权限框架)
        if (!RoleUtil.isSysAdmin()) {
            throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
        }
        // b. 判断当前的状态是否为关闭 (假设 4-已完成/关闭)
        if (!EbgSampCheckStatusEnum.FINISH.getStatus().equals(todoBo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.CHECK_STATUS_NOT_MATCH.getErrorCode());
        }
        EbgSampTaskIndicatorResultBo indicatorResultReqBo = new EbgSampTaskIndicatorResultBo();
        BeanUtils.copyProperties(req, indicatorResultReqBo);
        indicatorResultReqBo.setIndicatorId(req.getIndicator());
        indicatorResultReqBo.setUpdateUser(UserSessionUtil.getSession().getUserAccount());
        indicatorResultReqBo.setUpdateTime(new Date());
        indicatorResultReqBo.setQuantityResult(req.getCheckResult());

        indicatorResultDao.updateTaskResult(indicatorResultReqBo);

        // 更新责任人到采样代办表
        // 3. 获取最新的全量指标结果
        List<EbgSampTaskIndicatorResultBo> indicatorList = getIndicatorResultList(req);

        // 4. 聚合指标信息 (使用 LinkedHashSet 实现去重并保持顺序)
        Set<String> cseAssessorSet = new LinkedHashSet<>();
        Set<String> ccrAssessorSet = new LinkedHashSet<>();
        Set<String> pseAssessorSet = new LinkedHashSet<>();
        boolean passFlag = true;
        if (CollectionUtils.isNotEmpty(indicatorList)) {
            for (EbgSampTaskIndicatorResultBo item : indicatorList) {
                // 收集责任人
                addIfNotEmpty(pseAssessorSet, item.getPseAssessor(), item.getQuantityResult());
                addIfNotEmpty(cseAssessorSet, item.getCseAssessor(), item.getQuantityResult());
                addIfNotEmpty(ccrAssessorSet, item.getCcrAssessor(), item.getQuantityResult());
                // 判断是否有不通过项
                if (QualityResultEnum.NOT_PASS.getType().equals(item.getQuantityResult())) {
                    passFlag = false;
                }
            }
        }

        // 6. 处理三类责任人及申诉人
        processAssessor(cseAssessorSet, todoBo::setCseAssessor);
        processAssessor(ccrAssessorSet, todoBo::setCcrAssessor);
        processAssessor(pseAssessorSet, todoBo::setPseAssessor);
        // 8. 设置最终质检结果
        todoBo.setSrRuleResult(passFlag ? QualityResultEnum.PASS.getType() : QualityResultEnum.NOT_PASS.getType());
        // 9. 更新待办表
        updateToDoInfo(todoBo, isMajor);
        // f. 备注只记录到日志，不记录到数据表
        LOGGER.info("close handle, todoId is :{}, remarkOrReason is :{}", todoBo.getTodoId(),
                req.getRemarkOrAppealReason());
    }

    /**
     * 处理附件
     *
     * @param req 编辑入参
     */
    private void handleAttachment(TodoTaskQuaIndicatorUpdateBo req) {
        if (CollectionUtils.isEmpty(req.getFileIds())) {
            return;
        }
        String operationType = req.getOperationType();
        String uploadStage;
        if (EbgConstants.FIRST_REVIEW_INDICATOR_EDIT.equals(operationType)) {
            uploadStage = EbgConstants.FIRST_REVIEW_NAME;
        } else if (EbgConstants.FINAL_REVIEW_INDICATOR_EDIT.equals(operationType)) {
            uploadStage = EbgConstants.FINAL_REVIEW_NAME;
        } else if (EbgConstants.APPEAL_INDICATOR_EDIT.equals(operationType)) {
            uploadStage = EbgConstants.APPEAL_NAME;
        } else {
            uploadStage = EbgConstants.CLOSE_NAME;
        }
        for (Long fileId : req.getFileIds()) {
            // 校验附件存在性
            EbgSampTaskTodoAttachmentBo fileEntity = sampTaskTodoAttachmentDao.selectById(fileId);
            // 合并校验 文件不存在 -> 直接报错
            if (fileEntity == null) {
                LOGGER.error("EbgSampTaskTodoServiceImpl update indicator bind file not exist. fileId: {}",
                        fileId);
                throw new BaseException(QuaErrorCodeEnum.FILE_ID_NOT_EXIST.getErrorCode());
            }
            EbgSampTaskTodoAttachmentBo attachment = new EbgSampTaskTodoAttachmentBo();
            BeanUtils.copyProperties(req, attachment);
            attachment.setFileId(fileId);
            attachment.setUploadStage(uploadStage);
            attachment.setFileStatus(EbgConstants.FILE_RELATED_STATUS); // 1-文件被关联
            sampTaskTodoAttachmentDao.updateTaskTodoAttachment(attachment);
        }
    }

    /**
     * 记录操作日志
     *
     * @param req 请求入参
     * @param loginUser 当前登录用户
     * @param indicatorNameMap 指标map
     * @param indicatorBo 指标信息
     */
    private void recordOperationLog(TodoTaskQuaIndicatorUpdateBo req, String loginUser,
                                    Map<String, String> indicatorNameMap, IndicatorHistoryBo indicatorBo) {
        // 1. 构建日志基础信息
        EbgSampTaskTodoLogBo logBo = new EbgSampTaskTodoLogBo();
        BeanUtils.copyProperties(req, logBo);
        logBo.setOperator(loginUser);
        logBo.setOperateType("修改指标结果");
        logBo.setOperateTime(new Date());

        // 2. 拼接操作内容
        StringBuilder operaContent = new StringBuilder("修改指标结果");

        // 2.1 拼接指标名称
        String indicatorName = indicatorNameMap.get(req.getIndicator());
        if (StringUtils.isNotEmpty(indicatorName)) {
            operaContent.append("(").append(indicatorName).append(")--");
        }

        // 2.2 拼接备注信息
        if (StringUtils.isNotEmpty(req.getRemarkOrAppealReason())) {
            operaContent.append("备注信息: ").append(req.getRemarkOrAppealReason());
        }

        // 2.3 拼接考核责任人变更情况
        appendAssessorChange(operaContent, req.getAssessorType(), req, indicatorBo);

        // 2.4 拼接指标结果变更
        // 1. 提取公共逻辑：统一处理质检结果描述的转换
        String checkResultDesc = QualityResultEnum.PASS.getType().equals(req.getCheckResult())
                ? QualityResultEnum.PASS.getDesc()
                : QualityResultEnum.NOT_PASS.getDesc();

       // 2. 根据操作类型决定结果描述
        String operationDesc;
        String operationType = req.getOperationType();
        if (EbgConstants.APPEAL_INDICATOR_EDIT.equals(operationType)) {
            // 申诉环节有独立的描述逻辑
            operationDesc = EbgConstants.APPEAL_CONFIRM.equals(req.getIsAppealed())
                    ? EbgConstants.NEED_APPEAL
                    : EbgConstants.NO_APPEAL_CONFIRM_NAME;
            operaContent.append(", 当前指标:待申诉修改为").append(operationDesc);
        } else if (EbgConstants.FIRST_REVIEW_INDICATOR_EDIT.equals(operationType)) {
            operaContent.append(", 当前指标:初审结果修改为").append(checkResultDesc);
        } else if (EbgConstants.FINAL_REVIEW_INDICATOR_EDIT.equals(operationType)) {
            operaContent.append(", 当前指标:复审结果修改为").append(checkResultDesc);
        } else {
            // 默认/关闭环节
            operaContent.append(", 当前指标:关闭环节结果修改为").append(checkResultDesc);
        }

        // 3. 保存日志
        logBo.setOperateContent(operaContent.toString());
        sampTaskTodoLogDao.insertTaskTodoLog(logBo);
    }

    /**
     * 提取责任人变更的描述
     *
     * @param operaContent 操作类型
     * @param assessorType 责任人类型
     * @param req          请求参数
     * @param indicatorBo  指标信息
     */
    private void appendAssessorChange(StringBuilder operaContent, String assessorType,
                                      TodoTaskQuaIndicatorUpdateBo req, IndicatorHistoryBo indicatorBo) {
        String originalAssessor;
        String newAssessor;

        if (EbgConstants.ASS_CSE.equals(assessorType)) {
            originalAssessor = indicatorBo.getOriginalCseAssessor();
            newAssessor = req.getCseAssessor();
        } else if (EbgConstants.ASS_CCR.equals(assessorType)) {
            originalAssessor = indicatorBo.getOriginalCcrAssessor();
            newAssessor = req.getCcrAssessor();
        } else {
            originalAssessor = indicatorBo.getOriginalPseAssessor();
            newAssessor = req.getPseAssessor();
        }
        // f. 备注只记录到日志，不记录到数据表
        LOGGER.info("appendAssessorChange, todoId is :{},originalAssessor is :{}, newAssessor {}", req.getTodoId(),
                originalAssessor, newAssessor);
        // 只有当新旧责任人不同时，才需要记录日志
        if (!Objects.equals(newAssessor, originalAssessor)) {
            // 批量获取用户信息，避免多次RPC/DB查询
            List<String> w3List = Arrays.asList(originalAssessor, newAssessor);
            Map<String, UserInfoBo> userInfoBoMap = UserUtil.getUsers(w3List);

            String originalFullName = getDisplayName(userInfoBoMap, originalAssessor);
            String newAssessorFullName = getDisplayName(userInfoBoMap, newAssessor);

            if (StringUtils.isEmpty(originalAssessor)) {
                operaContent.append(", 考核责任人新增").append(newAssessorFullName);
            } else if (StringUtils.isNotEmpty(newAssessor)) {
                operaContent.append(", 考核责任人从").append(originalFullName)
                        .append("改为").append(newAssessorFullName);
            }
        }
    }

    private static void setAssessorByDict(TodoTaskQuaIndicatorUpdateBo req) {
        List<String> assessor = req.getAssessor();
        // 获取扩展属性
        String assessorType = CommonUtil.getDictDataExt(EbgConstants.ECARE_INDICATORS_KEY, req.getIndicator(),
                "sample");
        // 根据指标与责任人类型 配置项 分派不同的责任人
        if (CollectionUtils.isNotEmpty(assessor)) {
            if (EbgConstants.ASS_CSE.equals(assessorType)) {
                req.setCseAssessor(StringUtils.join(assessor, Constants.COMMA));
            }
            if (EbgConstants.ASS_PSE.equals(assessorType)) {
                req.setPseAssessor(StringUtils.join(assessor, Constants.COMMA));
            }
            if (EbgConstants.ASS_CCR.equals(assessorType)) {
                req.setCcrAssessor(StringUtils.join(assessor, Constants.COMMA));
            }
        }
        req.setAssessorType(assessorType);
        // 根据责任人类型 将是否涉及写入对应的字段（0-涉及，1-不涉及）
        if (EbgConstants.ASS_CSE.equals(assessorType)) {
            req.setIsCse(req.getIsInvolve());
        } else if (EbgConstants.ASS_PSE.equals(assessorType)) {
            req.setIsPse(req.getIsInvolve());
        } else if (EbgConstants.ASS_CCR.equals(assessorType)) {
            req.setIsCcr(req.getIsInvolve());
        }
    }

    /**
     * 校验「是否涉及 → 考核责任人必填/非必填」
     * 涉及(0) → 当前指标维度的考核责任人必填
     * 不涉及(1) → 当前指标维度的考核责任人非必填
     *
     * @param req 编辑入参
     */
    private void validateAssessorByInvolve(TodoTaskQuaIndicatorUpdateBo req) {
        // 0=涉及 → 当前指标维度的考核责任人必填
        if ("0".equals(req.getIsInvolve())) {
            String assessorType = req.getAssessorType();
            if (EbgConstants.ASS_CSE.equals(assessorType) && StringUtils.isEmpty(req.getCseAssessor())) {
                throw new BaseException(QuaErrorCodeEnum.PARAM_ERROR.getErrorCode());
            }
            if (EbgConstants.ASS_CCR.equals(assessorType) && StringUtils.isEmpty(req.getCcrAssessor())) {
                throw new BaseException(QuaErrorCodeEnum.PARAM_ERROR.getErrorCode());
            }
            if (EbgConstants.ASS_PSE.equals(assessorType) && StringUtils.isEmpty(req.getPseAssessor())) {
                throw new BaseException(QuaErrorCodeEnum.PARAM_ERROR.getErrorCode());
            }
        }
    }

    /**
     * 获取用户显示名称，如果Map中不存在或名称为空，则降级使用工号/账号
     *
     * @param userInfoBoMap 用户map
     * @param userCode 用户账号
     * @return 用户全名
     */
    private String getDisplayName(Map<String, UserInfoBo> userInfoBoMap, String userCode) {
        if (StringUtils.isEmpty(userCode)) {
            return userCode;
        }
        UserInfoBo userInfo = userInfoBoMap.get(userCode);
        if (userInfo != null && StringUtils.isNotEmpty(userInfo.getFullName())) {
            return userInfo.getFullName();
        }
        return userCode;
    }
}
```
