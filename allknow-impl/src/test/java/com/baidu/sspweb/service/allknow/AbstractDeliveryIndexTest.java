/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baidu.sspweb.service.allknow.index.PairEntry;
import com.baidu.sspweb.service.allknow.index.impl.DeliveryIndex;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;

/**
 * Created by whimsy on 15/10/26.
 */
public abstract  class AbstractDeliveryIndexTest {

    protected static final Long USER_ID = 5106311L;

    // 每过5000ms增量会合并到基准，所以过了WAIT_INDEX_SLEEP_TIMEms，验证索引的效率
    protected static final Long WAIT_INDEX_SLEEP_TIME = 6000L;


    static Logger logger = LoggerFactory.getLogger(AbstractDeliveryIndexTest.class);

    static DeliveryIndex deliveryIndex = null;

    protected static Multimap<Long, PairEntry> getData(String filename) {
        URL url = Resources.getResource(filename);

        try {
            List<String> entries =  Resources.readLines(url, Charsets.UTF_8);

            Multimap<Long, PairEntry> datas = ArrayListMultimap.create();

            for (String entry : entries) {
                String [] values = entry.split("\t");

                datas.put(Long.valueOf(values[0]),
                        new PairEntry(Long.valueOf(values[1]), values[2]));
            }


            return datas;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    protected static void loadEnv(String filename) {

        deliveryIndex = new DeliveryIndex();
        Multimap<Long, PairEntry> datas = getData(filename);

        Stopwatch watch = Stopwatch.createStarted();
        deliveryIndex.load(datas);
        logger.info("Construct Index cost {} ms", watch.elapsed(TimeUnit.MILLISECONDS));

    }


    protected static List<String[]> getRawData(String filename) {
        List<String[]> res = Lists.newArrayList();
        URL url = Resources.getResource(filename);

        try {
            List<String> entries =  Resources.readLines(url, Charsets.UTF_8);
            for (String entry : entries) {
                String [] values = entry.split("\t");
                res.add(values);

            }
            return res;

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void insertEnv(String filename) {
        deliveryIndex = new DeliveryIndex();

        List<String[]> datas = getRawData(filename);
        for (String[] values : datas) {
            deliveryIndex.push(Long.parseLong(values[0]),
                    Long.parseLong(values[1]),
                    values[2]);
        }

    }


    protected static void infoWarnThrow(String query, Integer expectedSize, long timeUsed) {

        logger.info("query = {} - size = {} |  used  {} ms\n",
                query, expectedSize,
                timeUsed);

        if (timeUsed > 300) {
            logger.warn("query = {} used more than 500ms");
        }

        if (timeUsed > 1000) {
            throw new RuntimeException("Consume to much time");
        }

    }
}
