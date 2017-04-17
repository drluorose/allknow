```
此服务是一个内存的全文索引, **Double Buffer Data** 的实现

插入 list of (id -> keyword)
支持高效查询keyword->Ids的模糊查询
比如， 查询广告名字包含"全国"的广告IDs

核心算法是后缀索引(Manber's algorithm)
通过数据分区和"热交换"，来避免静态索引带来的重建索引的长时间阻塞


其中:
Manber.java :  后缀索引核心算法
KWIC.java   :  全文索引的基本接口和方法
HotSwapKWIC.java  : 组合KWIC， 得到交换的索引
MonitorIndex.java : 组合HotSwapKWIC, 启动后台替换索引的线程

BigIndexLoadTest, MediumIndexTest, SmallIndexTest 对正确性验证
SwitchIndexProfilingTest 验证索引替换不影响查询效率

```

# All KNOW

注意， 该实现为公司内部申请 Goodcooder 认证时的作品，主要考虑实现技巧 **未在生产环境使用过** 


## 0. 背景

1. 广告， 物料， 订单， 广告位等模块均有根据 ID or Name 模糊匹配(sql like %abc%)
2. 此外， 在服务化的实践中， 我们定义查询接口会包含XIdsm 比如广告的查询接口会包含orderIds

从逻辑上来讲， 这类查询可以归纳为keyword -> ids, 可以进行统一。从查询效率上来讲， like 语句的效率可以优化

## 1. 概述

1. 实现内存索引， 用后缀数组对keyword 进行全文索引，支持对like %% 语句的模糊
2. 查询效率O(log (text length) ＋ 结果集合大小) text length = 所有keyword的总长度

## 2. 实现方式

将业务数据库的数据同步至内存中， 在内存中建立以二维索引， 用户ID索引后缀索引
支持索引的热替换

### 2.1 数据的读入

1. operate_log
	*	业务端写Operate_log
		*	缺点, 耦合业务	
	*	数据库trigger 
		* 	数据库trigger 难以维护， 不建议使用
2. fountain获取变更 [类似canel](https://github.com/alibaba/canal)


### 2.2 后缀索引

http://algs4.cs.princeton.edu/63suffix/

### 2.3 索引热替换

对于数据变更， 存储到一个增量， 进行累计。

另一监控线程定时检查索引列表， 对于增量超过阈值的索引重建索引。 

这期间， 查询仍能够访问旧的索引， 当索引重建完成。 监控线程将旧的索引替换成新的

### 2.4 初始化

第一次启动索引是， 读出所有数据， 一次性建立索引， 而不多次触发索引热替换.


## 3. 效能分析

1. 索引构建时间复杂度
	- N = text length O(N*log N)
	- 实验得20w 广告的name 构建索引时间为11s
2. 插入复杂度
	- 插入的实现方式为累计到1000条， 从新建立索引。 并且令起单独线程替换索引。
3. 查询复杂度
	- 单纯后缀索引的复杂度为
	- N = text length
	- M = size of resulst set
	- O(M + log N)


另外还有查询第一维userId索引的花费

上层索引为userId， 根据ID， 组装返回结果集的开销


