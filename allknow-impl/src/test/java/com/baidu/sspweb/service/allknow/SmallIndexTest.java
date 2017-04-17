/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.sspweb.service.allknow.index.PairEntry;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

/**
 * 中等数据集合正确性 size = 15
 *      batchLoad 一次性批量导入
 *      insertEnv 单条插入
 * Created by whimsy on 15/10/24.
 */

@RunWith(Parameterized.class)
public class SmallIndexTest extends AbstractDeliveryIndexTest {
    Logger logger = LoggerFactory.getLogger(SmallIndexTest.class);


    @Parameterized.Parameters(name = "{index} query = {0}, expetedSize = {1}")
    public static Collection<Object[]> data() {
        return Lists.newArrayList(new Object[][] {
                {"地区", 1},
                {"不存在", 0}

        });
    }



    private String query;
    private int expectedSize;

    public SmallIndexTest(String query, int expectedSize) {
        this.query = query;
        this.expectedSize = expectedSize;
    }

    @Test
    public void testLoadEnv() {

        loadEnv("delivery_5106311_data_short.txt");

        Stopwatch watch = Stopwatch.createStarted();

        List<PairEntry> ids = deliveryIndex.query(USER_ID, query);
        assertThat(ids.size(), is(expectedSize));

        infoWarnThrow(query, expectedSize, watch.elapsed(TimeUnit.MILLISECONDS));

    }

    @Test
    public void testInsertEnv() {
        insertEnv("delivery_5106311_data_short.txt");

        Stopwatch watch = Stopwatch.createStarted();

        List<PairEntry> ids = deliveryIndex.query(USER_ID, query);
        assertThat(ids.size(), is(expectedSize));

        infoWarnThrow(query, expectedSize, watch.elapsed(TimeUnit.MILLISECONDS));
    }



}
