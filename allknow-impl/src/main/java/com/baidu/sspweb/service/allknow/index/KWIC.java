/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow.index;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.baidu.unbiz.common.Assert;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.princeton.cs.algs4.Manber;

/**
 * 包装Manber算法 提供简单的接口 <br>
 *     1. 查询接口 <br/>
 *          根据keyword, 找出符合like %keyword% 的所有数据 <br/>
 *     2. 插入接口(重新构建索引) <br/>
 *          插入新的数据集合， 合并（更新或新增）后重新构建索引 <br/>
 *
 * <br/>
 * Created by whimsy on 9/5/14.
 */
public class KWIC {

    /**
     * 后缀数组的核心算法
     */
    private Manber manber;

    /**
     * key : keyword 在 Manber 索引字串中的开始下标
     *
     * 可通过该manber计算得到的id转化为用户数据条目
     *
     * @see #find
     */
    private TreeMap<Integer, PairEntry> dict = new TreeMap<Integer, PairEntry>();

    /**
     * 存入后缀索引内数据的Map形式的保存, 用来加入新的数据重新构建索引时， 合并相同ID <br/>
     * 在复制索引时需要复制该值 <br/>
     *
     * indexLock控制了并发量， stash无线程安全问题 <br/>
     *
     * @see #putAndReConstruct
     * @see #KWIC
     */
    private TreeMap<Long, PairEntry> stash = Maps.newTreeMap();

    /**
     * 索引的锁
     *
     * manber 是后缀索引， <br/>
     * dict 是本类对外接口的辅助数据， 完成从Manber下标->数据条目的映射 <br/>
     *
     * 1. manber + dict 构成了KWIC <br/>
     * 2. stash 数据易读取的存储方式， 同时保证ID相同被合并, 方便重构索引 <br/>
     *
     * manber + dict + stash 三者在存取上一致， 用同一个锁indexLock 来控制竞争状态， 保证数据的一致性。
     *
     */
    private ReadWriteLock indexLock = new ReentrantReadWriteLock();

    /**
     * 用数据集合构建索引
     * @param pairEntries  数据集合 NotNull
     */
    public KWIC(Collection<PairEntry> pairEntries) {

        Assert.assertNotNull(pairEntries);

        indexLock.writeLock().lock();
        try {
            putAndReConstruct(pairEntries);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 旧的索引 + 新增数据集合 构造新的索引
     * @param that  旧索引 NotNull
     * @param pairEntries  新的数据集合 NotNull
     */
    public KWIC(KWIC that, Collection<PairEntry> pairEntries) {
        Assert.assertNotNull(that);
        Assert.assertNotNull(pairEntries);

        indexLock.writeLock().lock();
        try {
            this.stash = (TreeMap<Long, PairEntry>) that.stash.clone();
            putAndReConstruct(pairEntries);
        } finally {
            indexLock.writeLock().unlock();
        }

    }

    /**
     * 根据keyword, 找出符合like %keyword% 的所有数据
     *
     * @param query 查询关键字
     * @return 满足条件的数据列表
     */
    public List<PairEntry> find(String query) {

        Set<PairEntry> res = Sets.newTreeSet();

        for (Integer index : manber.findPrefixMatch(query)) {
            res.add(dict.floorEntry(index).getValue());
        }

        return Lists.newArrayList(res);

    }

    /**
     * 插入新的数据集合， 合并（更新或新增）后重新构建索引
     * @param pairEntries 数据集合
     */
    public void putAndReConstruct(Collection<PairEntry> pairEntries) {
        int total = 0;

        // start sentry
        StringBuilder sb = new StringBuilder(Manber.START_SENTRY);

        for (PairEntry entry : pairEntries) {
            stash.put(entry.getId(), entry);
        }

        for (PairEntry entry : stash.values()) {
            dict.put(total, entry);
            sb.append(entry.getContent()).append(Manber.WORD_SEPARATOR);  // specail seperator
            total += entry.getContent().length() + 1;
        }

        // end sentry
        sb = sb.append(Manber.END_SENTRY);

        manber = new Manber(sb.toString());
    }
}
