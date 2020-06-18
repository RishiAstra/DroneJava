import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import jdk.jshell.spi.ExecutionControl;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * TODO: sending "hi hi" and making the arduino return it results in "hihi", all spaces lost
 * NOTE: z is the latitude, x is the longitude
 * NOTE: to turn, the motor speeds and rudder are adjusted
 */
public class Main {

    //global speed multipliers
    public static final double leftSpeedMult = 1.00d;
    public static final double rightSpeedMult = 1.00d;
    //if angle off (direction facing vs direction of target) is larger than this, one motor will be at minSpeed
    //because the rudder is used to turn, this might not be necessary
    public static final double maxAngleOff = 45d;
    public static final double minSpeed = 0.5d;
    //if the plane turns in the opposite direction, switch this
    public static final boolean swapMotors = false;
    //for landing
    public static final double landSpeedReduction = 0.1d;//amount to preiodically reduce speed by when landing
    public static final long landSpeedReductionWait = 50;//wait 50ms before reducing speed again
    //rudder constants
    public static final double rudderMiddleAngle = 90;//what angle is the rudder straight
    public static final double rudderMaxAngle = 60;//how far to one side of the middle angle can it turn

    public static boolean enabled = true;//if false, stop pathfinding etc. Used for landing or killing.

    public static final double distForTargetHit = 1 * 0.00000898331;//deg to meter constant is included

    public static Coord p;//current position
    public static double currentAngle;

    //TODO: WARNING:t.y is not used at the moment
    public static Coord t;//target position
    public static CopyOnWriteArrayList<Coord> targetPoints;//the target points to reach
    public static int targetPointIndex = 0;


    public static CopyOnWriteArrayList<String> commandsToSend;//the commands/data to be sent out. Each ends in ",". The command at index 0 is repeatedly sent and removes till the size() is 0
    public static CopyOnWriteArrayList<String> commandsReceived;//the commands/data received. Each ends in ",".
    static SerialPort comPort;
    static String stringBuffer;

    static boolean newData;
    public static CopyOnWriteArrayList<String> consoleInputUnprocessed;//the stuff typed in console
    public static CopyOnWriteArrayList<String> serialInputUnprocessed;//the stuff over serial received that hasn't been processed
    static String consoleCommandInProgress = "";//if something is typed into the console
    static String serialCommandInProgress = "";//if something is being received over serial
    static boolean firstGPSreceived = false;

    //initialization stuff
    static public void main(String[] args)
    {
        commandsToSend = new CopyOnWriteArrayList<String>();
        commandsReceived = new CopyOnWriteArrayList<String>();
        consoleInputUnprocessed = new CopyOnWriteArrayList<String>();
        serialInputUnprocessed = new CopyOnWriteArrayList<String>();

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
        (new Thread(new PathFindingThread())).start();
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
                        String commandString = s;
                        if(commandString.equals("kill")){
                            Kill();
                        }
                        else if(commandString.equals("land")){
                            Land();
                        }
                        else if(i!=-1){
                            commandString = s.substring(0, i);//the type of command
                            String arguments = s.substring(i + 1);//the data of the command
                            /////////////////GPS update command received//////
                            if(commandString.equals("gps")){
                                boolean succeeded = true;
                                double tempx = 0;
                                double tempy = 0;
                                double tempz = 0;
                                try{
                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        tempx = Double.parseDouble(arguments.substring(0, i));
                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        tempy = Double.parseDouble(arguments.substring(0, i));
                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                    tempz = Double.parseDouble(arguments);
                                }catch (Exception e){
                                    e.printStackTrace();
                                    succeeded = false;
                                }
                                if(succeeded){
                                    p.x = tempx;
                                    p.y = tempy;
                                    p.z = tempz;
                                    System.out.println("UPDATED GPS: " + tempx + ", " + tempy + ", " + tempz);
                                    if(!firstGPSreceived){
                                        t = new Coord(p);
                                        firstGPSreceived = true;
                                        System.out.println("First time receiving GPS, so a target was added: " + tempx + ", " + tempy + ", " + tempz);
                                    }
                                }
                            }
                            else if(commandString.equals("rot")){
                                boolean succeeded = true;
                                double temprot = 0;
                                try{
                                    temprot = Double.parseDouble(arguments);
                                }catch (Exception e){
                                    e.printStackTrace();
                                    succeeded = false;
                                }
                                if(succeeded){
                                    currentAngle = temprot;
                                    System.out.println("UPDATED ROTATION: " + temprot);
                                }
                            }
                            else if(commandString.equals("addt")){
                                boolean succeeded = true;
                                double tempx = 0;
                                double tempy = 0;
                                double tempz = 0;
                                try{
                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        tempx = Double.parseDouble(arguments.substring(0, i));
                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                    i = arguments.indexOf(":");
                                    if(i!=-1){
                                        tempy = Double.parseDouble(arguments.substring(0, i));
                                        arguments = arguments.substring(i + 1);
                                    }else{
                                        succeeded = false;
                                    }
                                    tempz = Double.parseDouble(arguments);
                                }catch (Exception e){
                                    e.printStackTrace();
                                    succeeded = false;
                                }
                                if(succeeded){
                                    targetPoints.add(new Coord(tempx, tempy, tempz));
                                    System.out.println("ADDED TARGET: " + tempx + ", " + tempy + ", " + tempz);
                                }
                            }
                            else{
                                System.out.println("The received command (with :) did nothing: " + commandString);
                            }
                            ////////////////////////////////////////
                        }
                        else{
                            System.out.println("The received command did nothing: " + commandString);
                        }

                    }
                    Thread.sleep(1);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //this writes out the commandsToSend to the arduino and says "SENDING COMMAND: "
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

