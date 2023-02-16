package org.apache.storm.urban.spouts;

import java.io.*;
import java.net.Socket;
import java.util.Map;

import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

public class DataReader extends BaseRichSpout{
    private SpoutOutputCollector collector;
    private Socket socket = null;
    private boolean completed = false;

    public void ack(Object msgId) {
        System.out.println("OK:"+msgId);
    }
    public void close() {
        System.out.println("Close!");
    }
    public void fail(Object msgId) {
        System.out.println("FAIL:"+msgId);
    }

    /**
     * nextTuple发送数据
     */
    public void nextTuple() {
        if(completed){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("捕捉到异常"+e);
            }
            return ;
        }
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader((socket.getInputStream())));
            while(true){
                String data = reader.readLine();
                if (data == null || data.equals("Close Connection!")){
                    System.out.println("数据发送方准备关闭接口...");
                    break;
                }
                this.collector.emit(new Values(data),data);
            }
            socket.close();
            reader.close();
        }catch(Exception e){
            throw new RuntimeException("错误的元组",e);
        }finally {
            completed = true;
        }
    }

    /**
     * 打开水龙头，准备接收数据的方式；在本系统中使用TCP接收；
     * @param conf      The Storm configuration for this spout. This is the configuration provided to the topology merged in with cluster
     *                  configuration on this machine.
     * @param context   This object can be used to get information about this task's place within the topology, including the task id and
     *                  component id of this task, input and output information, etc.
     * @param collector The collector is used to emit tuples from this spout. Tuples can be emitted at any time, including the open and
     *                  close methods. The collector is thread-safe and should be saved as an instance variable of this spout object.
     */
    public void open(Map conf, TopologyContext context,
                     SpoutOutputCollector collector) {
        try{
            System.out.println("连接数据服务器... "+conf.get("ip_addr").toString()+" "+Integer.parseInt(conf.get("ip_port").toString()));
            socket = new Socket(conf.get("ip_addr").toString(),Integer.parseInt(conf.get("ip_port").toString()));
            System.out.println("创建套接字成功");
        }catch (IOException e){
            e.printStackTrace();
            completed = true;
        }
        this.collector = collector;
    }

    /**
     *
     * @param declarer this is used to declare output stream ids, output fields, and whether or not each output stream is a direct stream
     */
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("line"));
    }
}
