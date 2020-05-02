import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import jdk.jshell.spi.ExecutionControl;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Scanner;

//TODO: sending "hi hi" and making the arduino return it results in "hihi", all spaces lost
public class Main {
    public static double x;
    public static double y;
    public static double z;

    public static ArrayList<String> commandsToSend;//the commands/data to be sent out. Each ends in ",". The command at index 0 is repeatedly sent and removes till the size() is 0
    public static ArrayList<String> commandsReceived;//the commands/data creceived. Each ends in ",". Nothing is done with these at present
    static SerialPort comPort;
    static String stringBuffer;

    static boolean newData;
    public static ArrayList<String> consoleInputUnprocessed;
    public static ArrayList<String> serialInputUnprocessed;
    static String consoleCommandInProgress = "";
    static String serialCommandInProgress = "";


    public static void SetTargetPosition(Double xpos, Double ypos, Double zpos){
        commandsToSend.add("t:" + xpos.toString() + ":"+ ypos.toString() + ":"+ zpos.toString() + ",");
    }
    public static void DropCargo(){
        commandsToSend.add("d,");
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
        (new Thread(new ProcessCommands())).start();

    }




    //this turns strings into commands, e.g. "hi,hihi,fadsl;fa" becomes {"hi," "hihi,"}
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

    ////////////////executing commands

    //executes commands received from the arduino
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
                        int i = s.indexOf(":");
                        if(i!=-1){
                            String commandString = s.substring(0, i);//the type of command
                            String arguments = s.substring(i + 1);//the data of the command
                            /////////////////GPS update command received//////
                            if(commandString.equals("gps")){
                                boolean succeeded = true;
                                double tempx, tempy, tempz;
                                try{

                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        x = Double.parseDouble(arguments.substring(0, i));
                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        y = Double.parseDouble(arguments.substring(0, i));
                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        z = Double.parseDouble(arguments.substring(0, i));
//                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                    succeeded = false;
                                }
                                if(succeeded){
                                    x = tempx;
                                    y = tempy;
                                    z = tempz;
                                    System.out.println("UPDATED GPS: " + tempx + ", " + tempy + ", " + tempz);
                                }
                            }else{
                                System.out.println("The received command did nothing: " + commandString);
                            }
                            ////////////////////////////////////////
                        }
                    }
                    Thread.sleep(1);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
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
                        System.out.println("SENDING COMMAND: " + s);
                        this.out.write(s.getBytes("ASCII"));
                    }
                    Thread.sleep(1);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }


    ///////////////////reading data

    //this reads console input
    public static class ConsoleCommands implements Runnable
    {
        public void run ()
        {
            Scanner scanner = new Scanner(System.in);
            try{

                while(true){
                    String s = scanner.next();
                    System.out.println("INPUTTED: " + s);
                    consoleInputUnprocessed.add(s);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

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

}