/**
 * Created by bamibestelt on 2016-11-22.
 */
public class VInt extends Value {

    public final Integer integer_;
    public VInt(Integer p1) { integer_ = p1; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof VInt) {
            VInt x = (VInt)o;
            return this.integer_.equals(x.integer_);
        }
        return false;
    }

}
