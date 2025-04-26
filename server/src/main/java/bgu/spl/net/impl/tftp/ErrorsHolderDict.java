package bgu.spl.net.impl.tftp;
import java.util.*;

public class ErrorsHolderDict { //Singelton Class - check if for each client thread it is initialized or just once??
    private static ErrorsHolderDict singleInstance = null; //makes sure only one instance of this dictionery is created
    private Map<Integer, String> errorsHolder;

    private ErrorsHolderDict(){
        errorsHolder = new HashMap<>();
        initializeErrorsHolder();
    }

    public static ErrorsHolderDict getInstance(){
        if (singleInstance == null) {
            singleInstance = new ErrorsHolderDict();
        }
        return singleInstance;
    }

    private void initializeErrorsHolder(){
        errorsHolder.put(0, "Not defined, see error message (if any).");
        errorsHolder.put(1, "File not found - RRQ DELRQ of non-existing file.");
        errorsHolder.put(2, "Access violation - File cannot be written, read or deleted.");
        errorsHolder.put(3, "Disk full or allocation exceeded - No room in disk.");
        errorsHolder.put(4, "Illegal TFTP operation - Unknown Opcode.");
        errorsHolder.put(5, "File already exists - File name exists on WRQ.");
        errorsHolder.put(6, "User not logged in - Any opcode received before Login completes.");
        errorsHolder.put(7, "User already logged in - Login username already connected.");
    }

    public String getErrorByNumber(int errorCode) {
        return errorsHolder.get(errorCode);
    }
}
