What the drone should do:
    Periodically send "gps:x:y:z," where x is the latitude, y (TO BE IMPLEMENTED, you can just send 0) altitude, z longitude (all string doubles)
    Periodically send "rot:r," where r is the rotation angle relative to the +x (positive latitude, counter-clockwise, DEGREES ONLY). (string double)

    Change left motor speed to s when "left:s," is sent where s had a range of [0,100] (string integer)
    See above but right motor, "right:s,"
    Drop cargo when "d," is received

    The drone is expected to handle if the connection is lost.
I can't guarantee that this program won't cause the drone to run away in a random direction.
