/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow;

import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.util.MatcherAssertionErrors.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

/**
 * 验证热切换索引 不影响用户查询
 * 计算:
 *      1. 索引重建前              baseIndex = 20w   plainIndex = 0
 *      2. 插入批量数据重索引前      baseIndex = 20w  plainIndex = 1001
 *      3. 插入批量数据索引中        baseIndex = 20w  plainIndex = 1001 （重建索引中, 访问旧的的索引)
 *      4. 重建索引后                baseIndex = 20w+ plainIndex = 0
 *
 *  验证统计运行时间， maxTime / minTime < 1.5
 * Created by whimsy on 15/10/26.
 */
@RunWith(Parameterized.class)
public class SwitchIndexProfilingTest extends AbstractDeliveryIndexTest {

    private static final int PLAIN_INDEX_THRESHOLD = 1000;
    private static final double TOLERANCE_FACTOR = 1.5;

    @Parameterized.Parameters(name = "{index} query = {0}, expetedSize = {1}")
    public static Collection<Object[]> data() {
        return Lists.newArrayList(new Object[][] {
                {"地区", 211},
        });
    }


    private String query;
    private int expectedSize;

    public SwitchIndexProfilingTest(String query, int expectedSize) {
        this.query = query;
        this.expectedSize = expectedSize;
    }


    @BeforeClass
    public static void setUpEnv() {
        loadEnv("delivery_5106311_data.txt");
    }


    private long minTime = Long.MAX_VALUE;
    private long maxTime = Long.MIN_VALUE;

    private void updateTime() {
        Stopwatch watch = Stopwatch.createStarted();
        deliveryIndex.query(USER_ID, query);
        long time = watch.elapsed(TimeUnit.MILLISECONDS);
        infoWarnThrow(query, expectedSize, time);
        minTime = Math.min(minTime, time);
        maxTime = Math.max(maxTime, time);
    }

    @Test
    public void test() throws InterruptedException {
        List<String[]> datas = getRawData("delivery_5106311_data.txt");

        int dataPtr = 0;

        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        // 1. before reindex
        updateTime();

        // (i) batch insert, the index is qualified for reindexing
        for (int i = 0; i <= PLAIN_INDEX_THRESHOLD + 1; ++i) {
            String [] values = datas.get(dataPtr++);
            deliveryIndex.push(Long.parseLong(values[0]), Long.parseLong(values[1]), values[2]);
        }

        // 2. right after insert
        updateTime();

        // (ii)  wait for monitor thread to start indexing
        Thread.sleep(WAIT_INDEX_SLEEP_TIME);


        // 3. during reindexing
        updateTime();

        // (iii) reindex fininshed
        Thread.sleep(WAIT_INDEX_SLEEP_TIME * 3);

        // 4. after reindexing
        updateTime();


        assertThat(maxTime * 1.0 / minTime, lessThan(TOLERANCE_FACTOR));


    }

}
