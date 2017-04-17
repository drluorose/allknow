/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.sspweb.service.allknow;

import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baidu.sspweb.service.allknow.index.PairEntry;
import com.baidu.sspweb.service.allknow.index.impl.DeliveryIndex;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Created by whimsy on 15/10/24.
 */
@Service
public class KnowServiceImpl implements KnowService {

    @Autowired
    DeliveryIndex deliveryIndex;

    @PostConstruct
    public void initIndex() {
        // TODO
    }

    @Override
    public List<Long> findDeliveryIds(Long userId, String keyword) {
        List<PairEntry> res = deliveryIndex.query(userId, keyword);

        return Lists.newArrayList(Lists.transform(res, new Function<PairEntry, Long>() {
            @Override
            public Long apply(PairEntry input) {
                return input.getId();
            }
        }));
    }
}
