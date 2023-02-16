package org.apache.storm.urban.helper;

import groovy.lang.Tuple;
import groovy.lang.Tuple2;

import java.util.Map;
import java.util.Vector;

import static java.lang.Math.*;

// 相关对象
public class Grid {
    private double distance;

    public class gridCell{
        public int noBlackhole = 0;
        public int noVolcano = 0;
        public int BlackholeresidualFlow = 0;
        public int VolcanoresidualFlow = 0;
        public Vector<Integer> _Edge = new Vector<>();
        public Vector<Integer> _Node = new Vector<>();
    }
    public double Rad(double d)
    {
        return d * 3.1415926 / 180.0;
    }
    public double Geodist(double lat1, double lon1, double lat2, double lon2)
    {
        double radLat1 = Rad(lat1);
        double radLat2 = Rad(lat2);
        double delta_lon = Rad(lon2 - lon1);
        double top_1 = cos(radLat2) * sin(delta_lon);
        double top_2 = cos(radLat1) * sin(radLat2) - sin(radLat1) * cos(radLat2) * cos(delta_lon);
        double top = sqrt(top_1 * top_1 + top_2 * top_2);
        double bottom = sin(radLat1) * sin(radLat2) + cos(radLat1) * cos(radLat2) * cos(delta_lon);
        double delta_sigma = atan2(top, bottom);
        double distance = delta_sigma * 6378137.0;
        return distance;
    }
    public class GridCellDistributor{
        public double colLength, rowLength;
        public double minLat, minLong;
        public GridCellDistributor(double _minLat, double _maxLat, double _minLong, double _maxLong, int NumGridCellcol, int NumGridCellrow) {
            minLat = _minLat;
            minLong = _minLong;
            colLength = Geodist(_maxLat, 0, _minLat, 0) / NumGridCellcol;
            rowLength = Geodist(_maxLong,0,_minLong,0) / NumGridCellrow;
        }
        public Tuple2<Integer, Integer> findGridCell(double _lat, double _long){
            int i = (int) (Geodist(_lat, 0, minLat, 0) / (colLength * 1.0000000001));
            int j = (int) (Geodist(_long, 0, minLong, 0) / (rowLength * 1.0000000001));
            return new Tuple2<Integer, Integer>(i,j);
        }
    }
    // 构建网格单元
    public Integer areaThreshold = 8;
    public gridCell[][] GC;
    public GridCellDistributor gd;
    public int GridRow = 0 ,GridCol = 0;
    double minLat = 999.0;
    double maxLat = -999.0;
    double minLon = 999.0;
    double maxLon = -999.0;
    public int findDomainSizeAndInsertNodes(Graph graph){
        for(vertex value:graph.sg.values()){
            if(value.lat < minLat) {
                minLat = value.lat;
            }
            if(value.lat > maxLat){
                maxLat = value.lat;
            }
            if(value.lon < minLon){
                minLon = value.lon;
            }
            if(value.lon > maxLon){
                maxLon = value.lon;
            }
        }
        System.out.println("minLat:"+minLat+" maxLat:"+maxLat+" minLon:"+minLon+" maxLon:"+maxLon);
        double length = Geodist(maxLat,maxLon,minLat,minLon) / 1000.0;
        double area = length * length;
        int numberOfCell = (int) (area / areaThreshold);
        return numberOfCell;
    }
    public void InitializeGridCell(int NumGridCellrow,int NumGridCellcol)
    {
        GC = new gridCell[NumGridCellrow+1][NumGridCellcol+1];
        for(int i=0; i<=NumGridCellrow;i++){
            for(int j=0; j<= NumGridCellcol;j++){
                GC[i][j] = new gridCell();
            }
        }
    }
    public void InsertNodeToGrid(Graph graph,GridCellDistributor gd){
        for(vertex value:graph.sg.values()){
            Tuple2<Integer,Integer> tmp = gd.findGridCell(value.lat,value.lon);
            GC[tmp.getFirst()][tmp.getSecond()].noBlackhole = 1;
        }
    }
    public void InsertEdgeToGrid(Graph graph,GridCellDistributor gd){
        for(edge value:graph.eg.values()){
            vertex nodef = graph.sg.get(value.nodef);
            vertex nodet = graph.sg.get(value.nodet);
            Tuple2<Integer,Integer> tmp1 = gd.findGridCell(nodef.lat,nodef.lon);
            Tuple2<Integer,Integer> tmp2 = gd.findGridCell(nodet.lat,nodet.lon);
            if (tmp1.getFirst() == tmp2.getFirst() && tmp1.getSecond() == tmp2.getSecond()){
                GC[tmp1.getFirst()][tmp1.getSecond()]._Edge.add(value.edgeID);
            }else{
                GC[tmp1.getFirst()][tmp1.getSecond()]._Edge.add(value.edgeID);
                GC[tmp2.getFirst()][tmp2.getSecond()]._Edge.add(value.edgeID);
            }
        }
    }
    public void makeGrid(Graph graph){
        int numberOfGridCell = findDomainSizeAndInsertNodes(graph);
        System.out.println("numberOfGridCell:"+numberOfGridCell);
        GridRow= (int) (sqrt(numberOfGridCell) + 1);
        GridCol = (int) (sqrt(numberOfGridCell) + 1);
        System.out.println("行:"+GridRow+"列:"+GridCol);
        gd = new GridCellDistributor(minLat,maxLat,minLon,maxLon,GridRow,GridCol);
        InitializeGridCell(GridRow,GridCol);
        InsertNodeToGrid(graph,gd);
        InsertEdgeToGrid(graph,gd);
    }
    public Grid(Graph graph){
        makeGrid(graph);
    }
}
