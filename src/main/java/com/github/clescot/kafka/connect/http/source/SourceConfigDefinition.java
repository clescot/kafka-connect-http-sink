package com.github.clescot.kafka.connect.http.source;

import com.github.clescot.kafka.connect.http.sink.ConfigConstants;
import org.apache.kafka.common.config.ConfigDef;

public class SourceConfigDefinition {


    private SourceConfigDefinition() {
        //Class with only static methods
    }

    public static ConfigDef config() {
        return new ConfigDef()
                .define(ConfigConstants.SUCCESS_TOPIC, ConfigDef.Type.STRING,  ConfigDef.Importance.HIGH, ConfigConstants.SUCCESS_TOPIC_DOC)
                .define(ConfigConstants.ERRORS_TOPIC, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, ConfigConstants.ERRORS_TOPIC_DOC)
                .define(ConfigConstants.QUEUE_NAME, ConfigDef.Type.STRING, null,ConfigDef.Importance.MEDIUM, ConfigConstants.QUEUE_NAME_DOC)
//                .define(ConfigConstants.STATIC_REQUEST_HEADER_NAMES, ConfigDef.Type.LIST,  Collections.emptyList(), ConfigDef.Importance.MEDIUM, ConfigConstants.STATIC_REQUEST_HEADER_NAMES_DOC)
                ;
    }
}
