/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow.index;

import org.springframework.util.Assert;

/**
 * 数据BO
 *
 * Created by whimsy on 15/10/24.
 */
public class PairEntry implements  Comparable<PairEntry> {

    /**
     * 实体ID
     */
    private Long id;

    /**
     * yao
     */
    private String content;

    public PairEntry(Long id, String content) {
        this.id = id;
        this.content = content;
    }

    /**
     * content 子串匹配
     *
     * @param query 查询的字串
     * @return true  如果匹配
     *         false 如果不匹配
     */
    public boolean subMatch(String query) {
        Assert.notNull(content);
        return this.content.contains(query);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public int compareTo(PairEntry o) {
        return this.id.compareTo(o.getId());
    }
}