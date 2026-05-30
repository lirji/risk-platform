package com.lrj.risk.rating.config;

import java.io.Serializable;

/**
 * 评级任务 (来自 MySQL t_rating_task): Web 平台创建, 引擎读 PENDING 任务执行。
 */
public class RatingTask implements Serializable {

    private final long id;
    private final String taskCode;
    private final String modelCode;
    private final String sourceIndex;   // ES 标签数据源
    private final String targetIndex;   // es 风险库目标

    public RatingTask(long id, String taskCode, String modelCode, String sourceIndex, String targetIndex) {
        this.id = id;
        this.taskCode = taskCode;
        this.modelCode = modelCode;
        this.sourceIndex = sourceIndex;
        this.targetIndex = targetIndex;
    }

    public long getId() {
        return id;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public String getModelCode() {
        return modelCode;
    }

    public String getSourceIndex() {
        return sourceIndex;
    }

    public String getTargetIndex() {
        return targetIndex;
    }
}
