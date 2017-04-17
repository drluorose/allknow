/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow.index;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.unbiz.common.Assert;
import com.google.common.collect.Multimap;

/**
 * 启动监控线程 负责热替换任务
 *
 *  MonitorIndex -> HotSwapKWIC -> KWIC -> Manber
 *
 * Created by whimsy on 15/10/25.
 */
public class MonitoredIndex implements Index {


    private static Logger logger = LoggerFactory.getLogger(MonitoredIndex.class);
    /**
     * 二维索引, 第一纬度UserId, 第二纬度为实现keyword -> Ids的高效查询的后缀索引
     * userId -> suffixIndex
     */
    private final Map<Long, HotSwapKWIC> coreIndex = new HashMap<Long, HotSwapKWIC>();

    /**
     * 1. 解决HashMap线程不安全问题
     * 2. push & load 写锁， query & ReIndex 读锁, 避免竞争
     * 避免“并发修改”
     */
    private ReadWriteLock indexMapLock = new ReentrantReadWriteLock();

    /**
     * 线程池的大小
     */
    private static final int THREAD_POOL_SIZE = 2;

    /**
     * 线程池
     */
    ScheduledExecutorService service = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);

    /**
     * 第一个监控线程开启的时间
     */
    private static final long MONITOR_INITIAL_DELAY = 5L;

    /**
     * 监控线程启动间隔
     */
    private static final long MONITOR_PERIOD = 5L;

    public MonitoredIndex() {

        ReIndexTask task = new ReIndexTask();
        service.scheduleAtFixedRate(task, MONITOR_INITIAL_DELAY,
                MONITOR_PERIOD, TimeUnit.SECONDS);
    }

    /**
     * 防止tomcat 无法正常关闭， 需要关闭线程池
     */
    public void closeThreadPool() {
        service.shutdownNow(); // Cancel currently executing tasks
        if (!service.isTerminated()) {
            logger.error("Pool did not terminate");
        }
    }

    /**
     *  遍历所有用户的索引， 尝试重建索引
     */
    private class ReIndexTask implements  Runnable {
        @Override
        public void run() {
            try {
                indexMapLock.readLock().lock();
                try {
                    for (HotSwapKWIC kwic : coreIndex.values()) {
                        kwic.tryReIndex();
                    }
                } finally {
                    indexMapLock.readLock().unlock();
                }
            } catch (Exception e) {
                logger.error("This shouldn't happen.", e);
            }

        }
    }

    /**
     * 多线程下 在同步状态进行批量倒入 <br/>
     * {@inheritDoc}
     */
    public void load(Multimap<Long, PairEntry> datas) {

        for (PairEntry entry : datas.values()) {
            Assert.assertNotNull(entry.getContent());
        }

        for (Long userId : datas.keySet()) {

            indexMapLock.writeLock().lock();
            try {
                if (coreIndex.get(userId) == null) {
                    HotSwapKWIC kwic = new HotSwapKWIC();

                    coreIndex.put(userId, kwic);
                }
            } finally {
                indexMapLock.writeLock().unlock();
            }

            indexMapLock.readLock().lock();


            HotSwapKWIC index = null;

            indexMapLock.readLock().lock();
            try {
                index = coreIndex.get(userId);
            } finally {
                indexMapLock.readLock().unlock();
            }

            index.batchLoad(datas.get(userId));
        }
    }

    /**
     * 多线程下 同步状态插入 <br/>
     *
     * {@inheritDoc}
     */
    public void push(Long userId, Long id, String content) {
        Assert.assertNotNull(content);

        indexMapLock.writeLock().lock();
        try {
            if (coreIndex.get(userId) == null) {
                HotSwapKWIC index = new HotSwapKWIC();
                coreIndex.put(userId, index);
            }
        } finally {
            indexMapLock.writeLock().unlock();
        }


        HotSwapKWIC index = null;

        indexMapLock.readLock().lock();
        try {
            index = coreIndex.get(userId);
        } finally {
            indexMapLock.readLock().unlock();
        }

        index.insertOrUpdate(new PairEntry(id, content));
    }

    /**
     * 多线程下， 在同步状态下进行检索 <br/>
     *
     * {@inheritDoc}
     */
    public List<PairEntry> query(Long userId, String keyword) {
        HotSwapKWIC index = null;
        indexMapLock.readLock().lock();
        try {
            index = coreIndex.get(userId);
        } finally {
            indexMapLock.readLock().unlock();
        }
        if (index == null) {
            logger.warn("UserId = {}, don't have index yet. keyword = {}", userId, keyword);
            return Collections.EMPTY_LIST;
        } else {
            return index.find(keyword);
        }
    }

}
