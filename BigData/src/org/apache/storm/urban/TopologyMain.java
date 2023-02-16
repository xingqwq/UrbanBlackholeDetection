package org.apache.storm.urban;

import org.apache.storm.StormSubmitter;
import org.apache.storm.tuple.Fields;
import org.apache.storm.urban.bolts.DataNormalizer;
import org.apache.storm.urban.bolts.UrbanBlackHoleDetect;
import org.apache.storm.urban.spouts.DataReader;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.topology.TopologyBuilder;


public class TopologyMain {
    public static void main(String[] args) throws InterruptedException {
        // 构建Topology图
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("data-reader", new DataReader());
        builder.setBolt("data-normalizer", new DataNormalizer(), 1).shuffleGrouping("data-reader");
        builder.setBolt("data-solver", new UrbanBlackHoleDetect(), 1).shuffleGrouping("data-normalizer");

        // 设置参数
        Config conf = new Config();
        conf.put("node_txt",args[0]);
        conf.put("edge_txt",args[1]);
        conf.put("ip_addr", args[2]);
        conf.put("ip_port", args[3]);
        conf.put("flow_threshold", 7);
        conf.setDebug(false);
        conf.put(Config.TOPOLOGY_MAX_SPOUT_PENDING, 1);

        // 提交Topology，注意这里需要捕获异常
        try {
            StormSubmitter cluster = new StormSubmitter();
            cluster.submitTopology("urban-stream-detect", conf, builder.createTopology());
        } catch (Exception e) {
            System.out.println("Error!");
        }
    }
}
