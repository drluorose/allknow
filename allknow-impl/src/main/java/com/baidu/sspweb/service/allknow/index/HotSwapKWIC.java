/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.sspweb.service.allknow.exception.IndexParamException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 热替换索引<br/>
 * <br/>
 * 重建索引耗时长， 为了<b>不阻塞用户的查询请求</b>。 <br/>
 * 0. 数据分段(suffixIndexes + plainIndexes) 累计到足够多变化后， 再重建索引
 * 1. 将重建索引任务交给后台线程。 <br/>
 * 2. 当后台线程重建索引时， 用户线程继续访问旧的的索引。  <br/>
 * 3. 重建索引完成后， 替换索引 <br/>
 * <br/>
 *
 * @see com.baidu.sspweb.service.allknow.index.MonitoredIndex
 *
 * Created by whimsy on 15/10/24.
 */
public class HotSwapKWIC {

    static Logger logger = LoggerFactory.getLogger(HotSwapKWIC.class);

    /**
     * 普通索引的长度限制， 当长度大于阈值的时候，
     * PLAIN_INDEX_THRESHOLD 重构索引
     *
     * 需要通过实验部署调整该数值
     */
    private static final int PLAIN_INDEX_THRESHOLD = 1000;

    /**
     *  当普通索引长度超过阈值，  后台线程无法及时重建索引, 即
     *  普通索引长度 PLAIN_INDEX_THRESHOLD * TOO_LONG_FACTOR
     *  打出报警日志， 并且抛出异常
     *
     *  需要通过实验部署调整该数值
     */
    private static final int TOO_LONG_FACTOR = 2;

    /**
     * 后缀索引, 构建两个用以热替换
     */
    private KWIC [] suffixIndexes =  new KWIC[2];

    /**
     * 增量存储索引， 构造两个用以热替换
     */
    private Map<Long, PairEntry> [] plainIndexes = new TreeMap[2];

    /**
     * 当前活跃的索引下标
     */
    private int activeIndex = 0;

    /**
     * 增量存储索引锁
     */
    private ReadWriteLock plainIndexLock = new ReentrantReadWriteLock();

    /**
     * 索引替换锁
     */
    private Lock switchIndexLock = new ReentrantLock();

    public HotSwapKWIC() {
        suffixIndexes[activeIndex] = new KWIC(new ArrayList<PairEntry>());

        plainIndexes[activeIndex] = Maps.newTreeMap();
        plainIndexes[other()] = Maps.newTreeMap();

    }

    /**
     * 获取 模糊匹配 query 的数据集
     * @param query 查询关键字
     * @return 模糊匹配的数据集 列表
     *
     * @throws java.lang.RuntimeException 索引参数设置不合理时
     */
    public List<PairEntry> find(String query) {
        // get base
        List<PairEntry> res = suffixIndexes[activeIndex].find(query);

        // updated by incremental
        Map<Long, PairEntry> resMap = new TreeMap<Long, PairEntry>();
        for (PairEntry entry : res) {
            resMap.put(entry.getId(), entry);
        }

        logger.info("Get {} from suffix index", res.size());
        if (plainIndexes[activeIndex].size() >= PLAIN_INDEX_THRESHOLD * TOO_LONG_FACTOR) {

            logger.error("size >= PLAIN_INDEX_THRESHOLD 的时候应该进行重建索引\n"
                    + "size >= 2 * PLAIN_INDEX_THRESHOLD 说明当前参数设置不合理, 热替换机制不能完成\n"
                    + "尝试\n"
                    + "    1. 增大 PLAIN_INDEX_THRESHOLD\n"
                    + "    2. 增大 TOLL_LONG_FACTOR\n"
                    + "    3. 增加重建索引监控线程数量\n"
                    + "    4. 增大重建索引线程运行频率\n");

            /**
             * size >= PLAIN_INDEX_THRESHOLD 的时候应该进行重建索引
             * size >= 2 * PLAIN_INDEX_THRESHOLD 说明当前参数设置不合理, 热替换机制不能完成
             * 尝试
             *      1. 增大 PLAIN_INDEX_THRESHOLD
             *      2. 增大 TOLL_LONG_FACTOR
             *      3. 增加重建索引监控线程数量
             *      4. 增大重建索引线程运行频率
             */
            throw new IndexParamException("There are troubles to reindex");
        }
        logger.info("PlainIndex size {}", plainIndexes[activeIndex].size());

        plainIndexLock.readLock().lock();
        try {
            for (Map.Entry<Long, PairEntry> entry : plainIndexes[activeIndex].entrySet()) {
                if (!entry.getValue().subMatch(query)) {
                    resMap.remove(entry.getKey());
                } else {
                    resMap.put(entry.getKey(), entry.getValue());
                }
            }
        } finally {
            plainIndexLock.readLock().unlock();
        }

        return Lists.newArrayList(resMap.values());

    }

    /**
     * 插入数据
     *
     * @param entry 数据条目
     */

    public void insertOrUpdate(PairEntry entry) {

        plainIndexLock.writeLock().lock();
        try {
            plainIndexes[activeIndex].put(entry.getId(), entry);
            plainIndexes[other()].put(entry.getId(), entry);
        } finally {
            plainIndexLock.writeLock().unlock();
        }

        // this can be user behavior, so we let the monitor thread to do reindex.
    }

    /**
     * 尝试重建索引
     */
    public void tryReIndex() {

        if (plainIndexes[activeIndex].size() >= PLAIN_INDEX_THRESHOLD) {
            if (switchIndexLock.tryLock()) {
                try {
                    if (plainIndexes[activeIndex].size() >= PLAIN_INDEX_THRESHOLD) {
                        logger.info("ThreadId = {}  Reindexing started", Thread.currentThread().getId());
                        putAndReConstruct();
                        logger.info("ThreadId = {}  Reindexing ended", Thread.currentThread().getId());
                    }
                } finally {
                    switchIndexLock.unlock();
                }
            }
        }
    }

    /**
     * 批量导入
     * @param entries 数据集合
     */
    public void batchLoad(Collection<PairEntry> entries) {
        plainIndexLock.writeLock().lock();
        try {
            for (PairEntry entry : entries) {
                plainIndexes[activeIndex].put(entry.getId(), entry);
                plainIndexes[other()].put(entry.getId(), entry);
            }
        } finally {
            plainIndexLock.writeLock().unlock();
        }

        // bath load is sys behavior, we can block it.
        tryReIndex();

    }

    /**
     *  合并plainIndex 到后缀索引， 重建索引
     */
    private void putAndReConstruct() {


        plainIndexLock.writeLock().lock();
        List<PairEntry> entries = null;
        try {

            entries = Lists.newArrayList(plainIndexes[other()].values());
            plainIndexes[other()] = Maps.newTreeMap();
        } finally {
            plainIndexLock.writeLock().unlock();
        }

        // 耗时操作， 并不获取plainIndexLock
        suffixIndexes[other()] = new KWIC(suffixIndexes[activeIndex], entries);

        plainIndexLock.writeLock().lock();
        try {
            // 切换索引
            activeIndex = other();
            // 同步plain index
            plainIndexes[other()] = new TreeMap<Long, PairEntry>(plainIndexes[activeIndex]);
        } finally {
            plainIndexLock.writeLock().unlock();
        }

    }

    /**
     * @return 交替索引中， 不活跃的索引
     */
    private int other() {
        return 1 - activeIndex;
    }

}
