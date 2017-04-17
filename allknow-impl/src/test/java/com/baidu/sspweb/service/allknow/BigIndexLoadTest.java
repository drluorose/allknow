/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.sspweb.service.allknow.index.PairEntry;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

/**
 * 大数据集合正确性 size = 20w
 *      batchLoad
 * Created by whimsy on 15/10/24.
 */

@RunWith(Parameterized.class)
public class BigIndexLoadTest extends AbstractDeliveryIndexTest {
    static Logger logger = LoggerFactory.getLogger(BigIndexLoadTest.class);

    @Parameterized.Parameters(name = "{index} query = {0}, expetedSize = {1}")
    public static Collection<Object[]> data() {
        return Lists.newArrayList(new Object[][] {
                {"地区", 211},
                {"2015", 78460},
                {"删除", 68286},
                {"北京", 14145},
                {"福建", 11664},
                {"男科", 5227},
                {"2015-07-09", 276},
                {"-", 209998},
                {"不存在", 0}
        });
    }

    private String query;
    private int expectedSize;

    public BigIndexLoadTest(String query, int expectedSize) {
        this.query = query;
        this.expectedSize = expectedSize;
    }

    @BeforeClass
    public static void setUpEnv() {
        loadEnv("delivery_5106311_data.txt");
    }

    @Test
    public void test() {

        Stopwatch watch = Stopwatch.createStarted();
        List<PairEntry> ids = deliveryIndex.query(USER_ID, query);
        assertThat(ids.size(), is(expectedSize));

        infoWarnThrow(query, expectedSize, watch.elapsed(TimeUnit.MILLISECONDS));

    }



}
