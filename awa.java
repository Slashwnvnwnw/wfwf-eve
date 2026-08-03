/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

import java.util.concurrent.locks.ReentrantLock;

/**
 * 功能描述
 *
 * @author LWX1535093
 * @since 2026-07-14
 */
public class awa {
    private Integer probeIO;

    private void fun(Integer probeIO) {
        ReentrantLock reentrantLock = new ReentrantLock();
        reentrantLock.lock();

        probeIO = 1;

        reentrantLock.unlock();
    }
}
