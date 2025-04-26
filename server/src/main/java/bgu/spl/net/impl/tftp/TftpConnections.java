package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class TftpConnections<T> implements Connections<T> {

    protected ConcurrentHashMap<Integer, ConnectionHandler<T>> active_connections = new ConcurrentHashMap<>(); 
    protected ConcurrentHashMap<Integer, ConnectionHandler<T>> inactive_connections = new ConcurrentHashMap<>();
    protected int inactive_counter = -1;

    
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler){ 
        this.active_connections.put(connectionId, handler);
    }

    public void basicConnect(int connectionId, ConnectionHandler<T> handler){
        this.inactive_connections.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg){
        this.active_connections.get(connectionId).send(msg);
        return false; 
    }

    public boolean sendInactive(int connectionId, T msg){
        this.inactive_connections.get(connectionId).send(msg);
        return false;
    }

    @Override
    public void disconnect(int connectionId){
        this.active_connections.remove(connectionId);
    }

    public void disconnectInactive(int connectionId){
        this.inactive_connections.remove(connectionId);
    }

    public int getUniqueInactiveID(){
        this.inactive_counter++;
        return this.inactive_counter;
    }
}