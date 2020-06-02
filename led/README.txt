What the drone should do:
    Periodically send "gps:x:y:z," where x is the latitude, y (TO BE IMPLEMENTED, you can just send 0) altitude, z longitude (all string doubles)
    Periodically send "rot:r," where r is the rotation angle relative to the +x (positive latitude, counter-clockwise angle, DEGREES ONLY). (string double)

    Change left motor speed to s when "left:s," is sent where s had a range of [0,100] (string integer)
    See above but right motor, "right:s,"
    Change rudder angle when "rudder:a" is received where a is the angle in degrees. The range is rudderMiddleAngle +- rudderMaxAngle
    Drop cargo when "d," is received

    The drone is expected to handle if the connection is lost.
I can't guarantee that this program won't cause the drone to run away in a random direction. It shouldn't.
In the console you can type "kill," to turn off both motors or "land," to gradually turn off the motors.

There are many constants. These might need to be changed.
    //global speed multipliers
    public static final double leftSpeedMult = 1.00d;
    public static final double rightSpeedMult = 1.00d;
    //if angle off (direction facing vs direction of target) is larger than this, one motor will be at minSpeed.
    public static final double maxAngleOff = 45d;
    public static final double minSpeed = 0.5d;
    etc, see Main.java to change these
