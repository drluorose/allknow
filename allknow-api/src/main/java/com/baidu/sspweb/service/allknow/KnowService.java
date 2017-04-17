/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow;

import java.util.List;

/**
 * Created by whimsy on 15/10/24.
 */
public interface KnowService {

    /**
     * 根据 用户ID + keyword
     * 进行 like %keyword% 查询， 得到 delivery Ids
     * @param userId 用户ID
     * @param keyword 查询关键字
     * @return 符合条件广告ID集合
     */
    List<Long> findDeliveryIds(Long userId, String keyword);
}
