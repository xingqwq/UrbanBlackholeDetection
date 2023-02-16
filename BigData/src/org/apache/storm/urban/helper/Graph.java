package org.apache.storm.urban.helper;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class Graph {
    public int nodeCnt = 0;
    public int edgeCnt = 0;
    public Map<Integer,vertex> sg;
    public Map<Integer,edge> eg;
    public Graph(){
        sg = new HashMap<>();
        eg = new HashMap<>();
    }
    public void InsertVertex(int id,double lat,double lon){
        nodeCnt += 1;
        vertex tmpVertex = new vertex();
        tmpVertex.nodeID = id;
        tmpVertex.lat = lat;
        tmpVertex.lon = lon;
        sg.put(id,tmpVertex);
    }

    public void InsertEdge(int id,int nodef,int nodet){
        // 创建新边
        edgeCnt += 1 ;
        edge tmpEdge = new edge();
        tmpEdge.nodef = nodef;
        tmpEdge.nodet = nodet;
        tmpEdge.edgeID = id;
        // 加入MAP
        eg.put(id,tmpEdge);
        // 更新节点边信息
        vertex e1 = sg.get(nodef);
        vertex e2 = sg.get(nodet);
        if (e1 != null && e2 != null){
            e1.rela.add(tmpEdge);
            e2.rela.add(tmpEdge);
        }
    }

    public void Clear(){
        sg.clear();
        eg.clear();
    }
}
