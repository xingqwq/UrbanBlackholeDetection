import socket
import argparse
import os
import time
import random
import threading
   

def SEND(args,conn,addr):
    with open("./flow/flow_15_8.txt","r") as file:
        data = file.readlines()
        data = data[1:]
    
    for i in data:
        time.sleep(0.2)
        conn.sendall(i.encode())
    conn.sendall("Close Connection!\n".encode())
    print("收到客户端连接：{}，且已经完成了数据传输，准备关闭套接字...".format(addr))
    conn.close()
    socketPtr.close()

             
parse = argparse.ArgumentParser()
parse.add_argument("--job",type=str,default="server")
parse.add_argument("--port",type=int,default=10240)
parse.add_argument("--addr",type=str,default="127.0.0.1")
parse.add_argument("--file_path",type=str,default="node.txt")
args = parse.parse_args()

if args.job == "server":
    HOST = args.addr
    PORT = args.port
    ADDR = (HOST, PORT)
    socketPtr = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    socketPtr.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1 )
    socketPtr.bind(ADDR)
    socketPtr.listen(10)
    print(ADDR)
    print("开始监听1024端口....")
    while True:
        try:
            conn, addr = socketPtr.accept()
            print("收到一个新的连接")
        except IOError as err:
            continue
        t = threading.Thread(target=SEND,args=(args,conn,addr))
        t.start()

