package com.github.clescot.kafka.connect.http.sink;

import com.github.clescot.kafka.connect.http.source.SourceConfigDefinition;
import com.google.common.base.Preconditions;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class WsSinkConnector extends SinkConnector {

    private Map<String, String> settings;

    @Override
    public void start(Map<String, String> settings) {
        Preconditions.checkNotNull(settings);
        this.settings = settings;
    }

    @Override
    public Class<? extends Task> taskClass() {
        return WsSinkTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int taskCount) {
        List<Map<String, String>> configs = new ArrayList<>(taskCount);
        Preconditions.checkNotNull(settings,"settings need to be initialized. call start() method before taskConfigs");
        for (int i = 0; i < taskCount; i++) {
            configs.add(this.settings);
        }
        return configs;
    }

    @Override
    public void stop() {
    }

    @Override
    public ConfigDef config() {
        return SourceConfigDefinition.config();
    }

    @Override
    public String version() {
        return VersionUtil.version(this.getClass());
    }
}
