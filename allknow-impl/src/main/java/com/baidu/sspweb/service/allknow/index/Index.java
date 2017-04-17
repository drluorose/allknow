/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow.index;

import java.util.List;

import com.google.common.collect.Multimap;

/**
 * 索引的接口定义
 *
 * Created by whimsy on 15/10/24.
 */
public interface Index {
    /**
     * 批量导入数据  <br/>
     * <br/>
     * userId ->  list of PairEntry  <br/>
     * PairEntry = [ id -> content ]  <br/>
     *
     * @param datas 批量导入数据， 数据格式如上
     */
    void load(Multimap<Long, PairEntry> datas);

    /**
     *
     * 插入一个条目  <br/>
     * user -> [ id -> content ]  <br/>
     *
     * @param userId  条目用户ID
     * @param id      条目ID
     * @param content  条目内容
     */

    void push(Long userId, Long id, String content);

    /**
     * 查询userId的， content 中包含 keyword 的条目  <br/>
     *
     * @param userId  用户ID
     * @param keyword 查询关键字
     * @return  符合查询条件的条目
     */
    List<PairEntry> query(Long userId, String keyword);
}
