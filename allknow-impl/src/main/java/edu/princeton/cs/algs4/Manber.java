/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package edu.princeton.cs.algs4;

import java.util.ArrayList;
import java.util.List;

/*************************************************************************
 *  <b>Code revised from
 *  <a href = "http://algs4.cs.princeton.edu/63suffix/Manber.java.html">
 *    Sedgewick Alogoriths </a> </b> <br>
 *
 *  <br/>
 *  核心索引算法 <br/>
 *  1. 构造索引：<br/>
 *      Manber(String text) 对text字符串构建后缀索引<br/>
 *  <br/>
 *  2. 查询 <br/>
 *      索引构建后可以用 findPrefixMatch(String keyword)<br/>
 *      快速找到匹配keyword的所有下标IDs, 对于每个id有<br/>
 *      text.substring(id, id + keyword.length()) 等于 keyword <br/>
 *  <br/>
 *
 *
 *
 *  Reads a text corpus from stdin and suffix sorts it in subquadratic <br/>
 *  time using a variant of Manber's algorithm. <br/>
 *
 *  NOTE: I THINK THIS IS CYCLIC SUFFIX SORTING <br/>
 *
 *************************************************************************/

public class Manber {

    private static final int NUM_OF_CHAR = 65536; // support chinese character

    public static final String WORD_SEPARATOR = "\uffff";
    public static final String START_SENTRY = "\u0000";
    public static final String END_SENTRY = "\uffff\uffff";

    private int length;               // length of input string
    private String text;         // input text
    private int[] index;         // offset of ith string in order
    private int[] rank;          // rank of ith string
    private int[] newrank;       // rank of ith string (temporary)
    private int offset;

    public Manber(String s) {

        length = s.length();
        text = s;
        index   = new int[length + 1];
        rank    = new int[length + 1];
        newrank = new int[length + 1];

        // sentinels
        index[length] = length;
        rank[length] = -1;

        msd();
        doit();
    }


    /**
     * @param query  查询关键字
     * @return 大于等于query的第一个元素的rank, 也就是可能前缀为 query 起始rank
     */
    private int floor(String query) {

        int left = 0;
        int right = length;
        while (left < right) {
            int mid = (left + right) / 2;
            if (compare(mid, query) < 0) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return Math.max(1, left);
    }



    /**
     *
     * @param query 查询关键字
     * @return 小于等于query+"\uffff"的第一个元素rank, 也就是前缀为query 的终止rank
     */
    private int ceiling(String query) {

        query += WORD_SEPARATOR + WORD_SEPARATOR;
        int left = 0;
        int right = length;
        while (left < right) {
            int mid = (left + right) / 2 + 1;
            if (compare(mid, query) > 0) {
                right = mid - 1;
            } else {
                left = mid;
            }
        }
        return Math.min(left, length - 1);
    }

    /**
     * @param query 查询关键字
     * @return 查找前缀匹配的字串下标
     */
    public List<Integer> findPrefixMatch(String query) {
        List<Integer> list = new ArrayList<Integer>();

        int left = floor(query);
        int right = ceiling(query);
        for (int i = left; i <= right; ++i) {
            list.add(index[i]);
        }
        return list;
    }

    private int mod(int x) {
        if (x > length) {
            x -= length;
        }
        return x;
    }
    private int compare(int curRank, String query) {
        int size = Math.min(length, query.length());
        for (int i = 0; i < size; ++i) {
            if (text.charAt(mod(index[curRank] + i)) < query.charAt(i)) {
                return -1;
            }
            if (text.charAt(mod(index[curRank] + i)) > query.charAt(i)) {
                return +1;
            }
        }
        return length - query.length();
    }

    // do one pass of msd sorting by rank at given offset
    private void doit() {
        for (offset = 1; offset < length; offset += offset) {

            int count = 0;
            for (int i = 1; i <= length; i++) {
                if (rank[index[i]] == rank[index[i - 1]]) {
                    count++;
                }
                else if (count > 0) {
                    // sort
                    int left = i - 1 - count;
                    int right = i - 1;
                    quicksort(left, right);

                    // now fix up ranks
                    int r = rank[index[left]];
                    for (int j = left + 1; j <= right; j++) {
                        if (less(index[j - 1], index[j]))  {
                            r = rank[index[left]] + j - left;
                        }
                        newrank[index[j]] = r;
                    }

                    // copy back - note can't update rank too eagerly
                    for (int j = left + 1; j <= right; j++) {
                        rank[index[j]] = newrank[index[j]];
                    }

                    count = 0;
                }
            }
        }
    }

    // sort by leading char, assumes UTF-8
    private void msd() {
        // calculate frequencies
        int[] freq = new int[NUM_OF_CHAR];
        for (int i = 0; i < length; i++) {
            freq[text.charAt(i)]++;
        }

        // calculate cumulative frequencies
        int[] cumm = new int[NUM_OF_CHAR];
        for (int i = 1; i < NUM_OF_CHAR; i++) {
            cumm[i] = cumm[i - 1] + freq[i - 1];
        }

        // compute ranks
        for (int i = 0; i < length; i++) {
            rank[i] = cumm[text.charAt(i)];
        }

        // sort by first char
        for (int i = 0; i < length; i++) {
            index[cumm[text.charAt(i)]++] = i;
        }
    }



/**********************************************************************
 *  Helper functions for comparing suffixes.
 **********************************************************************/

    /**********************************************************************
     * Is the substring text[v..length] lexicographically less than the
     * substring text[w..length] ?
     **********************************************************************/
    private boolean less(int v, int w) {
        if (v + offset >= length) {
            v -= length;
        }
        if (w + offset >= length) {
            w -= length;
        }
        return rank[v + offset] < rank[w + offset];
    }



    /*************************************************************************
     *  Quicksort code from Sedgewick 7.1, 7.2.
     *************************************************************************/

    // swap pointer sort indices
    private void exch(int i, int j) {
        int swap = index[i];
        index[i] = index[j];
        index[j] = swap;
    }


    // SUGGEST REPLACING WITH 3-WAY QUICKSORT SINCE ELEMENTS ARE
    // RANKS AND THERE MAY BE DUPLICATES
    void quicksort(int l, int r) {
        if (r <= l) {
            return;
        }
        int i = partition(l, r);
        quicksort(l, i - 1);
        quicksort(i + 1, r);
    }

    int partition(int l, int r) {
        int i = l - 1;
        int j = r;
        int v = index[r];

        while (true) {

            // find item on left to swap
            while (less(index[++i], v)) {

            }

            // find item on right to swap
            while (less(v, index[--j])) {
                if (j == l) {
                    break;
                }
            }

            // check if pointers cross
            if (i >= j) {
                break;
            }

            exch(i, j);
        }

        // swap with partition element
        exch(i, r);

        return i;
    }
}