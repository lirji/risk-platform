package com.lrj.risk.profiling.flink;

import java.time.Duration;
import java.time.ZoneId;

import com.lrj.risk.contracts.v1.TransactionEventV1;
import com.lrj.risk.feature.domain.FeatureAccumulator;
import com.lrj.risk.feature.domain.FeatureUpdate;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/** Stateful domain adapter; Redis writes are handled by a separate idempotent sink. */
public class FeatureAggregator extends KeyedProcessFunction<String, TransactionEventV1, FeatureUpdate> {

    private final String zoneId;
    private transient ValueState<FeatureAccumulator> accumulatorState;
    private transient ZoneId zone;

    public FeatureAggregator(String zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public void open(OpenContext openContext) {
        ValueStateDescriptor<FeatureAccumulator> descriptor =
                new ValueStateDescriptor<>("feature-accumulator-v1", FeatureAccumulator.class);
        descriptor.enableTimeToLive(StateTtlConfig.newBuilder(Duration.ofDays(90))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build());
        accumulatorState = getRuntimeContext().getState(descriptor);
        zone = ZoneId.of(zoneId);
    }

    @Override
    public void processElement(TransactionEventV1 event, Context context,
                               Collector<FeatureUpdate> collector) throws Exception {
        FeatureAccumulator accumulator = accumulatorState.value();
        if (accumulator == null) {
            accumulator = new FeatureAccumulator();
        }
        collector.collect(accumulator.apply(event, zone));
        accumulatorState.update(accumulator);
    }
}
