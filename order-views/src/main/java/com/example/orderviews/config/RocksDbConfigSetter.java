package com.example.orderviews.config;

import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.Options;

import java.util.Map;

public class RocksDbConfigSetter implements RocksDBConfigSetter {

    @Override
    public void setConfig(String storeName, Options options, Map<String, Object> configs) {
        // Optimize RocksDB for read-heavy workloads
        options.setIncreaseParallelism(1);
        options.setOptimizeFiltersForHits(true);
        options.setLevelCompactionDynamicLevelBytes(true);

        // Memory settings
        options.setWriteBufferSize(64 * 1024 * 1024); // 64MB
        options.setMaxWriteBufferNumber(3);
        options.setMaxBackgroundCompactions(2);
        options.setMaxBackgroundFlushes(1);

        // Compression
        options.setCompressionType(org.rocksdb.CompressionType.SNAPPY_COMPRESSION);
    }

    @Override
    public void close(String storeName, Options options) {
        // Close method - no custom cleanup needed
        // RocksDB options will be closed by Kafka Streams
    }
}
