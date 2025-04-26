package bgu.spl.net.impl.tftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.*;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate; //eliya - is it necessary to make it volatile
    private int connectionId;
    private boolean logged_in = false;
    private String incomingFileName = null;
    private ByteArrayOutputStream  bufferForFileFromClient = new ByteArrayOutputStream ();
    private boolean sending_data = false;
    private boolean nextends = false;
    private int blockNumCounter;
    private FileInputStream fileoutstream;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.shouldTerminate = false;
        this.connectionId = connectionId;
    }

    @Override
    public void process(byte[] message) { 
        byte opcode = message[1];
        if(!logged_in){
            if(opcode == 7){
                logOperation(message);            
            }

            else if(opcode == 10){
                disconnectOp(false);
            }
            else{          
                connectionsHolder.connectionsObj.sendInactive(this.connectionId, this.errorOperation(6));
            }
        }

        else{ //user is logged in
            if (opcode == 1)
                readRequest(message);

            else if (opcode == 2){
                writeRequest(message);
            }

            else if (opcode == 3){
                dataPacketIn(message);
            }
            else if (opcode == 4)
                acceptAckOperation(message);

            else if (opcode == 6)
                dataDirOut();

            else if (opcode == 7){ //logged in, and wanted to do another LOGRQ. will send error
                connectionsHolder.connectionsObj.send(this.connectionId, this.errorOperation(7)); //is error 7 correct for this case?
            }
            
            else if (opcode == 8)
                deleteFile(message);

            else if (opcode == 10)
                disconnectOp(true);

        }
    }

    //handles RRQ message sent from client to server
    private void readRequest(byte[] message){ 
        byte[] filenameInBytes = Arrays.copyOfRange(message,2, message.length - 1); 

        // Convert byte array to string using UTF-8
        String filename = new String(filenameInBytes, StandardCharsets.UTF_8);
        Path path = Paths.get("Files", filename); //constructs the path to the file
        Path filePath = path.toAbsolutePath();
        boolean isExist = false;
        try {
            isExist = Files.exists(filePath);
        } catch (SecurityException e) { connectionsHolder.connectionsObj.send(this.connectionId, this.errorOperation(2)); } //needed?? maybe not? - neya

        if(isExist){
            this.dataFileOut(filename);
        }
        else{
            connectionsHolder.connectionsObj.send(this.connectionId, this.errorOperation(1));
        }
    }

    //handles WRQ message sent from client to server
    private void writeRequest(byte[] message){
        byte[] filenameInBytes = Arrays.copyOfRange(message,2, message.length - 1);
        String filename = new String(filenameInBytes, StandardCharsets.UTF_8);
        Path path = Paths.get("Files", filename); //constructs the path to the file
        Path filePath = path.toAbsolutePath();
        boolean isExist = false;
        try {
            isExist = Files.exists(filePath);
        } catch (SecurityException e) {connectionsHolder.connectionsObj.send(this.connectionId, this.errorOperation(2));} //needed?? maybe not? - neya
        
        if(isExist){
            connectionsHolder.connectionsObj.send(this.connectionId, this.errorOperation(5)); //the file already exists in the server
        }
        else{
            this.dataInPrep(message); //prepares for data packets to be sent by the client
            connectionsHolder.connectionsObj.send(this.connectionId, this.ackOperation(0)); 
        }
    }

    //handles ACK message sent from server to client
    private void acceptAckOperation(byte[] message){ 
        if(this.nextends){
            this.sending_data = false;
            this.nextends = false;
        }
        else{
            this.dataFileOut(null);
        }
    }

    //sends ACK 0 when needed
    private byte[] ackOperation(int blockNum){
        short a = 4;    
        byte[] a_bytes = new byte []{( byte ) ( a >> 8) , ( byte ) ( a & 0xff ) };
        byte[] b_bytes = new byte []{( byte ) ( blockNum >> 8) , ( byte ) ( blockNum & 0xff ) };
        byte[] ack = new byte[]{a_bytes[0], a_bytes[1], b_bytes[0], b_bytes[1]};
        return ack;
    }

    //handles ERROR message sent from server to client
    private byte[] errorOperation(int errorCode){
        ErrorsHolderDict errorsDict = ErrorsHolderDict.getInstance();
        short opcode = 5;
        short eCode = (short)errorCode;
        byte[] op_bytes = new byte []{( byte ) ( opcode >> 8) , ( byte ) ( opcode & 0xff ) };
        byte[] eNum_bytes = new byte []{( byte ) ( eCode >> 8) , ( byte ) ( eCode & 0xff ) };
        byte[] eStr_bytes = errorsDict.getErrorByNumber(errorCode).getBytes();
        
        int totalLength = op_bytes.length + eNum_bytes.length + eStr_bytes.length + 1; //+1 for the 0 last byte
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.put(op_bytes);
        buffer.put(eNum_bytes);
        buffer.put(eStr_bytes);
        buffer.put((byte) 0);

        return buffer.array(); 
    }


    //handles LOGRQ message sent from client to server
    private void logOperation(byte[] message){
        ConnectionHandler<byte[]> BCH = connectionsHolder.connectionsObj.inactive_connections.get(connectionId);
        String name = "";
        try{
            name = new String(message, "UTF-8"); 
        } catch(UnsupportedEncodingException e){}
        int checkUserName = name.hashCode(); 

        if(connectionsHolder.connectionsObj.active_connections.containsKey(checkUserName)){
            connectionsHolder.connectionsObj.sendInactive(this.connectionId, this.errorOperation(7));
        }

        else{ //was inactive, has legal unique name, now should be activated
            connectionsHolder.connectionsObj.inactive_connections.remove(this.connectionId);
            this.connectionId = checkUserName;
            connectionsHolder.connectionsObj.connect(this.connectionId, BCH); //will insert to active connections
            this.logged_in = true;
            connectionsHolder.connectionsObj.send(this.connectionId, this.ackOperation(0));
        }
    }

    //handles DELRQ message sent from client to server
    private void deleteFile(byte[] message){
        byte[] filenameInBytes = Arrays.copyOfRange(message,2, message.length - 1); 

        String filename = new String(filenameInBytes, StandardCharsets.UTF_8);
        Path path = Paths.get("Files", filename); //constructs the path to the file
        Path filePath = path.toAbsolutePath();

        try {
            boolean isDeleted = Files.deleteIfExists(filePath);

            if(isDeleted) {
                connectionsHolder.connectionsObj.send(this.connectionId, this.ackOperation(0));
                //needed to send BCAST to all users
                this.bcastOperation(filename, false); 
            } else {
                connectionsHolder.connectionsObj.send(this.connectionId, this.errorOperation(1));
            }
        } catch (IOException e) {
            connectionsHolder.connectionsObj.sendInactive(this.connectionId, this.errorOperation(2));
        }
    }

    //handles ERROR message sent from server to all logged clients
    private void bcastOperation(String filename, boolean changeType){
        byte indicator = 0;
        if(changeType)
            indicator = 1;
        byte[] prefix = new byte[]{0,9,indicator};
        byte[] byteArray = filename.getBytes();
        byte zer = 0;
        int totalLength = 4 + byteArray.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.put(prefix);
        buffer.put(byteArray);
        buffer.put(zer);
        byte[] bcastPacket = buffer.array(); 
        for(Integer a: connectionsHolder.connectionsObj.active_connections.keySet()){
            connectionsHolder.connectionsObj.send(a, bcastPacket);
        }
    }

    //handles DISC message sent from client to server  //maybe no need of this method
    private void disconnectOp(boolean connected){
        if(connected){
            connectionsHolder.connectionsObj.send(this.connectionId, this.ackOperation(0));
            connectionsHolder.connectionsObj.disconnect(this.connectionId);
        }
        else{
            connectionsHolder.connectionsObj.sendInactive(this.connectionId, this.errorOperation(6));
            connectionsHolder.connectionsObj.sendInactive(this.connectionId, this.ackOperation(0)); //TODO check
            connectionsHolder.connectionsObj.disconnectInactive(this.connectionId);
        }

    }

    //handles DATA message sent from client to server
    private void dataPacketIn(byte[] message){
        short packetSize = (short) (((short) message[2]) << 8 | (short) (message[3]) & 0x00ff);
        short packetBlockNum = (short) (((short) message[4]) << 8 | (short) (message[5]) & 0x00ff);
        try{
            this.bufferForFileFromClient.write(Arrays.copyOfRange(message, 6, message.length));
        } catch(IOException e){}
        if(packetSize<512){ 
            this.saveFile(this.incomingFileName);
        }
        connectionsHolder.connectionsObj.send(this.connectionId, this.ackOperation(packetBlockNum));
    }

    //makes preliminary steps for handling dataIn
    private void dataInPrep(byte[] message){
        byte[] filenameInBytes = Arrays.copyOfRange(message,2, message.length - 1);
        String filename = new String(filenameInBytes, StandardCharsets.UTF_8);
        this.incomingFileName = filename;
        try{
            this.bufferForFileFromClient.flush();
        } catch(IOException e){}
    }

    //saves file after finished dataIn
    private void saveFile(String fileName){
        try{
            Path p = Paths.get("Files",this.incomingFileName);
            Path filePath = p.toAbsolutePath();
            File newfile = Files.createFile(filePath).toFile();
            FileOutputStream fileOutputStream = new FileOutputStream(newfile);
            this.bufferForFileFromClient.writeTo(fileOutputStream); //uploads accumulated bytes to the new file
        } catch(IOException e){}
        this.bcastOperation(this.incomingFileName, true); 
        this.incomingFileName = null; 
    }

    //handles sending packets to client following an RRQ by the client
    private void dataFileOut(String fileName){
        try{
            if(!this.sending_data){
                this.sending_data = true;
                this.blockNumCounter = 0;
                Path path = Paths.get("Files", fileName); //constructs the path to the file
                Path filePath = path.toAbsolutePath();
                File fileToSend = new File(filePath.toString());
                this.fileoutstream = new FileInputStream(fileToSend);
            }
    
            byte[] slicedData;
    
            if(this.sending_data){
                if(this.fileoutstream.available()>=0){
                    if(fileoutstream.available()>=512){
                        slicedData = new byte[512];
                    }
                    else{
                        slicedData = new byte[fileoutstream.available()];
                        this.nextends = true;
                    }
                    fileoutstream.read(slicedData);
                    blockNumCounter++;
                    byte[] readyPacket = createDataPacket(slicedData, blockNumCounter);
                    connectionsHolder.connectionsObj.send(this.connectionId, readyPacket);
                }
            }
        } catch(IOException e){}
       
    }

    private byte[] createDataPacket(byte[] rawData, int blockNumber){
        short a = 3;
        byte[] opdcodeBytes = new byte []{( byte ) ( a >> 8) , ( byte ) ( a & 0xff ) };
        byte[] packetSizeBytes = new byte []{( byte ) ( rawData.length >> 8) , ( byte ) ( rawData.length & 0xff ) };
        byte[] blockNumBytes = new byte []{( byte ) ( blockNumber >> 8) , ( byte ) ( blockNumber & 0xff ) };


        int totalLength = 6 + rawData.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.put(opdcodeBytes);
        buffer.put(packetSizeBytes);
        buffer.put(blockNumBytes);
        buffer.put(rawData);

        byte[] dataPacket = buffer.array(); 
        return dataPacket;
    }


    //handles DATA message sent from server to client
    private void dataDirOut(){ 
        String filename = "tempFileNames";
        Path pathNewFile = Paths.get("Files", filename);
        Path filePath = pathNewFile.toAbsolutePath();
        FileOutputStream dirstream;
        byte zer = 0;
        try {
            // Create the file
            File newfile = Files.createFile(filePath).toFile();
            dirstream = new FileOutputStream(newfile);
            List<Path> filteredPaths = listFilesExcludingByName("Files", "tempFileNames");

            for (Path path : filteredPaths) {
                dirstream.write(path.toFile().getName().getBytes());
                dirstream.write(zer);
            }
            this.dataFileOut("tempFileNames");
            Files.deleteIfExists(filePath);
        } catch (IOException e) {}
    }


        public static List<Path> listFilesExcludingByName(String directoryPath, String excludedFileName) throws IOException {
        Path directory = Paths.get(directoryPath);
        List<Path> filteredPaths = new ArrayList<>();

        if (Files.exists(directory) && Files.isDirectory(directory)) {
            filteredPaths = Files.walk(directory)
                .filter(Files::isRegularFile) // Filter only regular files
                .filter(path -> !path.getFileName().toString().equals(excludedFileName)) // Exclude files by name
                .collect(Collectors.toList()); // Collect filtered paths into a list
        }
        return filteredPaths;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 
}