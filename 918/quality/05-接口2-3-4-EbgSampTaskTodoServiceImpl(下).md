# 05-接口2-3-4-EbgSampTaskTodoServiceImpl(下).md

> 本文件包含以下源码（按包结构整理）

---

```
=== service/src/main/java/com/huawei/qua/ebg/service/impl/EbgSampTaskTodoServiceImpl.java ===
```

```java
    private boolean isAuthorizedUser(String userAccount, EbgSampTaskTodoAttachmentBo attachment, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }

        List<java.util.function.Supplier<String>> userProviders = Arrays.asList(attachment::getUploader,
            attachment::getFirstReviewProcessor, attachment::getCcrProcessor, attachment::getCseProcessor,
            attachment::getCseAppealProcessor, attachment::getPseAppealProcessor, attachment::getFinalReviewProcessor);

        // 检查当前用户是否匹配任何一位处理器
        for (java.util.function.Supplier<String> provider : userProviders) {
            String processorAccount = provider.get();
            if (Strings.CS.equals(userAccount, processorAccount)) {
                return true;
            }
        }

        return false;
    }

    private void getZip(List<EbgSampTaskTodoAttachmentBo> attachmentList, ZipOutputStream zipOut, String userAccount)
        throws IOException, EdmException {
        Set<String> seenNames = new HashSet<>();
        for (EbgSampTaskTodoAttachmentBo attachment : attachmentList) {
            if (StringUtils.isBlank(attachment.getEdmDocId())) {
                LOGGER.warn("EbgSampTaskTodoServiceImpl batchDownload edmDocId is blank for fileId: {}", attachment
                    .getFileId());
                continue;
            }

            String entryName = attachment.getFileName();
            if (StringUtils.isBlank(entryName)) {
                entryName = "file_" + attachment.getFileId();
                entryName = FilenameUtils.getName(entryName);
            }
            // 重名时追加序号，保证 zip 内条目名唯一
            String baseName = entryName;
            String uniqueName = entryName;
            int index = 1;
            while (!seenNames.add(uniqueName)) {
                int dotIndex = baseName.lastIndexOf('.');
                String extension = dotIndex >= 0 ? baseName.substring(dotIndex) : "";
                String namePart = dotIndex >= 0 ? baseName.substring(0, dotIndex) : baseName;
                uniqueName = namePart + " (" + index++ + ")" + extension;
            }
            entryName = uniqueName;

            zipOut.putNextEntry(new ZipEntry(entryName));

            // 使用 try-with-resources 确保 fileBuffer 被正确关闭
            try (ByteArrayOutputStream fileBuffer = new ByteArrayOutputStream()) {
                edmClientUtil.downloadFromEdm(attachment.getEdmDocId(), userAccount, fileBuffer);
                zipOut.write(fileBuffer.toByteArray());
            }

            zipOut.closeEntry();
        }
    }

    @Override
    public void getRecordFile(Long callFileId, HttpServletResponse response) {
        // 1. 基础参数与上下文获取
        String userAccount = UserSessionUtil.getSession().getUserAccount();
        // 2. 数据库查询
        EbgSrFileBo fileEntity = ebgSrFileDao.selectEbgSrFileById(callFileId);

        // 权限检查
        // 逻辑：有管理员/经理/系统管理员角色，则允许下载
        boolean isAdmin = RoleUtil.checkIsSysOrModuleAdminOrAdminOrManager();

        if (!isAdmin) {
            LOGGER.error("EbgSampTaskTodoServiceImpl checkPermission failed. user: {}, fileId: {}", userAccount,
                callFileId);
            throw new BaseException(QuaErrorCodeEnum.NOT_OPERATE_PERMISSION.getErrorCode());
        }

        String fileDownloadVoStr = MessageUtil.filterLogMsg(fileEntity);
        FileEntity file = new FileEntity();
        BeanUtils.copyProperties(fileEntity, file);
        file.setFileId(fileEntity.getSrFileId());
        fileService.checkAndFileDown(fileEntity.getSrFileId(), response, file, fileDownloadVoStr);
    }

    /**
     * 采样待办任务问题录音列表查询
     *
     * @param reqVo 问题单号
     * @param pageNum pageNum
     * @param pageSize pageSize
     * @return Result 问题录音列表
     */
    @Override
    public List<EbgCallRecordRespBo> getRecordQuery(GetEbgSampTaskRecordReqVo reqVo, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;

        List<String> callSnoList = ebgCallRecordDao.queryCallSnoPage(reqVo.getSrNum(), offset, pageSize);

        if (CollectionUtils.isEmpty(callSnoList)) {
            return new ArrayList<>();
        }

        List<EbgCallRecordRespBo> rawList = ebgCallRecordDao.queryBySrNumSubByCallSnoList(reqVo.getSrNum(),
            callSnoList);

        if (CollectionUtils.isEmpty(rawList)) {
            return new ArrayList<>();
        }

        // 2. 按 callSno 分组
        Map<String, List<EbgCallRecordRespBo>> groupedMap = rawList.stream().collect(Collectors.groupingBy(
            EbgCallRecordRespBo::getCallSno));

        // 3. 转换结果
        List<EbgCallRecordRespBo> resultList = new ArrayList<>();
        for (List<EbgCallRecordRespBo> group : groupedMap.values()) {
            EbgCallRecordRespBo result = group.getFirst(); // 取首条作为基础对象

            result.setCallTypeName(CommonUtil.getDictDataNameCn(DataConstants.CALL_TYPE, result.getCallType()));
            result.setCallChnlName(CommonUtil.getDictDataNameCn(DataConstants.CALL_CHNL_TYPE, result.getCallChnl()));
            result.setPhoneNo(anonymizedMobile(result.getPhoneNo()));

            try {
                // 尝试解密 text
                String decrypted = KmsCryptoUtil.decrypt(result.getText());
                result.setText(decrypted);
            } catch (Exception e) {
                // 解密失败，标记为 null，后续聚合时处理
                result.setText(null);
            }

            // 检查该组内是否有任何一条记录解密失败
            boolean needAggregate = group.stream().anyMatch(i -> i.getText() == null);

            setText(group, needAggregate, result);
            resultList.add(result);
        }
        return resultList;
    }

    private static void setText(List<EbgCallRecordRespBo> group, boolean needAggregate, EbgCallRecordRespBo result) {
        if (needAggregate) {
            List<EbgCallRecordRespBo> sortedGroup = group.stream()
                // 1. 过滤掉 subText 为空的记录
                .filter(item -> item.getSubText() != null && !item.getSubText().trim().isEmpty())
                // 2. 【核心修复】手动将 String index 转为 int 进行比较
                .sorted(Comparator.comparingInt(item -> {
                    String idx = item.getIndex();
                    if (idx == null || idx.trim().isEmpty()) {
                        return 0; // 空值排前面
                    }
                    try {
                        return Integer.parseInt(idx.trim());
                    } catch (NumberFormatException e) {
                        return 0; // 非数字排前面，避免报错
                    }
                })).toList();

            // 拼接
            StringBuilder sb = new StringBuilder();
            for (EbgCallRecordRespBo item : sortedGroup) {
                sb.append(item.getSubText());
            }
            result.setText(sb.toString());
        } else {
            // 如果全部解密成功，取第一条成功的 text (或者根据业务逻辑合并)
            String text = group.stream().map(EbgCallRecordRespBo::getText).filter(Objects::nonNull).findFirst().orElse(
                "");
            result.setText(text);
        }
    }

    /**
     * 手机号匿名化处理
     *
     * @param mobileNum 手机号
     * @return 匿名化后的手机号
     */
    public String anonymizedMobile(String mobileNum) {
        if (StringUtils.isNotEmpty(mobileNum) && VALIDATE_PHONE.matcher(mobileNum).matches()) {
            return AnonytionUtils.mobile(mobileNum);
        }
        return mobileNum;
    }

    /**
     * 操作日志列表查询
     *
     * @param reqBo 采样任务代办信息
     * @return 日志列表
     * @throws Exception Exception
     */
    @Override
    public PageInfo<EbgSampTaskTodoLogVo> getLogQuery(GetEbgSampTaskTodoDetailReqBo reqBo) throws Exception {
        // 1. 查询数据
        List<EbgSampTaskTodoLogBo> list = ebgSampTaskTodoLogDao.selectTaskTodoLog(reqBo);
        if (CollectionUtils.isEmpty(list)) {
            return new PageInfo<>(new ArrayList<>());
        }
        // 2. 提取非空操作员账号并查询用户信息
        // 使用 Stream 收集非空 operator，去重，转为 List 假设 isNotEmpty 判断非 null 且非空串
        List<String> accountList = list.stream().map(EbgSampTaskTodoLogBo::getOperator).filter(StringUtils::isNotEmpty)
                .distinct().collect(Collectors.toList());
        // 获取用户信息 Map，假设 UserUtil.getUsers 在传入空列表时返回空 Map 而非 null
        Map<String, UserInfoBo> userMap = CollectionUtils.isEmpty(accountList)
                ? Collections.emptyMap() : com.huawei.qua.common.util.UserUtil.getUsers(accountList);
        // 3. 转换 VO 列表
        List<EbgSampTaskTodoLogVo> voList = list.stream().map(item -> {
            EbgSampTaskTodoLogVo vo = new EbgSampTaskTodoLogVo();
            BeanUtils.copyProperties(item, vo);
            // 安全设置 operatorName
            String operator = item.getOperator();
            if (Constants.SYSTEM_ACCOUNT.equals(operator)) {
                vo.setOperatorName(Constants.SYSTEM_ACCOUNT);
            } else if (StringUtils.isNotEmpty(operator)) {
                UserInfoBo userInfo = userMap.get(operator);
                vo.setOperatorName(userInfo != null ? userInfo.getFullName() : "");
            } else {
                vo.setOperatorName("");
            }
            // 设置 operateType
            String operateTypeStr = item.getOperateType();
            if (EbgSampOperateTypeEnum.isValidEnum(operateTypeStr)) {
                vo.setOperateType(CommonUtil.getDictDataNameCn(DataConstants.OPERATE_TYPE, operateTypeStr));
            } else {
                vo.setOperateType(operateTypeStr);
            }
            return vo;
        }).collect(Collectors.toList());
        // 4. 构建 PageInfo
        PageInfo<EbgSampTaskTodoLogBo> resultBoPage = new PageInfo<>(list);
        PageInfo<EbgSampTaskTodoLogVo> resultVoPage = LocalBeanUtil
                .copyPageInfo(resultBoPage, EbgSampTaskTodoLogVo.class);
        resultVoPage.setList(voList);
        return resultVoPage;
    }

    /**
     * 采样待办任务转派接口
     *
     * @param reqBo 采样任务代办转派信息
     * @return 结果
     */
    @Override
    public int transfer(GetEbgSampTaskTodoTransferReqBo reqBo) {
        String userAccount = Objects.requireNonNull(UserSessionUtil.getSession()).getUserAccount();
        int result = 0;
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList = new ArrayList<>();
        EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo = new EbgSampTaskTodoLogBo();
        ebgSampTaskTodoLogBo.setTodoId(reqBo.getTodoId());
        ebgSampTaskTodoLogBo.setOperateTime(new Date());
        ebgSampTaskTodoLogBo.setOperator(userAccount);
        if (!StringUtils.isEmpty(reqBo.getBusiType())) {
            if (EbgBusiTypeEnum.ECARE.getType().equals(reqBo.getBusiType())) {
                EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(reqBo.getTodoId());
                result = getTodoResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                    userAccount, result);
            } else {
                EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(reqBo.getTodoId());
                result = getBaseResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo,
                    userAccount, result);
            }
        } else {
            // busiType 为空 → 先查 todo 再查 base
            EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(reqBo.getTodoId());
            if (ObjectUtils.isNotEmpty(ebgSampTaskTodoVo)) {
                result = getTodoResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                    userAccount, result);
                ebgSampTaskTodoLogDao.insertTaskTodoLog(ebgSampTaskTodoLogBo);
                return result;
            }
            EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(reqBo.getTodoId());
            if (ObjectUtils.isNotEmpty(ebgSampTaskBaseVo)) {
                result = getBaseResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo,
                    userAccount, result);
                ebgSampTaskTodoLogDao.insertTaskTodoLog(ebgSampTaskTodoLogBo);
                return result;
            }
        }

        ebgSampTaskTodoLogDao.batchAdd(ebgSampTaskTodoLogBoList);
        return result;
    }

    /**
     * 追加转派备注到操作日志
     *
     * @param logBo 操作日志
     * @param reqBo 转派请求
     * @param stage 转派环节（初审/复审/申诉）
     */
    private void appendRemark(EbgSampTaskTodoLogBo logBo, GetEbgSampTaskTodoTransferReqBo reqBo, String stage) {
        if (!StringUtils.isEmpty(reqBo.getTransferRemark())) {
            logBo.setOperateContent(logBo.getOperateContent() + stage + "转派备注：" + reqBo.getTransferRemark() + "；");
        }
    }

    private int getBaseResult(GetEbgSampTaskTodoTransferReqBo reqBo, EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo, String userAccount,
        int result) {
        ebgSampTaskTodoLogBo.setBusiType(ebgSampTaskBaseVo.getBusiType());
        ebgSampTaskTodoLogBo.setCheckType(ebgSampTaskBaseVo.getCheckType());
        SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo = new SaveEbgSampTaskBaseReqBo();
        saveEbgSampTaskBaseReqBo.setTodoId(reqBo.getTodoId());
        if (EbgSampApprovalTypeEnum.FIRST_REVIEW.getCode().equals(reqBo.getTransferType())) {
            if (!userAccount.equals(ebgSampTaskBaseVo.getFirstReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
                    .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
                throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
            }
            saveEbgSampTaskBaseReqBo.setFirstReviewProcessor(reqBo.getTransferAssignee());
            saveEbgSampTaskBaseReqBo.setCurrentProcessor(reqBo.getTransferAssignee());
            result = ebgSampTaskBaseDao.updateTaskBase(saveEbgSampTaskBaseReqBo);
            ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FIRST_REVIEW.getCode());
            ebgSampTaskTodoLogBo.setOperateContent("初审转派完成；初审责任人从" + ebgSampTaskBaseVo.getFirstReviewProcessor() + "修改为"
                + reqBo.getTransferAssignee() + "；");
            // 添加转派备注
            appendRemark(ebgSampTaskTodoLogBo, reqBo, "初审");
            ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        } else if (EbgSampApprovalTypeEnum.APPEAL.getCode().equals(reqBo.getTransferType())) {
            result = getAppealResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList,
                    ebgSampTaskBaseVo, userAccount, saveEbgSampTaskBaseReqBo);
        } else if (EbgSampApprovalTypeEnum.FINAL_REVIEW.getCode().equals(reqBo.getTransferType())) {
            if (!userAccount.equals(ebgSampTaskBaseVo.getFinalReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
                    .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
                throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
            }
            saveEbgSampTaskBaseReqBo.setFinalReviewProcessor(reqBo.getTransferAssignee());
            saveEbgSampTaskBaseReqBo.setCurrentProcessor(reqBo.getTransferAssignee());
            result = ebgSampTaskBaseDao.updateTaskBase(saveEbgSampTaskBaseReqBo);
            ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FINAL_REVIEW.getCode());
            ebgSampTaskTodoLogBo.setOperateContent("复审转派完成；复审责任人从" + ebgSampTaskBaseVo.getFinalReviewProcessor() + "修改为"
                + reqBo.getTransferAssignee() + "；");
            // 添加转派备注
            appendRemark(ebgSampTaskTodoLogBo, reqBo, "复审");
            ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        }
        return result;
    }

    private int getAppealResult(GetEbgSampTaskTodoTransferReqBo reqBo, EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
                               List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo,
                               String userAccount, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo) {
        int result;
        boolean pseModified = false;
        boolean cseModified = false;
        if (!StringUtils.isEmpty(ebgSampTaskBaseVo.getPseAppealProcessor())
                && (userAccount.equals(ebgSampTaskBaseVo.getPseAppealProcessor())
                || RoleUtil.isSysOrModuleAdmin(Objects.requireNonNull(UserSessionUtil.getSession()).getUserId()))) {
            saveEbgSampTaskBaseReqBo.setPseAppealProcessor(reqBo.getPseProcessor());
            EbgSampTaskTodoLogBo pseLog = new EbgSampTaskTodoLogBo();
            BeanUtils.copyProperties(ebgSampTaskTodoLogBo, pseLog);
            pseLog.setOperateType(EbgSampOperateTypeEnum.PSE_APPEAL.getCode());
            pseLog.setOperateContent("申诉转派完成；申诉责任人从" + ebgSampTaskBaseVo.getPseAppealProcessor()
                + "修改为" + reqBo.getPseProcessor() + "；");
            // 添加转派备注
            appendRemark(pseLog, reqBo, "申诉");
            ebgSampTaskTodoLogBoList.add(pseLog);
            pseModified = true;
        }
        if (!StringUtils.isEmpty(ebgSampTaskBaseVo.getCseAppealProcessor())
                && (userAccount.equals(ebgSampTaskBaseVo.getCseAppealProcessor())
                || RoleUtil.isSysOrModuleAdmin(Objects.requireNonNull(UserSessionUtil.getSession()).getUserId()))) {
            saveEbgSampTaskBaseReqBo.setCseAppealProcessor(reqBo.getCseProcessor());
            EbgSampTaskTodoLogBo cseLog = new EbgSampTaskTodoLogBo();
            BeanUtils.copyProperties(ebgSampTaskTodoLogBo, cseLog);
            cseLog.setOperateType(EbgSampOperateTypeEnum.CSE_APPEAL.getCode());
            cseLog.setOperateContent("申诉转派完成；申诉责任人从" + ebgSampTaskBaseVo.getCseAppealProcessor()
                + "修改为" + reqBo.getCseProcessor() + "；");
            // 添加转派备注
            appendRemark(cseLog, reqBo, "申诉");
            ebgSampTaskTodoLogBoList.add(cseLog);
            cseModified = true;
        }
        if (!pseModified && !cseModified) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        saveEbgSampTaskBaseReqBo.setCurrentProcessor(mergeProcessors(
                pseModified ? reqBo.getPseProcessor() : ebgSampTaskBaseVo.getPseAppealProcessor(),
                cseModified ? reqBo.getCseProcessor() : ebgSampTaskBaseVo.getCseAppealProcessor()
        ));
        result = ebgSampTaskBaseDao.updateTaskBase(saveEbgSampTaskBaseReqBo);
        return result;
    }

    private int getTodoResult(GetEbgSampTaskTodoTransferReqBo reqBo, EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo, String userAccount,
        int result) {
        ebgSampTaskTodoLogBo.setBusiType(ebgSampTaskTodoVo.getBusiType());
        ebgSampTaskTodoLogBo.setCheckType(ebgSampTaskTodoVo.getCheckType());

        SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo = new SaveEbgSampTaskTodoReqBo();
        saveEbgSampTaskTodoReqBo.setTodoId(reqBo.getTodoId());

        if (EbgSampApprovalTypeEnum.FIRST_REVIEW.getCode().equals(reqBo.getTransferType())) {
            result = handleFirstReviewTransfer(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                userAccount, saveEbgSampTaskTodoReqBo);
        } else if (EbgSampApprovalTypeEnum.APPEAL.getCode().equals(reqBo.getTransferType())) {
            result = handleAppealTransfer(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                userAccount, saveEbgSampTaskTodoReqBo);
        } else if (EbgSampApprovalTypeEnum.FINAL_REVIEW.getCode().equals(reqBo.getTransferType())) {
            result = handleFinalReviewTransfer(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                userAccount, saveEbgSampTaskTodoReqBo);
        }
        return result;
    }

    private int handleFirstReviewTransfer(GetEbgSampTaskTodoTransferReqBo reqBo,
        EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo, List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList,
        EbgSampTaskTodoVo ebgSampTaskTodoVo, String userAccount, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo) {
        if (!userAccount.equals(ebgSampTaskTodoVo.getFirstReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
                .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        saveEbgSampTaskTodoReqBo.setFirstReviewProcessor(reqBo.getTransferAssignee());
        saveEbgSampTaskTodoReqBo.setCurrentProcessor(reqBo.getTransferAssignee());
        int result = ebgSampTaskTodoDao.updateTaskTodo(saveEbgSampTaskTodoReqBo);

        ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FIRST_REVIEW.getCode());
        ebgSampTaskTodoLogBo.setOperateContent("初审转派完成；初审责任人从" + ebgSampTaskTodoVo.getFirstReviewProcessor() + "修改为"
            + reqBo.getTransferAssignee() + "；");
        // 添加转派备注
        appendRemark(ebgSampTaskTodoLogBo, reqBo, "初审");
        ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        return result;
    }

    private int handleAppealTransfer(GetEbgSampTaskTodoTransferReqBo reqBo, EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo, String userAccount,
        SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo) {
        boolean ccrModified = false;
        boolean cseModified = false;
        if (!StringUtils.isEmpty(ebgSampTaskTodoVo.getCcrProcessor())
                && (userAccount.equals(ebgSampTaskTodoVo.getCcrProcessor())
                || RoleUtil.isSysOrModuleAdmin(Objects.requireNonNull(UserSessionUtil.getSession()).getUserId()))) {
            saveEbgSampTaskTodoReqBo.setCcrProcessor(reqBo.getCcrProcessor());
            EbgSampTaskTodoLogBo ccrLog = new EbgSampTaskTodoLogBo();
            BeanUtils.copyProperties(ebgSampTaskTodoLogBo, ccrLog);
            ccrLog.setOperateType(EbgSampOperateTypeEnum.CCR_APPEAL.getCode());
            ccrLog.setOperateContent("申诉转派完成；申诉责任人从" + ebgSampTaskTodoVo.getCcrProcessor() + "修改为" + reqBo
                .getCcrProcessor() + "；");
            // 添加转派备注
            appendRemark(ccrLog, reqBo, "申诉");
            ebgSampTaskTodoLogBoList.add(ccrLog);
            ccrModified = true;
        }
        if (!StringUtils.isEmpty(ebgSampTaskTodoVo.getCseProcessor())
                && (userAccount.equals(ebgSampTaskTodoVo.getCseProcessor())
                || RoleUtil.isSysOrModuleAdmin(Objects.requireNonNull(UserSessionUtil.getSession()).getUserId()))) {
            saveEbgSampTaskTodoReqBo.setCseProcessor(reqBo.getCseProcessor());
            EbgSampTaskTodoLogBo cseLog = new EbgSampTaskTodoLogBo();
            BeanUtils.copyProperties(ebgSampTaskTodoLogBo, cseLog);
            cseLog.setOperateType(EbgSampOperateTypeEnum.CSE_APPEAL.getCode());
            cseLog.setOperateContent("申诉转派完成；申诉责任人从" + ebgSampTaskTodoVo.getCseProcessor() + "修改为" + reqBo
                .getCseProcessor() + "；");
            // 添加转派备注
            appendRemark(cseLog, reqBo, "申诉");
            ebgSampTaskTodoLogBoList.add(cseLog);
            cseModified = true;
        }
        if (!ccrModified && !cseModified) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        saveEbgSampTaskTodoReqBo.setCurrentProcessor(mergeProcessors(
                ccrModified ? reqBo.getCcrProcessor() : ebgSampTaskTodoVo.getCcrProcessor(),
                cseModified ? reqBo.getCseProcessor() : ebgSampTaskTodoVo.getCseProcessor()
        ));
        int result = ebgSampTaskTodoDao.updateTaskTodo(saveEbgSampTaskTodoReqBo);
        return result;
    }

    private int handleFinalReviewTransfer(GetEbgSampTaskTodoTransferReqBo reqBo,
        EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo, List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList,
        EbgSampTaskTodoVo ebgSampTaskTodoVo, String userAccount, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo) {
        if (!userAccount.equals(ebgSampTaskTodoVo.getFinalReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
                .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        saveEbgSampTaskTodoReqBo.setFinalReviewProcessor(reqBo.getTransferAssignee());
        saveEbgSampTaskTodoReqBo.setCurrentProcessor(reqBo.getTransferAssignee());
        int result = ebgSampTaskTodoDao.updateTaskTodo(saveEbgSampTaskTodoReqBo);

        ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FINAL_REVIEW.getCode());
        ebgSampTaskTodoLogBo.setOperateContent("复审转派完成；复审责任人从" + ebgSampTaskTodoVo.getFinalReviewProcessor() + "修改为"
            + reqBo.getTransferAssignee() + "；");
        // 添加转派备注
        appendRemark(ebgSampTaskTodoLogBo, reqBo, "复审");
        ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        return result;
    }

    /**
     * 采样待办任务批量转派接口
     *
     * @param reqBo 采样任务代办批量转派信息
     * @return 批量转派结果
     */
    @Override
    public TransferBatchRespVo transferBatch(TransferBatchReqBo reqBo) {
        List<TransferFailItemVo> failures = new ArrayList<>();
        int successCount = 0;

        for (Long todoId : reqBo.getTodoIds()) {
            try {
                // 1. 校验待办是否存在
                if (!todoExists(todoId, reqBo.getBusiType())) {
                    failures.add(new TransferFailItemVo(todoId, DATA_IS_NOT_EXIST.getErrorCode().getMessage()));
                    continue;
                }

                // 2. 构建单条转派请求，调用现有单条转派方法
                GetEbgSampTaskTodoTransferReqBo singleReqBo = new GetEbgSampTaskTodoTransferReqBo();
                singleReqBo.setTodoId(todoId);
                singleReqBo.setBusiType(reqBo.getBusiType());
                singleReqBo.setTransferType(reqBo.getTransferType());
                singleReqBo.setTransferAssignee(reqBo.getTransferAssignee());
                singleReqBo.setCseProcessor(reqBo.getCseProcessor());
                singleReqBo.setCcrProcessor(reqBo.getCcrProcessor());
                singleReqBo.setPseProcessor(reqBo.getPseProcessor());
                transfer(singleReqBo);
                successCount++;
            } catch (BaseException e) {
                failures.add(new TransferFailItemVo(todoId, e.getMessage()));
            }
        }

        return new TransferBatchRespVo(successCount, failures);
    }

    /**
     * 校验待办是否存在
     *
     * @param todoId 待办ID
     * @param busiType 业务类型
     * @return 是否存在
     */
    private boolean todoExists(Long todoId, String busiType) {
        if (EbgBusiTypeEnum.ECARE.getType().equals(busiType)) {
            return ebgSampTaskTodoDao.selectTaskTodoVoById(todoId) != null;
        } else {
            return ebgSampTaskBaseDao.selectTaskBaseVoById(todoId) != null;
        }
    }

    /**
     * 采样待办任务流程提交接口
     *
     * @param reqBo 采样任务代办提交信息
     * @return 结果
     */
    @Override
    public int commit(GetEbgSampTaskTodoCommitReqBo reqBo) {
        String userAccount = Objects.requireNonNull(UserSessionUtil.getSession()).getUserAccount();
        String curRegion = LocaleUtil.getLocale().toString();
        int result = 0;
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList = new ArrayList<>();
        EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo = new EbgSampTaskTodoLogBo();
        ebgSampTaskTodoLogBo.setTodoId(reqBo.getTodoId());
        ebgSampTaskTodoLogBo.setOperateTime(new Date());
        ebgSampTaskTodoLogBo.setOperator(userAccount);
        EbgSampTaskIndicatorResultBo indicatorResultBo = new EbgSampTaskIndicatorResultBo();
        indicatorResultBo.setTodoId(reqBo.getTodoId());
        indicatorResultBo.setBusiType(reqBo.getBusiType());
        indicatorResultBo.setCheckType(reqBo.getCheckType());
        // 2. 查询该任务下所有的指标结果
        List<EbgSampTaskIndicatorResultBo> indicatorList = ebgSampTaskIndicatorResultDao.selectList(indicatorResultBo);
        if (!StringUtils.isEmpty(reqBo.getBusiType())) {
            if (EbgBusiTypeEnum.ECARE.getType().equals(reqBo.getBusiType())) {
                EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(reqBo.getTodoId());
                if (ObjectUtils.isNotEmpty(ebgSampTaskTodoVo)) {
                    result = getTodoResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                            userAccount, result, curRegion, indicatorList);
                    ebgSampTaskTodoLogDao.batchAdd(ebgSampTaskTodoLogBoList);
                }
            } else {
                EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(reqBo.getTodoId());
                if (ObjectUtils.isNotEmpty(ebgSampTaskBaseVo)) {
                    result = getBaseResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo,
                        userAccount, result, curRegion, indicatorList);
                    ebgSampTaskTodoLogDao.batchAdd(ebgSampTaskTodoLogBoList);
                }
            }
        } else {
            EbgSampTaskTodoVo ebgSampTaskTodoVo = ebgSampTaskTodoDao.selectTaskTodoVoById(reqBo.getTodoId());
            if (ObjectUtils.isNotEmpty(ebgSampTaskTodoVo)) {
                result = getTodoResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                        userAccount, result, curRegion, indicatorList);
                ebgSampTaskTodoLogDao.batchAdd(ebgSampTaskTodoLogBoList);
                return result;
            }
            EbgSampTaskBaseVo ebgSampTaskBaseVo = ebgSampTaskBaseDao.selectTaskBaseVoById(reqBo.getTodoId());
            if (ObjectUtils.isNotEmpty(ebgSampTaskBaseVo)) {
                result = getBaseResult(reqBo, ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo,
                    userAccount, result, curRegion, indicatorList);
                ebgSampTaskTodoLogDao.batchAdd(ebgSampTaskTodoLogBoList);
                return result;
            }
        }
        return result;
    }

    private void check(EbgSampTaskTodoLogBo reqBo, List<EbgSampTaskIndicatorResultBo> indicatorList) {
        for (EbgSampTaskIndicatorResultBo indicator : indicatorList) {
            String indicatorResult = indicator.getQuantityResult();
            String indicatorCode = indicator.getIndicator();

            // 3. 查询该指标下的所有分类结果
            TodoRuleTaskResultReqBo categoryQuery = new TodoRuleTaskResultReqBo();
            categoryQuery.setTodoId(reqBo.getTodoId());
            categoryQuery.setIndicator(indicatorCode);
            List<EbgSampTaskCategoryResultBo> categoryList = ebgSampTaskCategoryResultDao.selectTaskResultList(
                categoryQuery);

            checkResult(indicatorResult, indicatorCode, categoryList);
        }
    }

    /**
     * 判断是否类型有不通过指标
     *
     * @param type type
     * @param indicatorList indicatorList
     * @return boolean
     */
    private boolean hasNotPass(List<EbgSampTaskIndicatorResultBo> indicatorList, String type) {
        if (indicatorList == null || indicatorList.isEmpty()) {
            throw new BaseException(QuaErrorCodeEnum.EBG_DATA_NOT_EXIST.getErrorCode());
        }

        boolean flag = false;

        // 3. 遍历检查
        for (EbgSampTaskIndicatorResultBo indicator : indicatorList) {
            String indicatorCode = indicator.getIndicator();
            String sample = CommonUtil.getDictDataExt(DataConstants.ECARE_INDICATORS, indicatorCode, "sample");

            // 只关注 类型
            if (type.equals(sample)) {
                String result = indicator.getQuantityResult();

                if (QualityResultEnum.NOT_PASS.getType().equals(result)) {
                    // 只要发现一个未确认的，直接返回 true，表示“有未确认的”
                    flag = true;
                    break;
                }
            }
        }

        // 4. 遍历结束未发现未确认的指标
        return flag;
    }

    // 判断所有指标类型未申诉
    private boolean checkAllUnappealed(List<EbgSampTaskIndicatorResultBo> indicatorList) {
        if (indicatorList == null || indicatorList.isEmpty()) {
            throw new BaseException(QuaErrorCodeEnum.EBG_DATA_NOT_EXIST.getErrorCode());
        }

        boolean flag = false;

        for (EbgSampTaskIndicatorResultBo indicator : indicatorList) {
            String isAppeal = indicator.getIsAppeal();
            String quantityResult = indicator.getQuantityResult();
            // 如果是不通过
            if (QualityResultEnum.NOT_PASS.getType().equals(quantityResult)) {
                // 已申诉的
                if ("0".equals(isAppeal)) {
                    flag = true;
                    break;
                }
            }
        }

        // 如果发现有已申诉的 指标，返回 false；否则返回 true
        return !flag;
    }

    // 判断所有指标如果有没确认的
    private boolean checkHasUnconfirmed(List<EbgSampTaskIndicatorResultBo> indicatorList) {
        if (indicatorList == null || indicatorList.isEmpty()) {
            throw new BaseException(QuaErrorCodeEnum.EBG_DATA_NOT_EXIST.getErrorCode());
        }

        // 3. 核心逻辑：遍历并判断
        // 假设 "确认" 的条件是：appealConfirm 不为 null 且不为空字符串
        boolean allConfirmed = true;

        for (EbgSampTaskIndicatorResultBo indicator : indicatorList) {
            String quantityResult = indicator.getQuantityResult();
            String appealConfirm = indicator.getAppealConfirm();
            // 如果是不通过
            if (QualityResultEnum.NOT_PASS.getType().equals(quantityResult)) {
                // 未确认的
                if (appealConfirm == null || appealConfirm.trim().isEmpty() || EbgConstants.APPEAL_NOT_CONFIRM.equals(
                    appealConfirm)) {
                    allConfirmed = false;
                    break;
                }
            }
        }
        return !allConfirmed;
    }

    /**
     * 判断指标如果有没确认的
     *
     * @param type type
     * @param indicatorList indicatorList
     * @return boolean
     */
    private boolean checkConfirm(List<EbgSampTaskIndicatorResultBo> indicatorList, String type) {
        if (indicatorList == null || indicatorList.isEmpty()) {
            throw new BaseException(QuaErrorCodeEnum.EBG_DATA_NOT_EXIST.getErrorCode());
        }

        // 3. 核心逻辑：遍历并判断
        // 假设 "确认" 的条件是：appealConfirm 不为 null 且不为空字符串
        boolean allConfirmed = true;

        for (EbgSampTaskIndicatorResultBo indicator : indicatorList) {
            String sample = CommonUtil.getDictDataExt(DataConstants.ECARE_INDICATORS, indicator.getIndicator(),
                "sample");

            // 只关注 类型
            if (Objects.equals(type, sample)) {
                String quantityResult = indicator.getQuantityResult();
                String appealConfirm = indicator.getAppealConfirm();
                // 如果是不通过
                if (QualityResultEnum.NOT_PASS.getType().equals(quantityResult)) {
                    // 未确认的
                    if (appealConfirm == null || appealConfirm.trim().isEmpty() || EbgConstants.APPEAL_NOT_CONFIRM
                        .equals(appealConfirm)) {
                        allConfirmed = false;
                        break;
                    }
                }
            }
        }
        return !allConfirmed;
    }

    private static void checkResult(String indicatorResult, String indicator,
        List<EbgSampTaskCategoryResultBo> categoryList) {
        if (QualityResultEnum.PASS.getType().equals(indicatorResult)
                || QualityResultEnum.NO_CHECK.getType().equals(indicatorResult)) {
            // --- 场景 A: 指标通过 ---
            // 逻辑：如果指标是 PASS，分类结果中不能存在 NOT_PASS
            boolean hasFail = false;
            for (EbgSampTaskCategoryResultBo category : categoryList) {
                if (QualityResultEnum.NOT_PASS.getType().equals(category.getQuantityResult())) {
                    hasFail = true;
                    break;
                }
            }
            if (hasFail) {
                throw new BaseException(QuaErrorCodeEnum.INDICATOR_CATEGORY_ERROR.getErrorCode());
            }
        } else if (QualityResultEnum.NOT_PASS.getType().equals(indicatorResult)) {
            // --- 场景 B: 指标不通过 ---
            // 逻辑：如果指标是 NOT_PASS，分类结果中必须至少有一个 NOT_PASS
            boolean hasFail = false;
            for (EbgSampTaskCategoryResultBo category : categoryList) {
                if (QualityResultEnum.NOT_PASS.getType().equals(category.getQuantityResult())) {
                    hasFail = true;
                    break;
                }
            }

            // 校验失败条件：列表为空 或者 列表中没有一条是不通过的
            if (CollectionUtils.isEmpty(categoryList) || !hasFail) {
                throw new BaseException(QuaErrorCodeEnum.INDICATOR_CATEGORY_ERROR.getErrorCode());
            }
        }
    }

    private int getTodoResult(GetEbgSampTaskTodoCommitReqBo reqBo, EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
                              List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList,
        EbgSampTaskTodoVo ebgSampTaskTodoVo, String userAccount, int result, String curRegion,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        ebgSampTaskTodoLogBo.setBusiType(ebgSampTaskTodoVo.getBusiType());
        ebgSampTaskTodoLogBo.setCheckType(ebgSampTaskTodoVo.getCheckType());

        check(ebgSampTaskTodoLogBo, indicatorList);

        SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo = new SaveEbgSampTaskTodoReqBo();
        saveEbgSampTaskTodoReqBo.setTodoId(ebgSampTaskTodoVo.getTodoId());
        if (EbgSampApprovalTypeEnum.FIRST_REVIEW.getCode().equals(reqBo.getOperateType())) {
            result = getTodoFirstResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo, userAccount,
                curRegion, saveEbgSampTaskTodoReqBo, indicatorList);
        } else if (EbgSampApprovalTypeEnum.APPEAL.getCode().equals(reqBo.getOperateType())) {
            result = getTodoAppealResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo,
                userAccount, curRegion, saveEbgSampTaskTodoReqBo, indicatorList);
        } else if (EbgSampApprovalTypeEnum.FINAL_REVIEW.getCode().equals(reqBo.getOperateType())) {
            result = getTodoFinalResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo, userAccount,
                curRegion, saveEbgSampTaskTodoReqBo);
        }
        return result;
    }

    private int getTodoFinalResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo,
        String userAccount, String curRegion, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo) {
        int result;
        if (!EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus().equals(ebgSampTaskTodoVo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.OPERATE_STATUS_ERROR.getErrorCode());
        }
        if (!userAccount.equals(ebgSampTaskTodoVo.getFinalReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
            .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        saveEbgSampTaskTodoReqBo.setCheckStatus(EbgSampCheckStatusEnum.FINISH.getStatus());
        saveEbgSampTaskTodoReqBo.setCurrentProcessor("");
        saveEbgSampTaskTodoReqBo.setFinalReviewProcessor(userAccount);
        saveEbgSampTaskTodoReqBo.setFinalReviewFinishTime(new Date());
        result = ebgSampTaskTodoDao.updateTaskTodo(saveEbgSampTaskTodoReqBo);
        ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FINAL_REVIEW.getCode());
        String remarkStr = (ebgSampTaskTodoVo.getFinalReviewRemark() == null) ? "" : ebgSampTaskTodoVo
            .getFinalReviewRemark();
        ebgSampTaskTodoLogBo.setOperateContent("操作内容：" + EbgSampOperateTypeEnum.FINAL_REVIEW.getType() + "；复审备注："
            + remarkStr + "；状态改为" + EbgSampCheckStatusEnum.getDescByStatus(saveEbgSampTaskTodoReqBo.getCheckStatus(),
                curRegion));
        ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        return result;
    }

    private int getTodoAppealResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo,
        String userAccount, String curRegion, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        AppealSlot ccrSlot = new AppealSlot(
            ebgSampTaskTodoVo.getCcrProcessor(), EbgConstants.ASS_CCR,
            () -> getCcrResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo, userAccount,
                curRegion, saveEbgSampTaskTodoReqBo, indicatorList));
        AppealSlot cseSlot = new AppealSlot(
            ebgSampTaskTodoVo.getCseProcessor(), EbgConstants.ASS_CSE,
            () -> getCseResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskTodoVo, userAccount,
                curRegion, saveEbgSampTaskTodoReqBo, indicatorList));
        return doAppealResult(userAccount, indicatorList, ebgSampTaskTodoVo.getCheckStatus(),
            ebgSampTaskTodoVo.getCurrentProcessor(), ccrSlot, cseSlot);
    }

    private int getCseResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo,
        String userAccount, String curRegion, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        return doAppealProcess(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, userAccount, curRegion, indicatorList,
            new AppealProcessSupport<>(
                ebgSampTaskTodoVo, saveEbgSampTaskTodoReqBo,
                EbgSampTaskTodoVo::getCheckStatus,
                EbgSampTaskTodoVo::getFinalReviewProcessor,
                SaveEbgSampTaskTodoReqBo::setCheckStatus,
                SaveEbgSampTaskTodoReqBo::setCurrentProcessor,
                SaveEbgSampTaskTodoReqBo::setCseProcessor,
                SaveEbgSampTaskTodoReqBo::setCseAppealFinishTime,
                ebgSampTaskTodoDao::updateTaskTodo,
                EbgSampOperateTypeEnum.CSE_APPEAL,
                EbgSampTaskTodoVo::getCseAppealRemark,
                SaveEbgSampTaskTodoReqBo::getCheckStatus,
                EbgSampTaskTodoVo::getCcrProcessor));
    }

    private int getCcrResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo,
        String userAccount, String curRegion, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        return doAppealProcess(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, userAccount, curRegion, indicatorList,
            new AppealProcessSupport<>(
                ebgSampTaskTodoVo, saveEbgSampTaskTodoReqBo,
                EbgSampTaskTodoVo::getCheckStatus,
                EbgSampTaskTodoVo::getFinalReviewProcessor,
                SaveEbgSampTaskTodoReqBo::setCheckStatus,
                SaveEbgSampTaskTodoReqBo::setCurrentProcessor,
                SaveEbgSampTaskTodoReqBo::setCcrProcessor,
                SaveEbgSampTaskTodoReqBo::setCcrAppealFinishTime,
                ebgSampTaskTodoDao::updateTaskTodo,
                EbgSampOperateTypeEnum.CCR_APPEAL,
                EbgSampTaskTodoVo::getCcrAppealRemark,
                SaveEbgSampTaskTodoReqBo::getCheckStatus,
                EbgSampTaskTodoVo::getCseProcessor));
    }

    private int getTodoFirstResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskTodoVo ebgSampTaskTodoVo,
        String userAccount, String curRegion, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        int result;
        if (!EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(ebgSampTaskTodoVo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.OPERATE_STATUS_ERROR.getErrorCode());
        }
        if (!userAccount.equals(ebgSampTaskTodoVo.getFirstReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
            .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }

        checkAndSave(ebgSampTaskTodoVo, saveEbgSampTaskTodoReqBo, indicatorList);
        getResult(ebgSampTaskTodoVo, saveEbgSampTaskTodoReqBo);
        saveEbgSampTaskTodoReqBo.setFirstReviewProcessor(userAccount);
        saveEbgSampTaskTodoReqBo.setFinalReviewProcessor(userAccount);
        saveEbgSampTaskTodoReqBo.setFirstReviewFinishTime(new Date());
        result = ebgSampTaskTodoDao.updateTaskTodo(saveEbgSampTaskTodoReqBo);
        ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FIRST_REVIEW.getCode());
        String remarkStr = (ebgSampTaskTodoVo.getFirstReviewRemark() == null) ? "" : ebgSampTaskTodoVo
            .getFirstReviewRemark();
        ebgSampTaskTodoLogBo.setOperateContent("操作内容：" + EbgSampOperateTypeEnum.FIRST_REVIEW.getType() + "；初审备注："
            + remarkStr + "；状态改为" + EbgSampCheckStatusEnum.getDescByStatus(saveEbgSampTaskTodoReqBo.getCheckStatus(),
                curRegion));
        ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        return result;
    }

    private void checkAndSave(EbgSampTaskTodoVo ebgSampTaskTodoVo, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        if (hasNotPass(indicatorList, EbgConstants.ASS_CCR)) {
            String ccrLeader = getProcessor(ebgSampTaskTodoVo.getCcrAssessor(), ebgSampTaskTodoVo.getProductLine(),
                "ccrAppellant");
            // 配置字典值 兜底失败 报错
            if (StringUtils.isEmpty(ccrLeader)) {
                throw new BaseException(QuaErrorCodeEnum.CCR_LEADER_ERROR.getErrorCode());
            }
            saveEbgSampTaskTodoReqBo.setCcrProcessor(ccrLeader);
        }
        if (hasNotPass(indicatorList, EbgConstants.ASS_CSE)) {
            String cseLeader = getProcessor(ebgSampTaskTodoVo.getCseAssessor(), ebgSampTaskTodoVo.getProductLine(),
                "cseAppellant");
            // 配置字典值 兜底失败 报错
            if (StringUtils.isEmpty(cseLeader)) {
                throw new BaseException(QuaErrorCodeEnum.CSE_LEADER_ERROR.getErrorCode());
            }
            saveEbgSampTaskTodoReqBo.setCseProcessor(cseLeader);
        }
    }

    private String getProcessor(String assessor, String productLine, String dictKey) {
        if (StringUtils.isEmpty(assessor)) {
            return CommonUtil.getDictDataExt(EbgConstants.EBG_APPELLANT, productLine, dictKey);
        }
        String first = assessor.split(Constants.COMMA)[0];
        String leaderAccount = accountUtil.getLeaderAccount(first);
        return StringUtils.isEmpty(leaderAccount) ? CommonUtil.getDictDataExt(EbgConstants.EBG_APPELLANT, productLine,
            dictKey) : leaderAccount;
    }

    /**
     * 设置状态
     *
     * @param ebgSampTaskTodoVo ebgSampTaskTodoVo
     * @param saveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo
     */
    @Override
    public void getResult(EbgSampTaskTodoVo ebgSampTaskTodoVo, SaveEbgSampTaskTodoReqBo saveEbgSampTaskTodoReqBo) {
        if (QualityResultEnum.PASS.getType().equals(ebgSampTaskTodoVo.getSrRuleResult())) {
            saveEbgSampTaskTodoReqBo.setCheckStatus(EbgSampCheckStatusEnum.FINISH.getStatus());
            saveEbgSampTaskTodoReqBo.setCurrentProcessor("");
        } else {
            saveEbgSampTaskTodoReqBo.setCheckStatus(EbgSampCheckStatusEnum.PENDING_APPEAL.getStatus());
            saveEbgSampTaskTodoReqBo.setCurrentProcessor(mergeProcessors(saveEbgSampTaskTodoReqBo.getCcrProcessor(),
                saveEbgSampTaskTodoReqBo.getCseProcessor()));
        }
    }

    private int getBaseResult(GetEbgSampTaskTodoCommitReqBo reqBo, EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo, String userAccount,
        int result, String curRegion, List<EbgSampTaskIndicatorResultBo> indicatorList) {
        ebgSampTaskTodoLogBo.setBusiType(ebgSampTaskBaseVo.getBusiType());
        ebgSampTaskTodoLogBo.setCheckType(ebgSampTaskBaseVo.getCheckType());

        check(ebgSampTaskTodoLogBo, indicatorList);

        SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo = new SaveEbgSampTaskBaseReqBo();
        saveEbgSampTaskBaseReqBo.setTodoId(ebgSampTaskBaseVo.getTodoId());
        if (EbgSampApprovalTypeEnum.FIRST_REVIEW.getCode().equals(reqBo.getOperateType())) {
            result = getBaseFirstResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo, userAccount,
                curRegion, saveEbgSampTaskBaseReqBo, indicatorList);
        } else if (EbgSampApprovalTypeEnum.APPEAL.getCode().equals(reqBo.getOperateType())) {
            result = getBaseAppealResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo,
                userAccount, curRegion, saveEbgSampTaskBaseReqBo, indicatorList);
        } else if (EbgSampApprovalTypeEnum.FINAL_REVIEW.getCode().equals(reqBo.getOperateType())) {
            result = getBaseFinalResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo, userAccount,
                curRegion, saveEbgSampTaskBaseReqBo);
        }
        return result;
    }

    private int getBaseFinalResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo,
        String userAccount, String curRegion, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo) {
        if (!EbgSampCheckStatusEnum.PENDING_FINAL_REVIEW.getStatus().equals(ebgSampTaskBaseVo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.OPERATE_STATUS_ERROR.getErrorCode());
        }
        if (!userAccount.equals(ebgSampTaskBaseVo.getFinalReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
            .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }
        saveEbgSampTaskBaseReqBo.setCheckStatus(EbgSampCheckStatusEnum.FINISH.getStatus());
        saveEbgSampTaskBaseReqBo.setCurrentProcessor("");
        saveEbgSampTaskBaseReqBo.setFinalReviewProcessor(userAccount);
        saveEbgSampTaskBaseReqBo.setFinalReviewFinishTime(new Date());
        int result = ebgSampTaskBaseDao.updateTaskBase(saveEbgSampTaskBaseReqBo);
        ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FINAL_REVIEW.getCode());
        String remarkStr = (ebgSampTaskBaseVo.getFinalReviewRemark() == null) ? "" : ebgSampTaskBaseVo
            .getFinalReviewRemark();
        ebgSampTaskTodoLogBo.setOperateContent("操作内容：" + EbgSampOperateTypeEnum.FINAL_REVIEW.getType() + "；复审备注："
            + remarkStr + "；状态改为" + EbgSampCheckStatusEnum.getDescByStatus(saveEbgSampTaskBaseReqBo.getCheckStatus(),
                curRegion));
        ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        return result;
    }

    private int getBaseAppealResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo,
        String userAccount, String curRegion, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        AppealSlot pseSlot = new AppealSlot(
            ebgSampTaskBaseVo.getPseAppealProcessor(), EbgConstants.ASS_PSE,
            () -> getPseResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo, userAccount,
                curRegion, saveEbgSampTaskBaseReqBo, indicatorList));
        AppealSlot cseSlot = new AppealSlot(
            ebgSampTaskBaseVo.getCseAppealProcessor(), EbgConstants.ASS_CSE,
            () -> getCseResult(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, ebgSampTaskBaseVo, userAccount,
                curRegion, saveEbgSampTaskBaseReqBo, indicatorList));
        return doAppealResult(userAccount, indicatorList, ebgSampTaskBaseVo.getCheckStatus(),
            ebgSampTaskBaseVo.getCurrentProcessor(), pseSlot, cseSlot);
    }

    private int getCseResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo,
        String userAccount, String curRegion, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        return doAppealProcess(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, userAccount, curRegion, indicatorList,
            new AppealProcessSupport<>(
                ebgSampTaskBaseVo, saveEbgSampTaskBaseReqBo,
                EbgSampTaskBaseVo::getCheckStatus,
                EbgSampTaskBaseVo::getFinalReviewProcessor,
                SaveEbgSampTaskBaseReqBo::setCheckStatus,
                SaveEbgSampTaskBaseReqBo::setCurrentProcessor,
                SaveEbgSampTaskBaseReqBo::setCseAppealProcessor,
                SaveEbgSampTaskBaseReqBo::setCseAppealFinishTime,
                ebgSampTaskBaseDao::updateTaskBase,
                EbgSampOperateTypeEnum.CSE_APPEAL,
                EbgSampTaskBaseVo::getCseAppealRemark,
                SaveEbgSampTaskBaseReqBo::getCheckStatus,
                EbgSampTaskBaseVo::getPseAppealProcessor));
    }

    private int getPseResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo,
        String userAccount, String curRegion, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        return doAppealProcess(ebgSampTaskTodoLogBo, ebgSampTaskTodoLogBoList, userAccount, curRegion, indicatorList,
            new AppealProcessSupport<>(
                ebgSampTaskBaseVo, saveEbgSampTaskBaseReqBo,
                EbgSampTaskBaseVo::getCheckStatus,
                EbgSampTaskBaseVo::getFinalReviewProcessor,
                SaveEbgSampTaskBaseReqBo::setCheckStatus,
                SaveEbgSampTaskBaseReqBo::setCurrentProcessor,
                SaveEbgSampTaskBaseReqBo::setPseAppealProcessor,
                SaveEbgSampTaskBaseReqBo::setPseAppealFinishTime,
                ebgSampTaskBaseDao::updateTaskBase,
                EbgSampOperateTypeEnum.PSE_APPEAL,
                EbgSampTaskBaseVo::getPseAppealRemark,
                SaveEbgSampTaskBaseReqBo::getCheckStatus,
                EbgSampTaskBaseVo::getCseAppealProcessor));
    }

    private int getBaseFirstResult(EbgSampTaskTodoLogBo ebgSampTaskTodoLogBo,
        List<EbgSampTaskTodoLogBo> ebgSampTaskTodoLogBoList, EbgSampTaskBaseVo ebgSampTaskBaseVo,
        String userAccount, String curRegion, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        if (!EbgSampCheckStatusEnum.PENDING_FIRST_REVIEW.getStatus().equals(ebgSampTaskBaseVo.getCheckStatus())) {
            throw new BaseException(QuaErrorCodeEnum.OPERATE_STATUS_ERROR.getErrorCode());
        }
        if (!userAccount.equals(ebgSampTaskBaseVo.getFirstReviewProcessor()) && !RoleUtil.isSysOrModuleAdmin(Objects
            .requireNonNull(UserSessionUtil.getSession()).getUserId())) {
            throw new BaseException(QuaErrorCodeEnum.NOT_AUTH_OPER.getErrorCode());
        }

        checkAndSave(ebgSampTaskBaseVo, saveEbgSampTaskBaseReqBo, indicatorList);

        if (QualityResultEnum.PASS.getType().equals(ebgSampTaskBaseVo.getSrRuleResult())) {
            saveEbgSampTaskBaseReqBo.setCheckStatus(EbgSampCheckStatusEnum.FINISH.getStatus());
            saveEbgSampTaskBaseReqBo.setCurrentProcessor("");
        } else {
            saveEbgSampTaskBaseReqBo.setCheckStatus(EbgSampCheckStatusEnum.PENDING_APPEAL.getStatus());
            saveEbgSampTaskBaseReqBo.setCurrentProcessor(
                    mergeProcessors(saveEbgSampTaskBaseReqBo.getPseAppealProcessor(),
                            saveEbgSampTaskBaseReqBo.getCseAppealProcessor()));
        }
        saveEbgSampTaskBaseReqBo.setFirstReviewProcessor(userAccount);
        saveEbgSampTaskBaseReqBo.setFinalReviewProcessor(userAccount);
        saveEbgSampTaskBaseReqBo.setFirstReviewFinishTime(new Date());
        int result = ebgSampTaskBaseDao.updateTaskBase(saveEbgSampTaskBaseReqBo);
        ebgSampTaskTodoLogBo.setOperateType(EbgSampOperateTypeEnum.FIRST_REVIEW.getCode());
        String remarkStr = (ebgSampTaskBaseVo.getFirstReviewRemark() == null) ? "" : ebgSampTaskBaseVo
            .getFirstReviewRemark();
        ebgSampTaskTodoLogBo.setOperateContent("操作内容：" + EbgSampOperateTypeEnum.FIRST_REVIEW.getType() + "；初审备注："
            + remarkStr + "；状态改为" + EbgSampCheckStatusEnum.getDescByStatus(saveEbgSampTaskBaseReqBo.getCheckStatus(),
                curRegion));
        ebgSampTaskTodoLogBoList.add(ebgSampTaskTodoLogBo);
        return result;
    }

    /**
     * 校验是否 具有责任人
     *
     * @param ebgSampTaskBaseVo 待办信息
     * @param saveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo
     * @param indicatorList indicatorList
     */
    private void checkAndSave(EbgSampTaskBaseVo ebgSampTaskBaseVo, SaveEbgSampTaskBaseReqBo saveEbgSampTaskBaseReqBo,
        List<EbgSampTaskIndicatorResultBo> indicatorList) {
        if (hasNotPass(indicatorList, EbgConstants.ASS_CSE)) {
            String cseLeader = getProcessor(ebgSampTaskBaseVo.getCseAssessor(), ebgSampTaskBaseVo.getProductLine(),
                "cseAppellant");
            // 配置字典值 兜底失败 报错
            if (StringUtils.isEmpty(cseLeader)) {
                throw new BaseException(QuaErrorCodeEnum.CSE_LEADER_ERROR.getErrorCode());
            }
            saveEbgSampTaskBaseReqBo.setCseAppealProcessor(cseLeader);
        }
        if (hasNotPass(indicatorList, EbgConstants.ASS_PSE)) {
            String pseLeader = CommonUtil.getDictDataExt(EbgConstants.EBG_APPELLANT, ebgSampTaskBaseVo.getProductLine(),
                "pseAppellant");
            if (StringUtils.isEmpty(pseLeader)) {
                throw new BaseException(QuaErrorCodeEnum.PSE_LEADER_ERROR.getErrorCode());
            } else {
                saveEbgSampTaskBaseReqBo.setPseAppealProcessor(pseLeader);
            }
        }
    }
}
```
