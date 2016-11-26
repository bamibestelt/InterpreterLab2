/**
 * Created by bamibestelt on 2016-11-24.
 */
public class Variable {

    private String variableId;
    private String variableType;
    private Object variableValue;
    private int variableFlag;

    public static final int VAR_LOCAL = 0;
    public static final int VAR_IMPORT = 1;

    public Variable() {}

    public Variable(String id, String type, Object value) {
        this.variableId = id;
        this.variableType = type;
        this.variableValue = value;
        this.variableFlag = VAR_LOCAL;
    }

    public void setVarFlag(int flag) {
        this.variableFlag = flag;
    }

    public int getVarFlag() {
        return this.variableFlag;
    }

    public String getVariableId() {
        return variableId;
    }

    public void setVariableId(String variableId) {
        this.variableId = variableId;
    }

    public String getVariableType() {
        return variableType;
    }

    public void setVariableType(String variableType) {
        this.variableType = variableType;
    }

    public Object getVariableValue() {
        return variableValue;
    }

    public void setVariableValue(Object variableValue) {
        this.variableValue = variableValue;
    }
}