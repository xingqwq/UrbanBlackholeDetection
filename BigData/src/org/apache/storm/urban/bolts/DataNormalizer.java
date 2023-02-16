package org.apache.storm.urban.bolts;

import groovy.lang.Tuple2;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.urban.helper.Graph;
import org.apache.storm.urban.helper.Grid;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;

public class DataNormalizer implements IRichBolt {
    private OutputCollector collector;
    private String nodeFile,edgeFile;
    private Graph graph;
    private Grid grid;

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }

    @Override
    public void prepare(Map<String, Object> topoConf, TopologyContext context, OutputCollector collector) {
        this.nodeFile = topoConf.get("node_txt").toString();
        this.edgeFile = topoConf.get("edge_txt").toString();
        // 加载参数
        this.graph = new Graph();
        System.out.println("正在加载道路图数据");
        String str;
        // 读取Node文件
        try{
            FileReader file = new FileReader(this.nodeFile);
            BufferedReader buff = new BufferedReader(file);
            while((str = buff.readLine())!=null){
                String[] data = str.split("\t");
                this.graph.InsertVertex(Integer.parseInt(data[0]),Double.parseDouble(data[1]),Double.parseDouble(data[2]));
            }
        }catch (Exception e){
            System.out.println("读取Node文件发生错误:");
        }
        // 读取Edge文件
        try{
            FileReader file = new FileReader(this.edgeFile);
            BufferedReader buff = new BufferedReader(file);
            while((str = buff.readLine())!=null){
                String[] data = str.split("\t");
                this.graph.InsertEdge(Integer.parseInt(data[0]),Integer.parseInt(data[1]),Integer.parseInt(data[2]));
            }
        }catch (Exception e){
            System.out.println("读取Edge文件发生错误:"+e);
        }
        this.grid = new Grid(graph);
        System.out.println("当前节点数量:"+this.graph.nodeCnt+" 当前边的数量："+this.graph.edgeCnt);
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        // 处理TCP数据
        System.out.println(input.getValue(0));
        // 解析Spout发来的数据
        String data = input.getString(0);
        String[] infos = data.split("\t");
        int flowDirection = Integer.parseInt(infos[0]);
        int time = Integer.parseInt(infos[1]);
        int edgeID = Integer.parseInt(infos[2]);
        int nodef = Integer.parseInt(infos[3]);
        double nodef_lat = Double.parseDouble(infos[4]);
        double nodef_lon = Double.parseDouble(infos[5]);
        int nodet = Integer.parseInt(infos[6]);
        double nodet_lat = Double.parseDouble(infos[7]);
        double nodet_lon = Double.parseDouble(infos[8]);
        int flow = Integer.parseInt(infos[9]);

        // 获得网格单元数据
        Tuple2<Integer, Integer> nodef_grid =  grid.gd.findGridCell(nodef_lat,nodef_lon);
        String gridID = nodef_grid.getFirst()+":"+nodef_grid.getSecond();
        collector.emit(new Values(gridID,flowDirection,edgeID,nodef,nodet,flow));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("gridID","flowDirection","edgeID","nodef","nodet","flow"));
    }

    @Override
    public void cleanup() {}
}
