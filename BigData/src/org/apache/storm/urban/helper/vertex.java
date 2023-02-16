package org.apache.storm.urban.helper;

import java.util.Vector;

public class vertex {
    public int nodeID;
    public double lat;
    public double lon;
    public Vector<edge> rela = new Vector<>();
}
