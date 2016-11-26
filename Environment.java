import CPP.Absyn.DFun;

import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Created by bamibestelt on 2016-11-24.
 */
public class Environment {

    public Environment() {}

    // map of function objects
    private TreeMap<String, DFun> functions = new TreeMap<>();

    // table of variables <var name, variable data>
    private LinkedHashMap<String, Variable> variables = new LinkedHashMap<>();

    public void setFunctions(TreeMap<String, DFun> inits) {
        this.functions = new TreeMap<>(inits);
    }

    public void addFunction(DFun dFun) {
        functions.put(dFun.id_, dFun);
    }

    public DFun getFunction(String id) {
        return functions.get(id);
    }

    public TreeMap<String, DFun> getFunctions() {
        return functions;
    }

    public void addVariable(String context, Variable var) {
        variables.put(context, var);
    }

    public Variable getVariable(String context) {
        return variables.get(context);
    }

    public LinkedHashMap<String, Variable> getVariables() {
        return variables;
    }

    // value can be many types int, double, boolean
    public void updateVariable(String id, Object value) {
        Variable var = getVariable(id);
        var.setVariableValue(value);
        addVariable(id, var);
    }
}