    /////////////////pathfinding
    public static class PathFindingThread implements Runnable
    {
        public void run ()
        {
            try{
                while(true){
                    if(t!=null && enabled){
                        CheckTarget();
                        AttackTarget();
                    }

                    Thread.sleep(1);//don't hog CPU
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    //calculated distance, ignoring y
    public static double DistSq(Coord a, Coord b){
        return (a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z);
    }

    //checks if target is hit
    public static void CheckTarget(){
        double distSq = DistSq(t, p);
        //if close enough to target
        if(distSq < distForTargetHit * distForTargetHit){
            NextTarget();
        }
    }

    //selects the next target
    public static void NextTarget() {
        //if there is no next target, keep circling around this target
        if(targetPoints.size() <= 1) return;

        targetPoints.remove(targetPointIndex);
        
        //find index of closest point
        int index = -1;
        double d = 99999999999d;
        for(int i = 0;i < targetPoints.size();i++){
            double tempDist = DistSq(targetPoints.get(i), p);
            if(tempDist < d){
                d = tempDist;
                index = i;
            }
        }

        //update the target
        targetPointIndex = index;
        p = targetPoints.get(targetPointIndex);
    }

    public static void AttackTarget() {
        //TODO: for now, only the x and z are considered, the y is ignored
        double dx = t.x-p.x;
        double dz = t.z-p.z;
        double angle = Math.atan2(p.z, p.x) * 57.295779513d;//rad to deg, perhaps it should be (x, z)
        double diff = angle - currentAngle;
        //get between to -180, 180
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;

        double rs = 1;
        double ls = 1;
        double rudderMult = 0;//a value from [-1, 1] of the rudder position desired
        if(diff < 0){
            //diff is negative, so the sign is flipped
            //this reduces the left speed
            ls *= (1d + diff / maxAngleOff);
        }else{
            rs *= (1d - diff / maxAngleOff);
        }
        rs = rs * (1-minSpeed) + minSpeed;
        ls = ls * (1-minSpeed) + minSpeed;
        rudderMult = diff/maxAngleOff;

        SetLeft(rs);
        SetRight(ls);
        SetRudder(rudderMult);
    }

    public static void GetTargetPosition(){
        //set t here
        System.out.println("Tried to get target position but this code is not written yet");
    }
    public static String DTS(Double d){//Double to String
        return String.format ("%.9f", d);//limit to 9 decimal places and force non-scientific notation
    }

    //////////commands to send


    //won't be used most likely
//    public static void SetTargetPosition(Double xpos, Double ypos, Double zpos){
//        commandsToSend.add("t:" + DTS(xpos) + ":"+ DTS(ypos) + ":"+ DTS(zpos) + ",");
//    }

    //drop the cargo
    public static void DropCargo(){
        commandsToSend.add("d,");
    }
    public static long DoubleToMotorSpeed(Double d){
        return Math.round(d * 100d);
    }
    //set motor speeds. Takes in a double from 0 - 1
    public static void SetLeft(Double s){
        if(s > 1) s = 1d;
        if(s < -1) s = -1d;
        String temp = swapMotors ? "right:" : "left:";
        commandsToSend.add(temp + DoubleToMotorSpeed(s * leftSpeedMult) + ",");
    }
    public static void SetRight(Double s){
        if(s > 1) s = 1d;
        if(s < -1) s = -1d;
        String temp = swapMotors ? "left:" : "right:";
        commandsToSend.add(temp + DoubleToMotorSpeed(s * rightSpeedMult) + ",");
    }
    //s should be between [-1, 1]
    public static void SetRudder(Double s){
        if(s > 1) s = 1d;
        if(s < -1) s = -1d;
        String temp = "rudder:";
        if(swapMotors) s = -s;
        s *= rudderMaxAngle;//multiply
        s += rudderMiddleAngle;//relative to middle angle
        commandsToSend.add(temp + DoubleToMotorSpeed(s * rightSpeedMult) + ",");
    }
    public static void SetBoth(Double s){
        SetLeft(s);
        SetRight(s);
    }

    //slowly reduces speed
    public static void Land(){
        (new Thread(new LandThread())).start();
        enabled = false;
        System.out.println("Started Landing");
    }

    public static void Kill(){
        SetLeft(0d);
        SetRight(0d);
        enabled = false;
        System.out.println("Killed");
    }

    /////land
    public static class LandThread implements Runnable
    {
        double speed;
        public LandThread(){
            speed = 1.00;
        }
        public void run ()
        {
            try{
                while(speed > 0.1d){
                    SetBoth(speed);
                    speed -= landSpeedReduction;
                    Thread.sleep(landSpeedReductionWait);//don't hog CPU
                }
                Kill();
                System.out.println("Finished Landing");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}