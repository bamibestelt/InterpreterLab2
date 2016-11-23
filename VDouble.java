/**
 * Created by bamibestelt on 2016-11-22.
 */
public class VDouble extends Value {

    public final Double double_;
    public VDouble(Double p1) { double_ = p1; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof VDouble) {
            VDouble x = (VDouble)o;
            return this.double_.equals(x.double_);
        }
        return false;
    }

}
