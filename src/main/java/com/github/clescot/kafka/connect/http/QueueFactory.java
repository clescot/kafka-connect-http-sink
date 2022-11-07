package com.github.clescot.kafka.connect.http;

import com.github.clescot.kafka.connect.http.source.Acknowledgement;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class QueueFactory {
    public static final String DEFAULT_QUEUE_NAME = "default";
    private static Logger LOGGER = LoggerFactory.getLogger(QueueFactory.class);
    private static Map<String,Queue<Acknowledgement>> queueMap = Maps.newHashMap();

    private static Map<String,Boolean> consumers = Maps.newHashMap();
    public static synchronized Queue<Acknowledgement> getQueue(String queueName){
        if(queueMap.get(queueName) == null){
            LOGGER.debug("creating the '{}' queue",queueName);
            queueMap.put(queueName, new ConcurrentLinkedQueue<>());
        }
        return queueMap.get(queueName);
    }
    public static synchronized Queue<Acknowledgement> getQueue(){
        return getQueue(DEFAULT_QUEUE_NAME);
    }

    public static boolean queueMapIsEmpty(){
        return queueMap.isEmpty();
    }

    public static void registerConsumerForQueue(String queueName){
        Preconditions.checkNotNull(queueName,"we cannot register a consumer for a null queueName");
        Preconditions.checkArgument(!queueName.isEmpty(),"we cannot register a consumer for an empty queueName");
        consumers.put(queueName,true);
    }

    public static boolean hasAConsumer(String queueName){
        Boolean queueHasAConsumer = consumers.get(queueName);
        return queueHasAConsumer != null && queueHasAConsumer;
    }
}
