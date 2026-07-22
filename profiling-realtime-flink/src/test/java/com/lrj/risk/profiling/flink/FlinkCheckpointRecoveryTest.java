package com.lrj.risk.profiling.flink;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.CheckpointListener;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink;
import org.junit.jupiter.api.Test;

/** Proves that the job can restart from a completed checkpoint in a real Flink MiniCluster. */
class FlinkCheckpointRecoveryTest {

    @Test
    void restoresOperatorStateAfterFailureFollowingCompletedCheckpoint() throws Exception {
        RecoveringMapper.reset();
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setParallelism(1);
        environment.enableCheckpointing(50, CheckpointingMode.EXACTLY_ONCE);
        environment.getCheckpointConfig().setCheckpointTimeout(5_000);
        Configuration recovery = new Configuration();
        recovery.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        recovery.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 2);
        recovery.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofMillis(20));
        environment.configure(recovery);

        environment.fromSequence(1, 500)
                .map(new RecoveringMapper())
                .name("checkpoint-aware-failure")
                .sinkTo(new DiscardingSink<>())
                .name("discard");

        MiniClusterConfiguration clusterConfiguration = new MiniClusterConfiguration.Builder()
                .setNumTaskManagers(1).setNumSlotsPerTaskManager(1).withRandomPorts().build();
        MiniCluster cluster = new MiniCluster(clusterConfiguration);
        try {
            cluster.start();
            cluster.executeJobBlocking(environment.getStreamGraph().getJobGraph());
        } finally {
            cluster.closeAsync().get();
        }

        assertTrue(RecoveringMapper.CHECKPOINT_COMPLETED.get(), "a checkpoint must complete before failure");
        assertTrue(RecoveringMapper.FAILURE_TRIGGERED.get(), "the test must exercise a task failure");
        assertTrue(RecoveringMapper.STATE_RESTORED.get(), "operator state must be restored after restart");
    }

    private static final class RecoveringMapper extends RichMapFunction<Long, Long>
            implements CheckpointedFunction, CheckpointListener {
        private static final AtomicBoolean CHECKPOINT_COMPLETED = new AtomicBoolean();
        private static final AtomicBoolean FAILURE_TRIGGERED = new AtomicBoolean();
        private static final AtomicBoolean STATE_RESTORED = new AtomicBoolean();

        private transient ListState<Integer> processedState;
        private int processed;

        static void reset() {
            CHECKPOINT_COMPLETED.set(false);
            FAILURE_TRIGGERED.set(false);
            STATE_RESTORED.set(false);
        }

        @Override
        public Long map(Long value) throws Exception {
            processed++;
            Thread.sleep(3);
            if (CHECKPOINT_COMPLETED.get() && FAILURE_TRIGGERED.compareAndSet(false, true)) {
                throw new IllegalStateException("intentional failure after completed checkpoint");
            }
            return value;
        }

        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {
            processedState.clear();
            processedState.add(processed);
        }

        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {
            processedState = context.getOperatorStateStore().getListState(
                    new ListStateDescriptor<>("processed-count", Integer.class));
            if (context.isRestored()) {
                STATE_RESTORED.set(true);
                for (Integer value : processedState.get()) {
                    processed = value;
                    break;
                }
            }
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
            CHECKPOINT_COMPLETED.set(true);
        }
    }
}
