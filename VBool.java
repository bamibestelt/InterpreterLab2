/**
 * Created by bamibestelt on 2016-11-22.
 */
public class VBool extends Value {

    public final Boolean bool_;
    public VBool(boolean p1) { bool_ = p1; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof VBool) {
            VBool x = (VBool)o;
            return this.bool_.equals(x.bool_);
        }
        return false;
    }

}