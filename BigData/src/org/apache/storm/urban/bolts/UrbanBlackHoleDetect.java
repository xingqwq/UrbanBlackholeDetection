package org.apache.storm.urban.bolts;

import clojure.lang.Obj;
import groovy.lang.Tuple2;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.BasicOutputCollector;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseBasicBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.urban.helper.Graph;
import org.apache.storm.urban.helper.Grid;
import org.apache.storm.urban.helper.edge;
import org.apache.storm.urban.helper.vertex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Array;
import java.util.*;

import static java.lang.Math.*;

// 优先队列对象
class Entry{
    public int eid;
    public double priority;
    public Entry(int id,double p){
        eid = id;
        priority = p;
    }
}
public class UrbanBlackHoleDetect implements IRichBolt {
    // PriorityQueue 排序
    static Comparator<Entry> prioritySort = new Comparator<Entry>() {
        @Override
        public int compare(Entry o1, Entry o2) {
            if(o1.priority > o2.priority){
                return 1;
            }else{
                return  0;
            }
        }
    };

    public int[] inFlow,outFlow,edgeChoice;
    public int[] pickedEdge,visitedEdge,blackholeEdge,visited;
    public Vector<Integer> currentSubgraph,tempBlackholes;
    public Vector<Vector<Integer>> prevBlackholes;
    public int flowThreshold = 0, sikpCnt = 0, visitCount = 0,blackholeCount=0,totalVisitCount=0;
    public int forloopCount=0, absInFlow=0, absOutFlow=0, tempBlackholesFlow=0;
    public PriorityQueue<Entry> q;
    public ArrayList<Tuple2<Integer,Integer>> sorted = new ArrayList<>();
    private OutputCollector collector;
    private String nodeFile,edgeFile;
    private Graph graph;
    private Grid grid;
    public int flowCnt = 0;

    public void sortEdgeFlowIndecsendingOrder(){
        for(edge value:graph.eg.values()){
            sorted.add(new Tuple2<>(value.edgeID,inFlow[value.edgeID]-outFlow[value.edgeID]));
        }
        sorted.sort(new Comparator<Tuple2<Integer, Integer>>() {
            @Override
            public int compare(Tuple2<Integer, Integer> o1, Tuple2<Integer, Integer> o2) {
                if(o1.getSecond() > o2.getSecond()){
                    return 1;
                }else{
                    return 0;
                }
            }
        });
    }
    public int getBlackholeUpperBound(int i,int j){
        int[] dx = {-1, -1, 0, 1, 1, 1, 0, -1};
        int[] dy = {0, 1, 1, 1, 0, -1, -1, -1};
        int maximun = 0;
        for(int startDirection = 0;startDirection < 8;startDirection+=2){
            int sum = 0;
            for(int k=0;k<3;k++){
                sum += grid.GC[i+dx[startDirection+k&7]][j+dy[startDirection+k&7]].BlackholeresidualFlow;
            }
            maximun = maximun>sum?maximun:sum;
        }
        maximun += grid.GC[i][j].BlackholeresidualFlow;
        return maximun;
    }

    public void BlackholeinitialGridPruning(){
        System.out.println("开始进行预减枝");
        int preCnt = 0;
        for(int i=0;i<grid.GridRow;i++){
            for(int j=0;j< grid.GridCol;j++){
                grid.GC[i][j].BlackholeresidualFlow = 0;
                for(Integer value:grid.GC[i][j]._Edge){
                    if(blackholeEdge[value]!=1){
                        grid.GC[i][j].BlackholeresidualFlow += (inFlow[value]-outFlow[value]>0?inFlow[value]-outFlow[value]:0);
                    }
                }
            }
        }
        for(int i=1;i<grid.GridRow;i++){
            for(int j=1;j< grid.GridCol;j++){
                if(getBlackholeUpperBound(i,j)<flowThreshold){
                    grid.GC[i][j].noBlackhole = 1;
                    if(grid.GC[i][j].noBlackhole == 1 && grid.GC[i][j].noVolcano == 1){
                        for(Integer value:grid.GC[i][j]._Edge){
                            preCnt += 1 ;
                            edgeChoice[value] = 1;
                        }
                    }
                }
            }
        }
        System.out.println("Pruning:"+preCnt);
    }

    public boolean tryPushEdge(int eid){
        if(pickedEdge[eid] == 1 || blackholeEdge[eid] == 1 || edgeChoice[eid] == 1){
            sikpCnt += 1;
            return true;
        }
        return false;
    }

