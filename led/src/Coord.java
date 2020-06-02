public class Coord {
    public double x;
    public double y;
    public double z;

    public Coord(double x, double y, double z){
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public Coord(Coord o){
        this.x = o.x;
        this.y = o.y;
        this.z = o.z;
    }
}
