import CPP.Absyn.Arg;
import CPP.Absyn.ListArg;
import CPP.Absyn.Type;

import java.util.LinkedList;

public class FunType{
    public LinkedList<Arg> args;
    public Type val;
    public Type returnType;

    public FunType(Type type, ListArg listArg){
        this.returnType = type;
        this.args = listArg;
    }
}