    public double getPriority(int iter,int eid){
        if(iter == 0){
            return inFlow[eid]-outFlow[eid];
        }else{
            return outFlow[eid] - inFlow[eid];
        }
    }

    public void blackHoleDynamicUB(){
        int NumGridCellcol = grid.GridCol;
        int NumGridCellrow = grid.GridRow;
        int prunedCnt = 0;
        Vector<Integer> affectedGridCell = new Vector<>();
        for(int i=0; i< tempBlackholes.size();i++){
            int nodef = graph.eg.get(tempBlackholes.get(i)).nodef;
            int nodet = graph.eg.get(tempBlackholes.get(i)).nodet;
            Tuple2<Integer,Integer> tmp1 = grid.gd.findGridCell(graph.sg.get(nodef).lat,graph.sg.get(nodef).lon);
            Tuple2<Integer,Integer> tmp2 = grid.gd.findGridCell(graph.sg.get(nodet).lat,graph.sg.get(nodet).lon);
            Integer row1 = tmp1.getFirst();
            Integer col1 = tmp1.getSecond();
            Integer row2 = tmp2.getFirst();
            Integer col2 = tmp2.getSecond();
            int delta = Math.max(inFlow[tempBlackholes.get(i)]-outFlow[tempBlackholes.get(i)],0);
            grid.GC[row1][col1].BlackholeresidualFlow -= delta;
            affectedGridCell.add((row1*NumGridCellcol+col1));
            if(row2!=row1 || col2!= col1){
                grid.GC[row2][col2].BlackholeresidualFlow -= delta;
                affectedGridCell.add((row2*NumGridCellcol+col2));
            }
            for(int k = 0;k<affectedGridCell.size();k++){
                int cellID = affectedGridCell.get(k);
                int tmpi = cellID/NumGridCellcol;
                int tmpj = cellID&NumGridCellcol;
                if(tmpi == 0 || tmpj == 0|| tmpi+1==NumGridCellrow || tmpj+1==NumGridCellcol){
                    continue;
                }
                if(getBlackholeUpperBound(tmpi,tmpj)<flowThreshold){
                    grid.GC[tmpi][tmpj].noBlackhole = 1;
                    if(grid.GC[tmpi][tmpj].noBlackhole == 1){
                        for(int l = 0;l< grid.GC[tmpi][tmpj]._Edge.size();l++){
                            prunedCnt += 1;
                            edgeChoice[graph.eg.get(grid.GC[tmpi][tmpj]._Edge.get(l)).nodef] = 1;
                        }
                    }
                }
            }
        }
    }
    public void CleanInfo(){
        if(absInFlow > flowThreshold){
            for(int i = 0;i< tempBlackholes.size();i++){
                blackholeEdge[tempBlackholes.get(i)] = 1;
            }
            for(int i = 0; i< tempBlackholes.size();i++){
                edgeChoice[tempBlackholes.get(i)] = 1;
            }
            blackholeCount += 1;
            blackHoleDynamicUB();
            prevBlackholes.add(tempBlackholes);
        }
        currentSubgraph.clear();
        tempBlackholes.clear();
        totalVisitCount += visitCount;
        tempBlackholesFlow = absInFlow = absOutFlow = visitCount = forloopCount = 0;
        for(int i = 0;i < visited.length;i++){
            visitedEdge[i] = 0;
            visited[i] = 0;
        }
    }

