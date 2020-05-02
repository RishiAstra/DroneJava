import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Scanner;

//TODO: sending "hi hi" and making the arduino return it results in "hihi", all spaces lost
public class Main {

    public static ArrayList<String> commandsToSend;//the commands/data to be sent out. Each ends in ",". The command at index 0 is repeatedly sent and removes till the size() is 0
    public static ArrayList<String> commandsReceived;//the commands/data creceived. Each ends in ",". Nothing is done with these at present
    static SerialPort comPort;
    static String stringBuffer;

    static boolean newData;
    public static ArrayList<String> consoleInputUnprocessed;
    public static ArrayList<String> serialInputUnprocessed;
    static String consoleCommandInProgress = "";
    static String serialCommandInProgress = "";


    //this reads data
    private static final class DataListener implements SerialPortDataListener
    {
        @Override
        public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

        @Override
        public void serialEvent(SerialPortEvent event)
        {
            try {
                //System.out.println("In event listener.");
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    return;
                //System.out.println("Past event type check.");
                byte[] newData = new byte[comPort.bytesAvailable()];
                int numRead = comPort.readBytes(newData, newData.length);
                stringBuffer = new String(newData, "ASCII");//new String(newData,0,numRead);
                //System.out.println("Read " + numRead + " bytes.");
                System.out.println("RECIEVED: " + stringBuffer);
                serialInputUnprocessed.add(stringBuffer);

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    //initialization stuff
    static public void main(String[] args)
    {
        commandsToSend = new ArrayList<String>();
        commandsReceived = new ArrayList<String>();
        consoleInputUnprocessed = new ArrayList<String>();
        serialInputUnprocessed = new ArrayList<String>();

        comPort = SerialPort.getCommPort("COM3");//.getCommPorts()[0];
        comPort.openPort();
        comPort.setBaudRate(115200);
        comPort.setComPortParameters(115200, 8,1, SerialPort.NO_PARITY);
        System.out.println("COM port open: " + comPort.getDescriptivePortName());
        DataListener listener = new DataListener();
        comPort.addDataListener(listener);
        System.out.println("Event Listener open.");
        (new Thread(new SerialWriter(comPort.getOutputStream()))).start();
        (new Thread(new ConsoleCommands())).start();
        (new Thread(new InputsToCommands())).start();
    }

    //this writes out the commandsToSend to the arduino and says "SENDING: "
    public static class SerialWriter implements Runnable
    {
        OutputStream out;

        public SerialWriter ( OutputStream out )
        {
            this.out = out;

        }


        public void run ()
        {
            try{

                while(true){
                    /////sends commandsToSend
                    while(commandsToSend.size() > 0){
                        String s = commandsToSend.remove(0);
                        System.out.println("SENDING: " + s);
                        this.out.write(s.getBytes("ASCII"));
                    }
                    Thread.sleep(1);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //process commands receives from the arduino
    public static class ProcessCommands implements Runnable
    {
        public void run ()
        {
            try{
                while(true){
                    /////sends commandsToSend
                    while(commandsReceived.size() > 0){
                        String s = commandsReceived.remove(0);
                        System.out.println("RECEIVED A COMMAND: " + s);
                    }
                    Thread.sleep(1);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //this processes data so it can be used
    public static class InputsToCommands implements Runnable
    {
        public void run ()
        {
            Scanner scanner = new Scanner(System.in);
            try{

                while(true){
                    //turn input into commandsToSend
                    while(serialInputUnprocessed.size() > 0){
                        serialCommandInProgress = serialCommandInProgress + serialInputUnprocessed.remove(0);
                    }
                    int i = serialCommandInProgress.indexOf(',');
                    while(i != -1){
                        commandsReceived.add(serialCommandInProgress.substring(0, i + 1));
                        serialCommandInProgress = serialCommandInProgress.substring(i + 1);
                        i = serialCommandInProgress.indexOf(',');
                    }

                    //turn console input into commandsToSend. This is important because the console input could be
                    //hi
                    //test,
                    //but the command would be "hitest,", not two commandsToSend "hi" and "test,"
                    while(consoleInputUnprocessed.size() > 0){
                        consoleCommandInProgress = consoleCommandInProgress + consoleInputUnprocessed.remove(0);
                    }
                    i = consoleCommandInProgress.indexOf(',');
                    while(i != -1){
                        commandsToSend.add(consoleCommandInProgress.substring(0, i + 1));
                        consoleCommandInProgress = consoleCommandInProgress.substring(i + 1);
                        i = consoleCommandInProgress.indexOf(',');
                    }
                    Thread.sleep(1);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //this reads console input
    public static class ConsoleCommands implements Runnable
    {
        public void run ()
        {
            Scanner scanner = new Scanner(System.in);
            try{

                while(true){
                    consoleInputUnprocessed.add(scanner.next());
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}