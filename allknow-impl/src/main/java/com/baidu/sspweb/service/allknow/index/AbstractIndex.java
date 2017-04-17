/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow.index;

import java.util.List;

import org.springframework.util.Assert;

import com.google.common.collect.Multimap;

/**
 * 具体业务索引实现拓展该类
 *
 * 广告索引
 * @see com.baidu.sspweb.service.allknow.index.impl.DeliveryIndex
 *
 * Created by whimsy on 15/10/24.
 */
public abstract class AbstractIndex implements Index {

    /**
     * 组合后台线程监控的热替换索引
     */
    protected MonitoredIndex monitoredIndex = new MonitoredIndex();

    /**
     * In order to shutdown gracefully, need to call
     * this method to release thread pool.
     */
    public void close() {
        monitoredIndex.closeThreadPool();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(Multimap<Long, PairEntry> datas) {
        Assert.notNull(datas);

        monitoredIndex.load(datas);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void push(Long userId, Long id, String content) {
        Assert.notNull(userId);
        Assert.notNull(id);
        Assert.notNull(content);

        monitoredIndex.push(userId, id, content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PairEntry> query(Long userId, String keyword) {
        Assert.notNull(userId);
        Assert.notNull(keyword);

        return monitoredIndex.query(userId, keyword);
    }


}