    public int BlackHoleDetection(){
        for(int i=0;i<graph.edgeCnt;i++){
            for(int j=0;j<2;j++){
                int eid = sorted.get(i).getFirst();
                System.out.println("EID:"+eid);
                if(tryPushEdge(eid)){
                    break;
                }else{
                    Entry tmp = new Entry(eid,getPriority(j,eid));
                    q.offer(tmp);
                    visited[eid] = 1;
                    visitedEdge[eid] = 1;
                    visitCount += 0;
                }
                System.out.println("开始进行检测1");
                double BH_minLat = 999, BH_minLong = 999, BH_maxLat = -999, BH_maxLong = -999;
                Boolean island = true;
                while (!q.isEmpty()){
                    int currentEdge = q.poll().eid;
                    int delta = inFlow[currentEdge]-outFlow[currentEdge];
                    int f = graph.eg.get(currentEdge).nodef;
                    int t = graph.eg.get(currentEdge).nodet;
                    double fLat = graph.sg.get(f).lat, fLon = graph.sg.get(f).lon;
                    double tLat = graph.sg.get(t).lat, tLon = graph.sg.get(t).lon;
                    // 检查空间阀值
                    double newMinLat = Math.min(BH_minLat, fLat);
                    newMinLat = Math.min(newMinLat, tLat);
                    double newMaxLat = Math.max(BH_maxLat, fLat);
                    newMaxLat = Math.max(newMaxLat, tLat);
                    double newMinLon = Math.min(BH_minLong,fLon);
                    newMinLon = Math.min(newMinLon,tLon);
                    double newMaxLon = Math.max(BH_maxLong,fLon);
                    newMaxLon = Math.max(newMaxLon,tLon);
                    double row = grid.Geodist(newMinLat,newMinLon,newMinLat,newMaxLon)/1000.0;
                    double col = grid.Geodist(newMaxLat,newMinLon,newMaxLat,newMaxLon)/1000.0;
                    double diagnose = grid.Geodist(newMaxLat,newMaxLon,newMinLat,newMaxLon)/1000.0;
                    double area = row * col;
                    double diagnoseThreshold = Math.sqrt(2.0* grid.areaThreshold);
                    System.out.println("开始进行检测2");
                    if(area>grid.areaThreshold || diagnose>diagnoseThreshold){
                        island = false;
                    }else{
                        BH_minLat =  newMinLat;
                        BH_maxLat =  newMaxLat;
                        BH_minLong = newMinLon;
                        BH_maxLong = newMaxLon;
                        currentSubgraph.add(currentEdge);
                        absInFlow += delta;
                        absOutFlow -= delta;
                        if(absInFlow>flowThreshold){
                            tempBlackholes = currentSubgraph;
                            tempBlackholesFlow = absInFlow;
                        }
                        System.out.println("开始进行检测3");
                        // 添加初始边
                        Vector<edge> edgeRelaf = graph.sg.get(f).rela;
                        for(int k=0;i<edgeRelaf.size();i++){
                            forloopCount += 1;
                            int tmpEid = edgeRelaf.get(k).edgeID;
                            visitCount += 1;
                            if(blackholeEdge[tmpEid] != 1 && visitedEdge[tmpEid] != 1){
                                q.add(new Entry(tmpEid,getPriority(j,tmpEid)));
                                visitedEdge[tmpEid] = 1;
                                visited[tmpEid] = 1;
                            }
                        }
                        System.out.println("开始进行检测4");
                        // 添加终止边
                        Vector<edge> edgeRelat = graph.sg.get(f).rela;
                        for(int k=0;i<edgeRelat.size();i++){
                            forloopCount += 1;
                            int tmpEid = edgeRelat.get(k).edgeID;
                            visitCount += 1;
                            if(blackholeEdge[tmpEid] != 1 && visitedEdge[tmpEid] != 1){
                                q.add(new Entry(tmpEid,getPriority(j,tmpEid)));
                                visitedEdge[tmpEid] = 1;
                                visited[tmpEid] = 1;
                            }
                        }
                        System.out.println("开始进行检测5");
                    }
                }
                if(island == true){
                    for(int k=0;k<currentSubgraph.size();k++){
                        edgeChoice[currentSubgraph.get(k)] = 1;
                    }
                }
                CleanInfo();
            }
        }
        return totalVisitCount;
    }

    @Override
    public void prepare(Map<String, Object> topoConf, TopologyContext context, OutputCollector collector) {
        flowThreshold = Integer.parseInt(topoConf.get("flow_threshold").toString());
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
        inFlow = outFlow = edgeChoice = pickedEdge = blackholeEdge = visitedEdge = visited = new int[graph.edgeCnt+2];
        q =  new PriorityQueue<>(prioritySort);
       currentSubgraph = tempBlackholes =new Vector<>();
       prevBlackholes = new Vector<>();
    }

    @Override
    public void execute(Tuple input) {
        int flowDerection = Integer.parseInt(input.getValue(1).toString());
        int edgeID = Integer.parseInt(input.getValue(2).toString());
        int nodef = Integer.parseInt(input.getValue(3).toString());
        int nodet = Integer.parseInt(input.getValue(4).toString());
        int flow = Integer.parseInt(input.getValue(5).toString());
        if(flowDerection == 1){
            inFlow[edgeID] += 1;
            flowCnt += 1;
        }else{
            outFlow[edgeID] += 1;
            flowCnt += 1;
        }
        if(flowCnt >= 20){
            BlackholeinitialGridPruning();
            sortEdgeFlowIndecsendingOrder();
            BlackHoleDetection();
        }
        System.out.println("UrbanDerection！！！！！！！！！！！！！！");

    }

    @Override
    public void cleanup() {

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("flow"));
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }
}
